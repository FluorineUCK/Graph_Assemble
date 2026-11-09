package dev.graphassembly.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.graphassembly.integration.vanilla.brewing.*;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin implements BrewingAccess {
    @Shadow private NonNullList<ItemStack> items;
    @Shadow int brewTime;
    @Shadow int fuel;
    @Unique private final BrewingTransaction.State graphAssembly$state=new BrewingTransaction.State();
    @Unique private BrewingTransaction.Scope graphAssembly$scope;
    public NonNullList<ItemStack> graphAssembly$items(){return items;}
    public int graphAssembly$time(){return brewTime;}
    public void graphAssembly$time(int value){brewTime=value;}
    public int graphAssembly$fuel(){return fuel;}
    public BrewingTransaction.State graphAssembly$brewingState(){return graphAssembly$state;}
    @WrapMethod(method="serverTick")
    private static void graphAssembly$tick(Level level,BlockPos pos,BlockState block,BrewingStandBlockEntity stand,Operation<Void> original){
        var self=(BrewingStandMixin)(Object)stand;
        try(var scope=BrewingTransaction.enter(level,pos,stand)){
            if(scope==null)return;self.graphAssembly$scope=scope;
            try{original.call(level,pos,block,stand);}finally{self.graphAssembly$scope=null;}
        }
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"))
    private static Object graphAssembly$fuelStack(NonNullList<ItemStack> items,int slot,Operation<Object> original){
        var result=original.call(items,slot);return slot==4&&dev.graphassembly.integration.sequence.NativeSequences.tracked((ItemStack)result)?ItemStack.EMPTY:result;
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean graphAssembly$appearance(Level level,BlockPos pos,BlockState updated,int flags,Operation<Boolean> original,
        Level tickLevel,BlockPos tickPos,BlockState block,BrewingStandBlockEntity stand){
        // A callback may replace/remove the host. Native bottle appearance must never resurrect it.
        if(stand.isRemoved()||level.getBlockEntity(pos)!=stand||!level.getBlockState(pos).is(block.getBlock()))return false;
        return original.call(level,pos,updated,flags);
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;isBrewable(Lnet/minecraft/world/item/alchemy/PotionBrewing;Lnet/minecraft/core/NonNullList;)Z"))
    private static boolean graphAssembly$canBrew(PotionBrewing brewing,NonNullList<ItemStack> items,Operation<Boolean> original,Level level,BlockPos pos,BlockState block,BrewingStandBlockEntity stand){
        var scope=((BrewingStandMixin)(Object)stand).graphAssembly$scope;
        var result=BrewingTransaction.brewable(scope);
        return result==null?BrewingTransaction.ordinaryBrewable(scope,copy->original.call(brewing,copy)):result;
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;doBrew(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/NonNullList;)V"))
    private static void graphAssembly$finish(Level level,BlockPos pos,NonNullList<ItemStack> items,Operation<Void> original,Level tickLevel,BlockPos tickPos,BlockState block,BrewingStandBlockEntity stand){
        var scope=((BrewingStandMixin)(Object)stand).graphAssembly$scope;
        if(scope!=null&&scope.lookup!=null&&scope.lookup.handled()){
            scope.authorized=true;BrewingTransaction.complete(scope);
        }else if(scope!=null&&!scope.invalid){
            // Ordinary completion keeps the original event path, with the scoped wrapper bypass below.
            scope.authorized=true;try{original.call(level,pos,items);}finally{scope.authorized=false;}
        }
    }
    @WrapMethod(method="doBrew")
    private static void graphAssembly$direct(Level level,BlockPos pos,NonNullList<ItemStack> items,Operation<Void> original){
        var stand=level.getBlockEntity(pos);
        if(stand instanceof BrewingStandBlockEntity){
            var scope=((BrewingStandMixin)(Object)stand).graphAssembly$scope;
            if(scope!=null&&scope.authorized){scope.authorized=false;BrewingTransaction.ordinaryComplete(scope,copy->original.call(level,pos,copy));return;}
        }
        BrewingTransaction.direct(level,pos,items,copy->original.call(level,pos,copy));
    }
    @WrapOperation(method="doBrew",at=@At(value="INVOKE",target="Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private static void graphAssembly$deferDrop(Level level,double x,double y,double z,ItemStack stack,Operation<Void> original){
        if(!BrewingTransaction.deferDrop(level,x,y,z,stack))original.call(level,x,y,z,stack);
    }
    @WrapOperation(method="doBrew",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
    private static void graphAssembly$deferEvent(Level level,int event,BlockPos pos,int data,Operation<Void> original){
        if(!BrewingTransaction.deferEvent(level,event,pos,data))original.call(level,event,pos,data);
    }
    @Inject(method="saveAdditional",at=@At("TAIL"))
    private void graphAssembly$save(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci){tag.putString("GraphAssemblyBrewingJob",graphAssembly$state.job);tag.putString("GraphAssemblyBrewingRuntime",graphAssembly$state.runtime);}
    @Inject(method="loadAdditional",at=@At("TAIL"))
    private void graphAssembly$load(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci){graphAssembly$state.job=tag.getString("GraphAssemblyBrewingJob");graphAssembly$state.runtime=tag.getString("GraphAssemblyBrewingRuntime");graphAssembly$state.loaded();}
}
