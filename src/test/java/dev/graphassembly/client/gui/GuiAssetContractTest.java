package dev.graphassembly.client.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class GuiAssetContractTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/graph_assembly");

    @Test void artistAssetsNeedOnlyPngAndStandardItemModelReference() throws Exception {
        assertFalse(Files.exists(ASSETS.resolve("ui/palette.json")), "Remove custom art JSON");
        try (var files = Files.walk(ASSETS.resolve("textures/gui"))) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".mcmeta")), "UV and scaling live in Java");
        }
        assertNotNull(ImageIO.read(ASSETS.resolve(GraphGuiAssets.ATLAS_PNG).toFile()));
        var item = ImageIO.read(ASSETS.resolve("textures/item/graph_filter.png").toFile());
        assertNotNull(item);
        assertEquals(16, item.getWidth());
        assertEquals(16, item.getHeight());
        var model = JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/graph_filter.json"))).getAsJsonObject();
        assertEquals("graph_assembly:item/graph_filter_appearance", model.get("parent").getAsString());
        var appearance = JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/graph_filter_appearance.json"))).getAsJsonObject();
        assertEquals("minecraft:item/generated", appearance.get("parent").getAsString());
        assertEquals("graph_assembly:item/graph_filter", appearance.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test void reloadReplacesRatherThanAccumulatesAtlasAvailability() {
        var previous = GraphGuiAssets.current();
        try {
            var set = EnumSet.allOf(GraphGuiAssets.Sprite.class);
            var first = new GraphGuiAssets.Snapshot(set);
            set.clear();
            GraphGuiAssets.publish(first);
            assertTrue(GraphGuiAssets.current().available().contains(GraphGuiAssets.Sprite.FILTER_PANEL));
            assertThrows(UnsupportedOperationException.class, () -> first.available().clear());
            GraphGuiAssets.publish(new GraphGuiAssets.Snapshot(set));
            assertTrue(GraphGuiAssets.current().available().isEmpty());
            assertFalse(first.available().isEmpty());
        } finally { GraphGuiAssets.publish(previous); }
    }

    @Test void retainedIconsHaveExplicitFallbacksWithoutDeletedButtonArtwork() {
        assertEquals(GraphGuiAssets.Fallback.PLANE, GraphGuiAssets.Sprite.PLANE.fallback());
        assertEquals(GraphGuiAssets.Fallback.SPHERE, GraphGuiAssets.Sprite.SPHERE.fallback());
        assertEquals(GraphGuiAssets.Fallback.ADD, GraphGuiAssets.Sprite.ADD.fallback());
        assertEquals(GraphGuiAssets.Fallback.TRASH, GraphGuiAssets.Sprite.REMOVE.fallback());
        assertEquals(GraphGuiAssets.Fallback.TRASH, GraphGuiAssets.Sprite.CLEAR.fallback());
        assertEquals(GraphGuiAssets.Fallback.CONFIRM, GraphGuiAssets.Sprite.CONFIRM.fallback());
        assertEquals(GraphGuiAssets.Fallback.FILTER_CHOICE, GraphGuiAssets.Sprite.FILTER_CHOICE.fallback());
        assertNotEquals(GraphGuiAssets.Sprite.FILTER_PANEL, GraphGuiAssets.Sprite.FILTER_CHOICE);
        assertNotEquals(GraphGuiAssets.Sprite.CLEAR.path(), GraphGuiAssets.Sprite.REMOVE.path());
    }
}
