package dev.graphassembly.examples;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.io.OutputTrees;
import dev.graphassembly.core.io.OutputItem;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
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
        var low = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "minecraft:copper_ingot"), () -> 0);
        assertEquals(List.of("minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:redstone"), ((TransitionResult.Settled) low).outputs().stream().map(OutputItem::item).toList());
        assertEquals(3, ((TransitionResult.Settled) low).outputs().get(1).count());
        var high = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "minecraft:copper_ingot"), () -> .99);
        assertEquals(List.of("minecraft:diamond"), ((TransitionResult.Settled) high).outputs().stream().map(OutputItem::item).toList());
        assertEquals(5, OutputTrees.maxStacks(((TransitionPlan.SettlementPlan) GraphPipeline.plan(graph, null, "finish", "minecraft:copper_ingot")).outputPool()));
    }
}
