package dev.graphassembly.core.io;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class NestedOutputsTest {
    @Test void fixedAAndIndependentBAndCCanAllBeDeliveredTogether() {
        var graph = graph("""
            [{"mode":"all","results":[
                {"item":"test:a"}, {"item":"test:b","chance":0.25}, {"item":"test:c","chance":0.1}
            ]}]
            """);
        var result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .1, .05));
        assertTrue((result instanceof TransitionResult.Settled));
        assertEquals(List.of("test:a", "test:b", "test:c"), ((TransitionResult.Settled) result).outputs().stream().map(OutputItem::item).toList());
        assertEquals(List.of("test:a", "test:b"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .1, .9))).outputs().stream().map(OutputItem::item).toList());
        assertEquals(List.of("test:a", "test:c"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .9, .05))).outputs().stream().map(OutputItem::item).toList());
        assertEquals(List.of("test:a"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .9, .9))).outputs().stream().map(OutputItem::item).toList());
    }
    @Test void countUsesNativePerItemChanceRatherThanAnAllOrNothingStack() {
        var graph = graph("""
            [{"item":"test:a","count":3,"chance":0.5}]
            """);
        var output = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .2, .8, .3));
        assertEquals(2, ((TransitionResult.Settled) output).outputs().getFirst().count());
        assertTrue(((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .9, .9, .9))).outputs().isEmpty());
    }
    @Test void nestedPoolsKeepTheirLocalWeightsAndCanSelectSimultaneousBundles() {
        var graph = graph("""
            [{"mode":"all","results":[{"item":"test:a"},
              {"mode":"weighted","results":[{"item":"test:b","weight":3},
                {"mode":"all","weight":1,"results":[{"item":"test:c"},
                  {"mode":"weighted","results":[{"item":"test:d","weight":2},{"item":"test:e","weight":1}]}]}]}]}]
            """);
        assertEquals(List.of("test:a", "test:b"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .7))).outputs().stream().map(OutputItem::item).toList());
        assertEquals(List.of("test:a", "test:c", "test:e"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .9, .9))).outputs().stream().map(OutputItem::item).toList());
    }
    @Test void emptySuccessDoesNotDrawScrapOrRerollAndGroupGatesRunOnce() {
        var graph = graph("""
            [{"mode":"all","chance":0.5,"results":[{"item":"test:a"},{"item":"test:b"}]}]
            """);
        var empty = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .9));
        assertTrue((empty instanceof TransitionResult.Settled)); assertTrue(((TransitionResult.Settled) empty).outputs().isEmpty());
        assertEquals(2, ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .1))).outputs().size());
        assertTrue(((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph("[{\"item\":\"test:a\",\"chance\":0}]"), null, "finish", "test:raw"), draws(0))).outputs().isEmpty());
    }
    @Test void maximalBundlesKeepWeightedAlternativesSeparateAndReserveSimultaneousItemsTogether() {
        var graph = graph("""
            [{"mode":"all","results":[{"item":"test:a"},{"mode":"weighted","results":[
              {"item":"test:b","count":3,"chance":0.2},{"item":"test:c"}]}]}]
            """);
        var plan = GraphPipeline.plan(graph, null, "finish", "test:raw");
        var bundles = new java.util.ArrayList<List<String>>();
        assertTrue(OutputTrees.allMaximalOutcomesFit(((TransitionPlan.SettlementPlan) plan).outputPool(), items -> {
            bundles.add(items.stream().map(item -> item.item() + ":" + item.count()).toList()); return true;
        }));
        assertEquals(List.of(List.of("test:a:1", "test:b:3"), List.of("test:a:1", "test:c:1")), bundles);
        assertEquals(2, OutputTrees.maxStacks(((TransitionPlan.SettlementPlan) plan).outputPool()));
    }
    @Test void nestedStrictScrapRollsBundlesOnlyWhenNoSuccessRuleMatches() {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"finish","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"}}],
             "edges":[],"outputs":[{"results":[{"item":"test:success"}]}]}
            """).getAsJsonObject();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements",
            JsonParser.parseString("{\"mark_ranges\":{\"missing\":{\"min\":1}}}"));
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("scrap", JsonParser.parseString("""
            [{"mode":"weighted","results":[{"mode":"all","weight":3,"results":[
              {"item":"test:dust","count":2},{"item":"test:bonus","chance":0.5}]},
              {"item":"test:other","weight":1}]}]
            """));
        var result = GraphPipeline.resolve(GraphPipeline.plan(GraphCodec.parse("test:nested", json), null, "finish", "test:raw"), draws(0, .5, .4));
        assertEquals(List.of("test:dust", "test:bonus"), ((TransitionResult.Settled) result).outputs().stream().map(OutputItem::item).toList());
        assertEquals(2, ((TransitionResult.Settled) result).outputs().size());
    }
    @Test void invalidTreeDataIsRejectedWithItsNestedPath() {
        for (String bad : List.of("{\"item\":\"test:a\",\"chance\":1.1}",
                "{\"item\":\"test:a\",\"chance\":-0.1}", "{\"item\":\"test:a\",\"chance\":\"0.5\"}",
                "{\"mode\":\"weighted\",\"results\":[]}", "{\"mode\":\"unknown\",\"results\":[]}",
                "{\"mode\":\"all\",\"item\":\"test:a\",\"results\":[]}",
                "{\"item\":\"test:a\",\"weight\":0}", "{\"item\":\"test:a\",\"channce\":0.5}")) {
            var error = assertThrows(IllegalArgumentException.class,
                () -> graph("[{\"mode\":\"all\",\"results\":[" + bad + "]}]"));
            assertTrue(error.getMessage().contains("results[0].results[0]"), error.getMessage());
        }
    }
    @Test void emptyAllIsExplicitSuccessAndNeverUsesChanceOrScrapRng() {
        var result = GraphPipeline.resolve(GraphPipeline.plan(graph("[{\"mode\":\"all\",\"results\":[]}]"), null, "finish", "test:raw"), draws(0));
        assertTrue((result instanceof TransitionResult.Settled)); assertTrue(((TransitionResult.Settled) result).outputs().isEmpty());
    }
    @Test void allChildrenIgnoreTheirWeightsAndChanceBoundariesMatchCreate() {
        var graph = graph("""
            [{"mode":"all","results":[{"item":"test:a","weight":10000,"chance":1},
              {"item":"test:b","weight":0.001,"chance":0.5}, {"item":"test:never","chance":0}]}]
            """);
        assertEquals(List.of("test:a", "test:b"), ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "finish", "test:raw"), draws(0, .5))).outputs().stream().map(OutputItem::item).toList());
    }
    @Test void deepMixedNestingAndCapacityChecksDoNotNeedFlatteningOrRng() {
        String results = "{\"item\":\"test:a\"}";
        for (int i = 0; i < 64; i++) results = "{\"mode\":\"" + (i % 2 == 0 ? "all" : "weighted") + "\",\"results\":[" + results + "]}";
        var plan = GraphPipeline.plan(graph("[" + results + "]"), null, "finish", "test:raw");
        assertEquals("test:a", ((TransitionResult.Settled) GraphPipeline.resolve(plan, () -> 0)).outputs().getFirst().item());
        var visits = new AtomicInteger();
        assertFalse(OutputTrees.allMaximalOutcomesFit(((TransitionPlan.SettlementPlan) plan).outputPool(), batch -> { visits.incrementAndGet(); return false; }));
        assertEquals(1, visits.get());
    }
    @Test void capacityTraversalVisitsCartesianBundlesAndStopsOnFirstFailure() {
        var graph = graph("""
            [{"mode":"all","results":[
              {"mode":"weighted","results":[{"item":"test:a"},{"item":"test:b"}]},
              {"mode":"weighted","results":[{"item":"test:c"},{"item":"test:d"}]}]}]
            """);
        var pool = ((TransitionPlan.SettlementPlan) GraphPipeline.plan(graph, null, "finish", "test:raw")).outputPool();
        var combinations = new java.util.ArrayList<List<String>>();
        assertTrue(OutputTrees.allMaximalOutcomesFit(pool, batch -> { combinations.add(batch.stream().map(WeightedOutput::item).toList()); return true; }));
        assertEquals(List.of(List.of("test:a", "test:c"), List.of("test:a", "test:d"),
            List.of("test:b", "test:c"), List.of("test:b", "test:d")), combinations);
        var calls = new AtomicInteger();
        assertFalse(OutputTrees.allMaximalOutcomesFit(pool, batch -> calls.incrementAndGet() < 2));
        assertEquals(2, calls.get());
    }
    public static GraphDefinition<OperationSpec> graph(String results) {
        return GraphCodec.parse("test:nested", JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"finish","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"},
                "scrap":[{"item":"test:scrap"}]}],"edges":[],"outputs":[{"results":%s}]}
            """.formatted(results)).getAsJsonObject());
    }
    static java.util.function.DoubleSupplier draws(double... values) {
        var index = new AtomicInteger();
        return () -> { assertTrue(index.get() < values.length, "Unexpected additional RNG draw"); return values[index.getAndIncrement()]; };
    }
}
