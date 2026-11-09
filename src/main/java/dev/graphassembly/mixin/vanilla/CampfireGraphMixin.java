package dev.graphassembly.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.graphassembly.integration.vanilla.campfire.CampfireCooking;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

/** Keep ordinary tick/cooldown, insertion consumption, rendering, and break handling native. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireGraphMixin {
    @Shadow @Final private int[] cookingProgress;
    @Shadow @Final private int[] cookingTime;
    @Unique private final CampfireCooking.State graphAssembly$state=new CampfireCooking.State();
    @Unique private CampfireCooking.Scope graphAssembly$scope;
    @Inject(method="getCookableRecipe",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$admission(ItemStack held,CallbackInfoReturnable<Optional<RecipeHolder<CampfireCookingRecipe>>> cir) {
        var result=CampfireCooking.cookable((CampfireBlockEntity)(Object)this,held);
        if(result!=null)cir.setReturnValue(result);
    }
    @WrapMethod(method="cookTick")
    private static void graphAssembly$tick(Level level,BlockPos pos,BlockState block,CampfireBlockEntity fire,Operation<Void> original) {
        if(level.isClientSide||!block.getValue(CampfireBlock.LIT))return;
        var self=(CampfireGraphMixin)(Object)fire;
        try(var scope=CampfireCooking.enter(level,pos)) {
            if(scope==null)return;
            self.graphAssembly$scope=scope;
            try{original.call(level,pos,block,fire);}finally{self.graphAssembly$scope=null;}
        }
    }
    @WrapOperation(method="cookTick",at=@At(value="INVOKE",target="Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"))
    private static Object graphAssembly$slot(NonNullList<ItemStack> items,int slot,Operation<Object> original,
        Level level,BlockPos pos,BlockState block,CampfireBlockEntity fire) {
        var self=(CampfireGraphMixin)(Object)fire;
        if(CampfireCooking.tickSlot(fire,self.graphAssembly$state,slot,self.cookingProgress,self.cookingTime,self.graphAssembly$scope))return ItemStack.EMPTY;
        return original.call(items,slot);
    }
    @Inject(method="saveAdditional",at=@At("TAIL"))
    private void graphAssembly$save(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci) {
        var jobs=new CompoundTag();for(int i=0;i<4;i++)jobs.putString(Integer.toString(i),graphAssembly$state.jobs[i]);
        tag.put("GraphAssemblyCampfireJobs",jobs);
    }
    @Inject(method="loadAdditional",at=@At("TAIL"))
    private void graphAssembly$load(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci) {
        var jobs=tag.getCompound("GraphAssemblyCampfireJobs");for(int i=0;i<4;i++)graphAssembly$state.jobs[i]=jobs.getString(Integer.toString(i));
        graphAssembly$state.loaded();
    }
}
