package dev.graphassembly.examples;

import com.google.gson.JsonParser;
import dev.graphassembly.core.*;
import dev.graphassembly.client.view.SettlementDescription;
import dev.graphassembly.client.view.SettlementViewModel;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ThreePathOutputsExampleTest {
    // The custom recipe is deliberately a JSON literal, also copyable into a datapack.
    private static GraphDefinition example() throws Exception {
        var script = Files.readString(Path.of("examples/kubejs/three_path_outputs.js"));
        int start = script.indexOf("event.custom(") + "event.custom(".length();
        int end = script.lastIndexOf(").id(");
        return GraphCodec.parse("example:three_path_outputs",
            JsonParser.parseString(script.substring(start, end)).getAsJsonObject());
    }

    private static Completion finish(GraphDefinition graph, String step, int visits, String finish, double draw) {
        var state = GraphEngine.complete(graph, null, "blank", "minecraft:raw_iron",
            () -> { throw new AssertionError("A choice entrance must not draw RNG"); }).state();
        for (int i = 0; i < visits; i++)
            state = GraphEngine.complete(graph, state, step, "minecraft:iron_nugget",
                () -> { throw new AssertionError("A choice processing step must not draw RNG"); }).state();
        return GraphEngine.complete(graph, state, finish, "minecraft:iron_nugget", () -> draw);
    }

    @Test void cuttingRouteSettlesAtBothInclusiveBoundaries() throws Exception {
        var graph = example();
        for (int visits : new int[]{1, 2}) {
            var result = finish(graph, "cut", visits, "finish_iron", .99);
            assertTrue(result.settled());
            assertNull(result.state());
            assertEquals("minecraft:iron_ingot", result.output().item());
            assertEquals(1, result.output().count());
        }
    }

    @Test void otherTwoRoutesYieldTheirOwnProductsNotARandomThreeProductPool() throws Exception {
        var graph = example();
        assertTrue(graph.outputs().isEmpty(), "An unconditional global output would bypass local failure pools");
        for (int visits : new int[]{2, 3}) for (double draw : new double[]{0, .99})
            assertEquals("minecraft:gold_ingot", finish(graph, "wire", visits, "finish_gold", draw).output().item());
        for (double draw : new double[]{0, .99})
            assertEquals("minecraft:diamond", finish(graph, "fill", 1, "finish_diamond", draw).output().item());
    }

    private static void pool(GraphDefinition graph, String step, int visits, String end,
                             String first, int countA, String second, int countB, double boundary) {
        for (double draw : new double[]{0, boundary - 1e-6, boundary + 1e-6, Math.nextDown(1.0)}) {
            var result = finish(graph, step, visits, end, draw);
            assertTrue(result.settled());
            assertNull(result.state(), "Strict failure must not leave another progressing workpiece");
            assertNull(result.item());
            assertEquals(draw < boundary ? first : second, result.output().item());
            assertEquals(draw < boundary ? countA : countB, result.output().count());
        }
    }

    @Test void overProcessedIronUsesOnlyItsThreeToOneScrapPool() throws Exception {
        pool(example(), "cut", 3, "finish_iron", "minecraft:iron_nugget", 3, "minecraft:gravel", 1, .75);
    }

    @Test void underOrOverProcessedGoldUsesOnlyItsTwoToOneScrapPool() throws Exception {
        for (int visits : new int[]{1, 4})
            pool(example(), "wire", visits, "finish_gold", "minecraft:gold_nugget", 2, "minecraft:redstone", 1, 2.0 / 3);
    }

    @Test void overFilledDiamondUsesOnlyItsOneToThreeScrapPool() throws Exception {
        pool(example(), "fill", 2, "finish_diamond", "minecraft:quartz", 1, "minecraft:flint", 2, .25);
    }

    @Test void illegalExitIsStoppedRatherThanTreatedAsAScrapRoll() throws Exception {
        var graph = example();
        assertThrows(IllegalArgumentException.class, () -> finish(graph, "cut", 1, "finish_gold", 0));
        assertThrows(IllegalArgumentException.class, () -> finish(graph, "cut", 0, "finish_iron", 0));
    }

    @Test void existingJeiModelsExposeThreeProductsAndThreeSeparateFailurePools() throws Exception {
        var graph = example();
        var view = SettlementViewModel.from(graph);
        assertEquals(3, view.rules().size());
        assertEquals(3, view.failures().size());
        for (var rule : view.rules()) assertEquals(List.of(rule.sourceNode()), rule.effectiveFinishes());
        assertEquals(Set.of("finish_iron", "finish_gold", "finish_diamond"),
            view.failures().stream().map(SettlementViewModel.Failure::finish).collect(java.util.stream.Collectors.toSet()));
        var description = SettlementDescription.from(graph);
        assertEquals(9, description.outputs().size());
        assertEquals(3, description.paragraphs().stream().filter(line -> line.key().equals("failure.pool")).count());
        assertEquals(3, description.paragraphs().stream().filter(line -> line.key().equals("mark_range")).count());
        assertEquals(3, description.paragraphs().stream().filter(line -> line.key().equals("checkpoint_count")).count());
        var lines = description.paragraphs();
        int firstExit = lines.indexOf(lines.stream().filter(line -> line.key().equals("finish")).findFirst().orElseThrow());
        int nextExit = firstExit + 1;
        while (nextExit < lines.size() && !lines.get(nextExit).key().equals("finish")) nextExit++;
        var firstSection = lines.subList(firstExit, nextExit);
        assertEquals(1, firstSection.stream().filter(line -> line.key().equals("failure.pool")).count(),
            "Each exit keeps its own failure pool next to its conditions");
        assertEquals(3, firstSection.stream().filter(line -> line.key().startsWith("output")).count());
        assertFalse(lines.stream().anyMatch(line -> line.key().equals("merge")), "Independent exits do not merge pools");
    }
}
