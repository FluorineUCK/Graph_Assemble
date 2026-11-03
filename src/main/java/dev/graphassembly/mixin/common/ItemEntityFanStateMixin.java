package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.FanGraphRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only our tagged completed jobs after native ItemStack decoding; ordinary items stay native. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityFanStateMixin {
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void graphAssembly$loadedFanIdentity(CompoundTag tag,CallbackInfo ci) {
        FanGraphRuntime.loadedEntity((ItemEntity)(Object)this,tag);
    }
}
