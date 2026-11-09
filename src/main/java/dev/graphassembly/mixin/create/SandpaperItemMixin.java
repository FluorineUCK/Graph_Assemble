package dev.graphassembly.mixin.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import dev.graphassembly.integration.SandpaperOperations;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SandPaperItem.class)
public abstract class SandpaperItemMixin {
    @Inject(method="finishUsingItem",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$refundBlockedPolishing(ItemStack paper,Level level,LivingEntity entity,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        if(!(entity instanceof Player player))return;
        var reservation=paper.get(AllDataComponents.SAND_PAPER_POLISHING);
        if(reservation==null)return;
        var found=SandpaperOperations.lookup(level,reservation.item());
        if(!found.handled()||!found.recipes().isEmpty())return;
        // Returning the input from applyPolish is too late: the host still pays its
        // crafting remainder and tool damage. Abort that transaction before either.
        if(!level.isClientSide) {
            paper.remove(AllDataComponents.SAND_PAPER_POLISHING);
            player.getInventory().placeItemBackInInventory(reservation.item().copy());
        }
        cir.setReturnValue(paper);
    }
}
