package dev.graphassembly.examples;

import com.google.gson.JsonParser;
import dev.graphassembly.core.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NestedProbabilityOutputsExampleTest {
    @Test void publishedExampleKeepsFixedOutputAndBothIndependentAndNestedProbabilityRules() throws Exception {
        var script = Files.readString(Path.of("examples/kubejs/nested_probability_outputs.js"));
        int start = script.indexOf("event.custom(") + "event.custom(".length();
        var graph = GraphCodec.parse("example:nested_probability_outputs", JsonParser.parseString(script.substring(start, script.lastIndexOf(").id("))).getAsJsonObject());
        var low = GraphEngine.complete(graph, null, "finish", "minecraft:copper_ingot", () -> 0);
        assertEquals(List.of("minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:redstone"), low.outputs().stream().map(WeightedOutput::item).toList());
        assertEquals(3, low.outputs().get(1).count());
        var high = GraphEngine.complete(graph, null, "finish", "minecraft:copper_ingot", () -> .99);
        assertEquals(List.of("minecraft:diamond"), high.outputs().stream().map(WeightedOutput::item).toList());
        assertEquals(5, OutputTrees.maxStacks(GraphEngine.plan(graph, null, "finish", "minecraft:copper_ingot").pool()));
    }
}
