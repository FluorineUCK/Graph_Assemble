package dev.graphassembly.mixin.vanilla.bridge;

import dev.graphassembly.integration.bridge.NativeRecipeCatalog;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeLifecycleMixin {
    @Inject(method={"replaceRecipes", "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"},at=@At("RETURN"))
    private void graphAssembly$releaseDefinitions(CallbackInfo ci) {
        var manager=(RecipeManager)(Object)this;
        NativeRecipeCatalog.release(manager);
        dev.graphassembly.integration.sequence.NativeSequences.release(manager);
        RuntimeSnapshots.release(manager);
        GraphIndex.invalidate();
    }
}
