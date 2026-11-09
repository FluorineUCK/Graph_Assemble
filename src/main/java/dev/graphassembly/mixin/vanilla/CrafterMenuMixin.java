package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.vanilla.crafting.*;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(CrafterMenu.class)
public abstract class CrafterMenuMixin {
    @Shadow @Final private Player player;
    @Shadow @Final private CraftingContainer container;
    @Shadow @Final private ResultContainer resultContainer;
    @Unique private boolean graphAssembly$refreshing;
    @Inject(method="refreshRecipeResult",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$preview(CallbackInfo ci) {
        if(((Object)this).getClass()!=CrafterMenu.class||player.level().isClientSide())return;
        if(graphAssembly$refreshing){ci.cancel();return;}
        graphAssembly$refreshing=true;
        try {
            var inputs=container.getItems().stream().map(ItemStack::copy).toList();
            var lookup=CraftingLookup.find(player.level(),container);
            var preview=CraftingLookup.preview(player.level(),lookup);
            if(!ItemStack.listMatches(inputs,container.getItems())){ci.cancel();return;}
            ((CraftingPreview)resultContainer).graphAssembly$preview(lookup.handled());
            if(lookup.handled()) {
                resultContainer.setRecipeUsed(null);resultContainer.setItem(0,preview);ci.cancel();
            }
        } finally {graphAssembly$refreshing=false;}
    }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$noPreviewTransfer(Player player,int slot,CallbackInfoReturnable<ItemStack> cir) {
        if(slot==45&&((CraftingPreview)resultContainer).graphAssembly$preview())cir.setReturnValue(ItemStack.EMPTY);
    }
}
