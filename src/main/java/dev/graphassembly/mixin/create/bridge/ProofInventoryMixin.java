package dev.graphassembly.mixin.create.bridge;
import dev.graphassembly.integration.bridge.InventoryProof;
import com.simibubi.create.foundation.item.SmartInventory;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.function.BiPredicate;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;

@Mixin(value=SmartInventory.class,remap=false)
public abstract class ProofInventoryMixin {
    @Unique private final Map<Integer,InventoryProof.Origin> graphAssembly$origins=new HashMap<>();
    @Unique private WeakReference<SyncedBlockEntity> graphAssembly$machine=new WeakReference<>(null);
    @Inject(method="<init>(ILcom/simibubi/create/foundation/blockEntity/SyncedBlockEntity;IZ)V",at=@At("RETURN"))
    private void graphAssembly$owner(int size,SyncedBlockEntity owner,int stackSize,boolean stackNonStackables,CallbackInfo ci){graphAssembly$machine=new WeakReference<>(owner);}
    @Inject(method="<init>(ILcom/simibubi/create/foundation/blockEntity/SyncedBlockEntity;IZLjava/util/function/BiPredicate;)V",at=@At("RETURN"))
    private void graphAssembly$filteredOwner(int size,SyncedBlockEntity owner,int stackSize,boolean stackNonStackables,BiPredicate<Integer,ItemStack> filter,CallbackInfo ci){graphAssembly$machine=new WeakReference<>(owner);}
    @Inject(method="getStackInSlot",at=@At("RETURN")) private void graphAssembly$read(int slot,CallbackInfoReturnable<ItemStack> cir){InventoryProof.read((IItemHandler)this,graphAssembly$machine.get(),graphAssembly$origins,slot,cir.getReturnValue());}
    @Inject(method="extractItem",at=@At("HEAD")) private void graphAssembly$beforeExtract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> cir){((IItemHandler)this).getStackInSlot(slot);}
    @Inject(method="extractItem",at=@At("RETURN")) private void graphAssembly$afterExtract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> cir){if(!simulate&&!cir.getReturnValue().isEmpty())graphAssembly$origins.remove(slot);}
    @Inject(method="setStackInSlot",at=@At("HEAD")) private void graphAssembly$replace(int slot,ItemStack stack,CallbackInfo ci){graphAssembly$origins.remove(slot);}
    @Inject(method="insertItem",at=@At("RETURN")) private void graphAssembly$insert(int slot,ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> cir){if(!simulate&&cir.getReturnValue().getCount()!=stack.getCount())graphAssembly$origins.remove(slot);}
}
