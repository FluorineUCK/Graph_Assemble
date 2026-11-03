package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.client.gui.GraphGuiAssets;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in F8 resource-reload probe, excluded with the gametest package from the release jar.
 * Vanilla 1.21.1 ignores F3+T while a Screen is open. This calls the same reload API,
 * without injecting screen, draft, recipe, or input state, and reports the retained screen identity.
 */
@EventBusSubscriber(modid = GraphAssembly.ID, value = Dist.CLIENT)
public final class ArtAssetsClientProbe {
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Post event) {
        if (!Boolean.getBoolean("graph_assembly.artAssetsVerification") || event.getKeyCode() != GLFW.GLFW_KEY_F8) return;
        var minecraft = Minecraft.getInstance();
        var screen = minecraft.screen;
        var previous = GraphGuiAssets.current();
        minecraft.reloadResourcePacks().thenRun(() -> minecraft.execute(() -> {
            var next = GraphGuiAssets.current();
            GraphAssembly.LOGGER.info("ART_ASSETS_PROBE reloaded sameScreen={} newSnapshot={} available={}",
                minecraft.screen == screen, next != previous, next.available());
        }));
    }
}
