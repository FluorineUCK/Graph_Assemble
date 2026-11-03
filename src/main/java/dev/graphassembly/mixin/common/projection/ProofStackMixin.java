package dev.graphassembly.mixin.common.projection;
import dev.graphassembly.integration.projection.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ItemStack.class)
public abstract class ProofStackMixin implements InventoryProof.StackMeta {
    @Unique private InventoryProof.Origin graphAssembly$origin;
    @Unique private ResultReceipt graphAssembly$receipt;
    public InventoryProof.Origin graphAssembly$origin(){return graphAssembly$origin;}
    public void graphAssembly$origin(InventoryProof.Origin o){graphAssembly$origin=o;}
    public ResultReceipt graphAssembly$receipt(){return graphAssembly$receipt;}
    public void graphAssembly$receipt(ResultReceipt r){graphAssembly$receipt=r;}
    @Inject(method="copy",at=@At("RETURN")) private void graphAssembly$copy(CallbackInfoReturnable<ItemStack> cir){InventoryProof.copied((ItemStack)(Object)this,cir.getReturnValue());}
    @Inject(method="applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V",at=@At("RETURN")) private void graphAssembly$finish(CallbackInfo ci){if(graphAssembly$receipt!=null)graphAssembly$receipt.restore((ItemStack)(Object)this);}
    @Inject(method="isSameItemSameComponents",at=@At("HEAD"),cancellable=true) private static void graphAssembly$compare(ItemStack a,ItemStack b,CallbackInfoReturnable<Boolean> cir){if(InventoryProof.staleComparison(a,b))cir.setReturnValue(false);}
}
