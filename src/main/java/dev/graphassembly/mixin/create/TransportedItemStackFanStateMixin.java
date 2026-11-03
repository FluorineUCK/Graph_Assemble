package dev.graphassembly.mixin.create;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import dev.graphassembly.integration.FanGraphRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe native serialization only for existing graph fan jobs; no ordinary stack state added. */
@Mixin(TransportedItemStack.class)
public abstract class TransportedItemStackFanStateMixin {
    @Inject(method="serializeNBT",at=@At("RETURN"))
    private void graphAssembly$saveFanIdentity(HolderLookup.Provider provider,CallbackInfoReturnable<CompoundTag> cir) {
        FanGraphRuntime.saveTransported((TransportedItemStack)(Object)this,cir.getReturnValue());
    }
    @Inject(method="read",at=@At("RETURN"))
    private static void graphAssembly$readFanIdentity(CompoundTag saved,HolderLookup.Provider provider,CallbackInfoReturnable<TransportedItemStack> cir) {
        FanGraphRuntime.loadedTransported(cir.getReturnValue(),saved);
    }
}
