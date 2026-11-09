package dev.graphassembly.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.graphassembly.integration.*;
import dev.graphassembly.integration.vanilla.furnace.*;
import dev.graphassembly.recipe.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

/** Preserve the native burn loop; only graph query, capacity, commit and accounting are substituted. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceGraphMixin implements CookingStateAccess {
    @Shadow @Final private RecipeType<? extends AbstractCookingRecipe> recipeType;
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;
    @Shadow int litTime;
    @Shadow int litDuration;
    @Unique private CookingState graphAssembly$state;
    public CookingState graphAssembly$cookingState(){
        if(graphAssembly$state==null)graphAssembly$state=new CookingState((AbstractFurnaceBlockEntity)(Object)this,recipeType);
        return graphAssembly$state;
    }
    public void graphAssembly$cookProgress(int value){cookingProgress=value;}
    public void graphAssembly$cookTime(int value){cookingTotalTime=value;}
    public void graphAssembly$abortUnpaidIgnition() {
        litTime=0;
        litDuration=0;
    }
    @Unique private static CookingState state(AbstractFurnaceBlockEntity f){return ((CookingStateAccess)f).graphAssembly$cookingState();}
    @WrapMethod(method="serverTick")
    private static void graphAssembly$tick(Level level,BlockPos pos,BlockState block,AbstractFurnaceBlockEntity furnace,Operation<Void> original) {
        if(!CookingLookup.machine(furnace)) {
            original.call(level,pos,block,furnace);
            return;
        }
        var s=state(furnace);
        if(s.busy) {
            s.invalid=true;
            return;
        }
        try {
            s.begin(level);
            // A callback during lookup/identity may even remove graph admission. Never let
            // an invalid original transaction become an ordinary fallback operation.
            if(s.invalid)return;
            original.call(level,pos,block,furnace);
        } finally {
            s.end();
        }
    }
    @Redirect(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static Optional<?> graphAssembly$query(RecipeManager.CachedCheck check,RecipeInput input,Level level,
        Level tickLevel,BlockPos pos,BlockState block,AbstractFurnaceBlockEntity furnace) {
        var selection=state(furnace).selection;
        if(state(furnace).invalid)return Optional.empty();
        return selection!=null&&selection.handled()?Optional.ofNullable(selection.recipe()):check.getRecipeFor(input,level);
    }
    @Inject(method="getTotalCookTime",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$time(Level level,AbstractFurnaceBlockEntity furnace,CallbackInfoReturnable<Integer> cir) {
        if(!CookingLookup.machine(furnace))return;
        var selected=CookingLookup.select(level,furnace,state(furnace).type);
        if(selected.handled())cir.setReturnValue(selected.recipe()==null?200:selected.recipe().value().getCookingTime());
    }
    @Inject(method="canBurn",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$capacity(RegistryAccess registries,RecipeHolder<?> recipe,NonNullList<ItemStack> items,int limit,
        AbstractFurnaceBlockEntity furnace,CallbackInfoReturnable<Boolean> cir) {
        var s=state(furnace);var selected=s.selection;
        if(s.busy&&s.invalid){cir.setReturnValue(false);return;}
        if(CookingLookup.machine(furnace)&&dev.graphassembly.integration.sequence.NativeSequences.tracked(furnace.getItem(1))) {cir.setReturnValue(false);return;}
        if(selected!=null&&selected.handled())cir.setReturnValue(s.fits());
    }
    @Inject(method="burn",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$commit(RegistryAccess registries,RecipeHolder<?> recipe,NonNullList<ItemStack> items,int limit,
        AbstractFurnaceBlockEntity furnace,CallbackInfoReturnable<Boolean> cir) {
        var s=state(furnace);var selected=s.selection;
        if(selected==null||!selected.handled())return;
        cir.setReturnValue(false);
        var prepared=s.prepare();
        if(prepared==null||!s.valid())return;
        var result=prepared.roll(furnace.getLevel().random);
        var merged=CookingLookup.merge(items.get(2),result,limit);
        if(merged==null)throw new IllegalStateException("Validated cooking outcome no longer fits");
        items.set(2,merged);
        if(items.get(0).is(Blocks.WET_SPONGE.asItem())&&items.get(1).is(Items.BUCKET))items.set(1,new ItemStack(Items.WATER_BUCKET));
        items.get(0).shrink(1);
        s.experience+=selected.recipe().value().getExperience();

        furnace.setChanged();
        cir.setReturnValue(true);
    }
    @Inject(method="setRecipeUsed",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$account(RecipeHolder<?> recipe,CallbackInfo ci) {
        var s=graphAssembly$cookingState();if(s.busy&&s.selection!=null&&s.selection.handled())ci.cancel();
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;getBurnDuration(Lnet/minecraft/world/item/ItemStack;)I"))
    private static int graphAssembly$fuelDuration(AbstractFurnaceBlockEntity furnace,ItemStack fuel,Operation<Integer> original) {
        var s=state(furnace);
        if(!s.graph())return original.call(furnace,fuel);
        var before=s.snapshot();
        int value=original.call(furnace,fuel);
        s.checkCallback(before);
        return s.invalid?0:value;
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;hasCraftingRemainingItem()Z"))
    private static boolean graphAssembly$hasRemainder(ItemStack fuel,Operation<Boolean> original,Level l,BlockPos p,BlockState b,AbstractFurnaceBlockEntity furnace) {
        var s=state(furnace);
        if(!s.graph())return original.call(fuel);
        var before=s.snapshot();
        boolean value=original.call(fuel);
        s.checkCallback(before);
        if(s.invalid)((CookingStateAccess)furnace).graphAssembly$abortUnpaidIgnition();
        return s.invalid||value;
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;getCraftingRemainingItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack graphAssembly$remainder(ItemStack fuel,Operation<ItemStack> original,Level l,BlockPos p,BlockState b,AbstractFurnaceBlockEntity furnace) {
        var s=state(furnace);
        if(!s.graph())return original.call(fuel);
        if(s.invalid)return furnace.getItem(1).copy();
        var before=s.snapshot();
        var value=original.call(fuel);
        s.checkCallback(before);
        // The has-remainder branch runs before fuel consumption. The empty-stack fallback
        // runs after the native shrink and has already paid for its burn time.
        if(s.invalid&&!before.get(1).isEmpty())((CookingStateAccess)furnace).graphAssembly$abortUnpaidIgnition();
        return s.invalid?furnace.getItem(1).copy():value;
    }
    @Inject(method="canPlaceItem",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$protectFuel(int slot,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {if(CookingLookup.machine((AbstractFurnaceBlockEntity)(Object)this)&&slot==1&&dev.graphassembly.integration.sequence.NativeSequences.tracked(stack))cir.setReturnValue(false);}
    @Inject(method="saveAdditional",at=@At("TAIL"))
    private void graphAssembly$save(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci) {
        var s=graphAssembly$cookingState();tag.putString("GraphAssemblyCookingJob",s.job);tag.putDouble("GraphAssemblyCookingExperience",s.experience);
    }
    @Inject(method="loadAdditional",at=@At("TAIL"))
    private void graphAssembly$load(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci) {
        var s=graphAssembly$cookingState();s.job=tag.getString("GraphAssemblyCookingJob");s.experience=tag.getDouble("GraphAssemblyCookingExperience");s.index=null;
    }
    @Inject(method="getRecipesToAwardAndPopExperience",at=@At("TAIL"))
    private void graphAssembly$experience(ServerLevel level,Vec3 pos,CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        var s=graphAssembly$cookingState();double experience=s.experience;s.experience=0;s.furnace.setChanged();
        int count=(int)Math.floor(experience);if(Math.random()<experience-count)count++;
        if(count>0)ExperienceOrb.award(level,pos,count);
    }
}
