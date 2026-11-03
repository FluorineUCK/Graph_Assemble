package dev.graphassembly.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.IOException;
import java.util.EnumSet;

/** CLIENT-only PNG validation; texture upload/reload stays with Minecraft's TextureManager. */
public final class GraphGuiReloadListener extends SimplePreparableReloadListener<GraphGuiAssets.Snapshot> {
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("graph_assembly", path); }

    @Override protected GraphGuiAssets.Snapshot prepare(ResourceManager resources, ProfilerFiller profiler) {
        var available = EnumSet.noneOf(GraphGuiAssets.Sprite.class);
        var resource = resources.getResource(id(GraphGuiAssets.ATLAS_PNG));
        if (resource.isPresent()) {
            try (var input = resource.get().open(); var image = NativeImage.read(input)) {
                if (GraphGuiAssets.matchesAtlasSize(image.getWidth(), image.getHeight()))
                    available = EnumSet.allOf(GraphGuiAssets.Sprite.class);
                else LogUtils.getLogger().warn("Graph GUI atlas must be 1024x512 or an integer multiple; using fallback ({}x{})",
                    image.getWidth(), image.getHeight());
            } catch (IOException | RuntimeException error) {
                LogUtils.getLogger().warn("Graph GUI atlas invalid; using fallback: {}", error.toString());
            }
        }
        boolean background = false;
        var tile = resources.getResource(id(GraphGuiAssets.CANVAS_BACKGROUND_PNG));
        if (tile.isPresent()) {
            try (var input = tile.get().open(); var image = NativeImage.read(input)) {
                background = image.getWidth() == image.getHeight() && image.getWidth() >= GraphGuiAssets.CANVAS_TILE_SIZE
                    && image.getWidth() % GraphGuiAssets.CANVAS_TILE_SIZE == 0;
                if (!background) LogUtils.getLogger().warn("Graph canvas PNG must be square, 16px or an integer multiple; keeping the panel background");
            } catch (IOException | RuntimeException error) {
                LogUtils.getLogger().warn("Graph canvas PNG invalid; keeping the panel background: {}", error.toString());
            }
        }
        return new GraphGuiAssets.Snapshot(available, background);
    }

    @Override protected void apply(GraphGuiAssets.Snapshot prepared, ResourceManager resources, ProfilerFiller profiler) {
        GraphGuiAssets.publish(prepared);
        dev.graphassembly.client.view.PreviewReloads.resourcesChanged();
    }
}
