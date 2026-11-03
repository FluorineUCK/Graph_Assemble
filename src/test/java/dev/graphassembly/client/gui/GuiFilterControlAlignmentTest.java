package dev.graphassembly.client.gui;

import dev.graphassembly.client.view.FilterScreenLayout;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The dynamic Create buttons must exactly cover the two buttons baked into the filter panel. */
class GuiFilterControlAlignmentTest {
    @Test void dynamicActionGlyphsExactlyCoverTheirBakedFooterGlyphs() throws Exception {
        var atlas = ImageIO.read(Path.of("src/main/resources/assets/graph_assembly")
            .resolve(GraphGuiAssets.ATLAS_PNG).toFile());
        var layout = FilterScreenLayout.fit(640, 360);
        var footer = GraphGuiAssets.Sprite.FILTER_PANEL_FOOTER;
        // The separate chrome copy was removed. The blank ANY button remains baked in the footer.
        int chromeX = footer.x() + (int)layout.modeButton(0).left();
        int chromeY = footer.y() + (int)(layout.modeButton(0).top() - layout.panelFooter().top());
        var icons = new GraphGuiAssets.Sprite[] {GraphGuiAssets.Sprite.CLEAR, GraphGuiAssets.Sprite.CONFIRM};
        var buttons = new dev.graphassembly.client.view.NodePresentation.Bounds[] {layout.clearButton(), layout.confirmButton()};
        for (int i = 0; i < icons.length; i++) {
            var icon = icons[i];
            var button = buttons[i];
            int bx = footer.x() + (int)button.left();
            int by = footer.y() + (int)(button.top() - layout.panelFooter().top());
            for (int y = 0; y < 18; y++) for (int x = 0; x < 18; x++) {
                int rendered = atlas.getRGB(chromeX + x, chromeY + y);
                // Create IconButton draws its 16px glyph at button origin + (1,1).
                if (x >= 1 && x < 17 && y >= 1 && y < 17) {
                    int glyph = atlas.getRGB(icon.x() + x - 1, icon.y() + y - 1);
                    if ((glyph >>> 24) != 0) rendered = glyph;
                }
                assertEquals(atlas.getRGB(bx + x, by + y), rendered,
                    icon + " is offset from its baked button at " + x + "," + y);
            }
        }
    }

    @Test void bakedAddButtonBordersMatchTheNormalButtonSprite() throws Exception {
        var atlas = ImageIO.read(Path.of("src/main/resources/assets/graph_assembly")
            .resolve(GraphGuiAssets.ATLAS_PNG).toFile());
        assertNotNull(atlas);
        var panel = GraphGuiAssets.Sprite.FILTER_PANEL;
        var layout = FilterScreenLayout.fit(280, 200);
        var footer = GraphGuiAssets.Sprite.FILTER_PANEL_FOOTER;
        int chromeX = footer.x() + (int)layout.modeButton(0).left();
        int chromeY = footer.y() + (int)(layout.modeButton(0).top() - layout.panelFooter().top());
        var buttons = new java.util.ArrayList<dev.graphassembly.client.view.NodePresentation.Bounds>();
        buttons.add(layout.addButton()); buttons.add(layout.addInvertedButton());
        for (int mode = 0; mode < 3; mode++) buttons.add(layout.modeButton(mode));
        buttons.add(layout.clearButton()); buttons.add(layout.confirmButton());
        for (var button : buttons) {
            assertEquals(18, button.right() - button.left());
            assertEquals(18, button.bottom() - button.top());
            int x = panel.x() + (int) button.left();
            int y = button.top() < layout.panelFooter().top() ? panel.y() + (int)button.top()
                : GraphGuiAssets.Sprite.FILTER_PANEL_FOOTER.y() + (int)(button.top() - layout.panelFooter().top());
            for (int py = 0; py < 18; py++) for (int px = 0; px < 18; px++) {
                if (px > 1 && px < 16 && py > 1 && py < 16) continue;
                assertEquals(atlas.getRGB(chromeX + px, chromeY + py), atlas.getRGB(x + px, y + py),
                    "Button border mismatch at " + px + "," + py);
            }
        }
    }
}
