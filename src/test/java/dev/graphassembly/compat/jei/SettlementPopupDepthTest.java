package dev.graphassembly.compat.jei;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source/API depth contract for pinned Minecraft 1.21.1 + JEI 19, not a substitute for visual acceptance. */
class SettlementPopupDepthTest {
    @Test void panelIsAboveTheGraphPlaneAfterGraphWidgetClearsPreviewDepth() {
        assertTrue(SettlementFooterWidget.POPUP_Z > 0);
    }
    @Test void nativeItemAndCountLayersAreStrictlyBelowJeiTooltipTextAndBackground() {
        // Verified GuiGraphics.renderItem/renderItemDecorations/renderTooltipInternal sources.
        int nativeItemOffset = 150, nativeCountOffset = 200, tooltipZ = 400;
        assertTrue(SettlementFooterWidget.POPUP_Z + nativeItemOffset < tooltipZ,
            "Native item models must not occlude the tooltip");
        assertTrue(SettlementFooterWidget.POPUP_Z + nativeCountOffset < tooltipZ,
            "Item counts must not reach or exceed the tooltip plane");
    }
}
