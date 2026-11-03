package dev.graphassembly.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiFooterContrastTest {
    @Test void footerLabelsRemainReadableOnJeisLightRecipeBackground() {
        for (String role : new String[]{"input", "success", "scrap"}) {
            double text = luminance(GuiColors.color("footer." + role));
            double background = luminance(0xFFC6C6C6);
            double contrast = (Math.max(text, background) + .05) / (Math.min(text, background) + .05);
            assertTrue(contrast >= 4.5, role + " contrast=" + contrast);
        }
    }
    private static double luminance(int rgb) {
        return .2126 * channel(rgb >> 16) + .7152 * channel(rgb >> 8) + .0722 * channel(rgb);
    }
    private static double channel(int value) {
        double s = (value & 255) / 255.0;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }
}
