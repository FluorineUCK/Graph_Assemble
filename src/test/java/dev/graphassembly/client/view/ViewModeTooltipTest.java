package dev.graphassembly.client.view;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewModeTooltipTest {
    @Test void planeViewOffersSphereButExplainsCurrentPanningControls() {
        var tooltip = ViewModeTooltip.button(false);
        assertEquals("graph_assembly.view.plane", tooltip.currentModeKey());
        assertEquals("graph_assembly.view.sphere", tooltip.nextModeKey());
        assertEquals("graph_assembly.view.controls.plane", tooltip.controlsKey());
    }

    @Test void sphereViewOffersPlaneButExplainsCurrentRotationControls() {
        var tooltip = ViewModeTooltip.button(true);
        assertEquals("graph_assembly.view.sphere", tooltip.currentModeKey());
        assertEquals("graph_assembly.view.plane", tooltip.nextModeKey());
        assertEquals("graph_assembly.view.controls.sphere", tooltip.controlsKey());
    }

    @Test void sharedControlsHintFollowsTheActiveView() {
        assertEquals("graph_assembly.view.controls.plane", ViewModeTooltip.controlsKey(false));
        assertEquals("graph_assembly.view.controls.sphere", ViewModeTooltip.controlsKey(true));
    }

    @Test void bothLanguagesNameCurrentAndNextViewsAndDifferentDragActions() throws Exception {
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/graph_assembly/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : new String[]{"graph_assembly.view.current", "graph_assembly.view.switch"})
                assertTrue(language.get(key).getAsString().contains("%s"),
                    locale + ": " + key + " must name the current view or the next view explicitly");
            for (boolean mode : new boolean[]{false, true}) {
                var tooltip = ViewModeTooltip.button(mode);
                for (String key : new String[]{tooltip.currentModeKey(), tooltip.nextModeKey(), tooltip.controlsKey()}) {
                    assertTrue(language.has(key), locale + ": " + key);
                    assertFalse(language.get(key).getAsString().isBlank());
                }
            }
            assertTrue(language.get(ViewModeTooltip.controlsKey(false)).getAsString()
                .contains(locale.equals("zh_cn") ? "平移" : "pan"));
            assertTrue(language.get(ViewModeTooltip.controlsKey(true)).getAsString()
                .contains(locale.equals("zh_cn") ? "旋转" : "rotate"));
        }
    }
}
