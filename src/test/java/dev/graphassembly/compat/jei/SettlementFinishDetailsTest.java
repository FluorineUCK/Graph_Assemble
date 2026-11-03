package dev.graphassembly.compat.jei;

import dev.graphassembly.client.view.SettlementGroups;
import dev.graphassembly.core.GraphCodec;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementFinishDetailsTest {
    @Test void singleRuleSummarizesRangesWithoutHidingAdditionalPathRequirements() throws Exception {
        var script = Files.readString(Path.of("examples/kubejs/three_path_outputs.js"));
        var graph = GraphCodec.parse("test:details", JsonParser.parseString(script.substring(
            script.indexOf("event.custom(") + "event.custom(".length(), script.lastIndexOf(").id("))).getAsJsonObject());
        var finishes = SettlementGroups.from(graph).finishes();
        var gold = SettlementFinishDetails.from(finishes.get(1));
        assertEquals("quality 2–3; wire ×2–3", gold.ranges());
        assertTrue(gold.moreConditions());
        assertEquals(1, gold.rules());
        assertEquals("quality 1; fill ×1", SettlementFinishDetails.from(finishes.get(2)).ranges());
    }
}
