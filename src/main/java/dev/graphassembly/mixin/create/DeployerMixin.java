package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.*;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.integration.sequence.NativeSequences;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerMixin {
    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$recipe(ItemStack input, CallbackInfoReturnable<RecipeHolder<? extends Recipe<? extends RecipeInput>>> cir) {
        var self = (DeployerBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null || self.getPlayer() == null) return;
        var held = self.getPlayer().getMainHandItem();
        boolean polishingHandled=false;
        if (held.getItem() instanceof com.simibubi.create.content.equipment.sandPaper.SandPaperItem && !NativeSequences.tracked(held)) {
            var polishing=dev.graphassembly.integration.SandpaperOperations.lookup(level,input);
            polishingHandled=polishing.handled();
            if (!polishing.recipes().isEmpty()) {
                var holder=polishing.first().orElseThrow();
                var op=dev.graphassembly.integration.GraphOperation.get(holder.value());
                dev.graphassembly.integration.GraphOperation.attach(holder.value(),new dev.graphassembly.integration.GraphOperation(level,op.graph(),op.node(),input,java.util.List.of(held)));
                cir.setReturnValue(holder);return;
            }
        }
        var inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, input); inventory.setStackInSlot(1, held);
        var wrapper = new RecipeWrapper(inventory);
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class,
            r -> r.value().matches(wrapper, level), false, java.util.List.of(held));
        if (lookup.handled() || polishingHandled || NativeSequences.tracked(held)) cir.setReturnValue(lookup.first().orElse(null));
    }
}
