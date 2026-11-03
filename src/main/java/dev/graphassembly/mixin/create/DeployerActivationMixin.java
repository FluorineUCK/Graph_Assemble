package dev.graphassembly.mixin.create;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.graphassembly.integration.GraphOperation;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeltDeployerCallbacks.class)
public abstract class DeployerActivationMixin {
    @Inject(method = "activate", at = @At("HEAD"), cancellable = true)
    private static void graphAssembly$revalidate(TransportedItemStack input, TransportedItemStackHandlerBehaviour handler,
                                                DeployerBlockEntity deployer, Recipe<?> recipe, CallbackInfo ci) {
        var player = deployer.getPlayer();
        if (player == null || deployer.getLevel() == null) { ci.cancel(); return; }
        var held = player.getMainHandItem();
        var operation = GraphOperation.get(recipe);
        if (operation != null ? !operation.matchesHeld(deployer.getLevel(), input.stack, held)
            : Workpiece.has(input.stack) || Workpiece.has(held)) ci.cancel();
    }
}
