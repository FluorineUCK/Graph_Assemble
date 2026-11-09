package dev.graphassembly.core.condition;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class RangeRequirementsTest {
    public static JsonObject graphJson() {
        return JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"wash","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"polish","checkpoint":true,"recipe":{"type":"create:pressing"}},
                      {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"wash","to":"polish"},{"from":"polish","to":"polish"},
                      {"from":"polish","to":"finish"}],
             "outputs":[{"results":[{"item":"test:result"}]}]}
            """).getAsJsonObject();
    }

    static ProgressRequirements requirements(String fields) {
        var json = graphJson();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject()
            .add("requirements", JsonParser.parseString(fields));
        return GraphCodec.parse("test:ranges",json).outputRules().getFirst().requirements();
    }

    @Test void rangesCombineWithOrderedCheckpointsAndCountRepeatedVisits() {
        var requirements = requirements("""
            {"mark_ranges":{"quality":{"min":3,"max":5}},
             "checkpoints":{"mode":"partial","nodes":["wash","finish"]},
             "checkpoint_counts":{"polish":{"min":2,"max":4}}}
            """);
        for (int quality : List.of(3, 4, 5))
            assertTrue(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", quality, "other", 10), List.of("wash", "polish", "polish", "finish"))));
        for (int quality : List.of(0, 2, 6))
            assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", quality), List.of("wash", "polish", "polish", "finish"))));
        assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 4), List.of("finish", "polish", "polish", "wash"))));
        assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 4), List.of("wash", "polish", "finish"))));
        assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 4), List.of("wash", "polish", "polish", "polish", "polish", "polish", "finish"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "2", "{\"min\":null}", "{\"min\":1.5}",
        "{\"min\":-1}", "{\"max\":2147483648}", "{\"min\":2147483648}",
        "{\"min\":2,\"max\":1}", "{\"max\":\"3\"}", "{\"min\":true}", "{\"typo\":0}"})
    void rejectsInvalidRangeShapesWithGraphAndFieldPath(String range) {
        for (String field : List.of("mark_ranges", "checkpoint_counts")) {
            String key = field.equals("mark_ranges") ? "quality" : "polish";
            var error = assertThrows(IllegalArgumentException.class,
                () -> requirements("{\"" + field + "\":{\"" + key + "\":" + range + "}}"));
            assertTrue(error.getMessage().contains("Graph test:ranges:"), error.getMessage());
            assertTrue(error.getMessage().contains("graph.outputs[0].requirements." + field + "." + key), error.getMessage());
        }
    }

    @Test void missingCountsAreZeroAndMissingBoundsAreInclusiveDefaults() {
        var zero = requirements("""
            {"mode":"exact","mark_ranges":{"damage":{"max":0}},"checkpoint_counts":{"polish":{"max":0}}}
            """);
        assertTrue(ProgressMatcher.matches(zero, new ProgressFacts(Map.of("unrelated", 10), List.of("wash", "finish"))));
        assertFalse(ProgressMatcher.matches(zero, new ProgressFacts(Map.of("damage", 1), List.of())));
        assertFalse(ProgressMatcher.matches(zero, new ProgressFacts(Map.of(), List.of("polish"))));
        var minimum = requirements("{\"mark_ranges\":{\"quality\":{\"min\":2147483647}}}");
        assertTrue(ProgressMatcher.matches(minimum, new ProgressFacts(Map.of("quality", Integer.MAX_VALUE), List.of())));
        assertFalse(ProgressMatcher.matches(minimum, new ProgressFacts(Map.of("quality", Integer.MAX_VALUE - 1), List.of())));
        var empty = requirements("{\"mark_ranges\":{},\"checkpoint_counts\":{}}");
        assertEquals(ProgressRequirements.NONE, empty);
        assertTrue(ProgressMatcher.matches(empty, new ProgressFacts(Map.of("extra", 1), List.of("wash"))));
        assertFalse(ProgressMatcher.matches(requirements("{\"marks\":{},\"mark_ranges\":{}}"), new ProgressFacts(Map.of("extra", 1), List.of())));
    }

    @Test void countReferencesRejectUnknownNodesAtTheirExactPath() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> requirements("{\"checkpoint_counts\":{\"missing\":{\"max\":0}}}"));
        assertTrue(error.getMessage().contains("Graph test:ranges:"), error.getMessage());
        assertTrue(error.getMessage().contains("graph.outputs[0].requirements.checkpoint_counts.missing"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "1"})
    void rangeMapsMustBeObjects(String value) {
        for (String field : List.of("mark_ranges", "checkpoint_counts")) {
            var error = assertThrows(IllegalArgumentException.class,
                () -> requirements("{\"" + field + "\":" + value + "}"));
            assertTrue(error.getMessage().contains("graph.outputs[0].requirements." + field), error.getMessage());
        }
    }

    @Test void upperBoundsApplyIndependentlyOfLegacyModesAndLegacyUnorderedDuplicates() {
        var bounded = requirements("""
            {"mode":"partial","marks":{"quality":2},"mark_ranges":{"quality":{"max":3}},
             "checkpoints":{"mode":"unordered","nodes":["polish","polish"]},
             "checkpoint_counts":{"polish":{"min":2,"max":3}}}
            """);
        assertTrue(ProgressMatcher.matches(bounded, new ProgressFacts(Map.of("quality", 3, "extra", 8), List.of("polish", "wash", "polish"))));
        assertFalse(ProgressMatcher.matches(bounded, new ProgressFacts(Map.of("quality", 4), List.of("polish", "polish"))));
        assertFalse(ProgressMatcher.matches(bounded, new ProgressFacts(Map.of("quality", 1), List.of("polish", "polish"))));
        assertFalse(ProgressMatcher.matches(bounded, new ProgressFacts(Map.of("quality", 2), List.of("polish"))));
        assertTrue(ProgressMatcher.matches(requirements("{\"checkpoints\":{\"mode\":\"unordered\",\"nodes\":[\"polish\",\"polish\"]}}"), new ProgressFacts(Map.of(), List.of("polish"))), "Legacy unordered still ignores duplicate counts");
    }

    @Test void finishRangesReadPostRewardFullCheckpointsAndMergeMatchingPoolsWithOneDraw() {
        var json = graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("mark_amounts", JsonParser.parseString("{\"quality\":3}"));
        var finish = json.getAsJsonArray("nodes").get(2).getAsJsonObject();
        finish.add("mark_amounts", JsonParser.parseString("{\"quality\":2}"));
        json.add("outputs", JsonParser.parseString("""
            [{"requirements":{"mark_ranges":{"quality":{"min":3,"max":5}},
                "checkpoint_counts":{"wash":{"min":1,"max":1},"polish":{"min":4,"max":4},"finish":{"min":1}}},
              "results":[{"item":"test:global","weight":1}]},
             {"requirements":{"mark_ranges":{"quality":{"max":4}}},"results":[{"item":"test:wrong_snapshot","weight":100}]}]
            """));
        finish.add("outputs", JsonParser.parseString("""
            [{"requirements":{"mark_ranges":{"quality":{"min":5,"max":5}},
               "checkpoint_counts":{"polish":{"min":4,"max":4}}},"results":[{"item":"test:local","weight":3}]}]
            """));
        var graph = GraphCodec.parse("test:ranges", json);
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "wash", "test:raw"), () -> fail("No draw before finish"))).progress();
        for (int i = 0; i < 4; i++)
            state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "polish", "test:work"), () -> fail("No draw before finish"))).progress();
        assertEquals(List.of("polish", "polish", "polish"), state.recentHistory());
        var restored = ProgressCodec.fromJson(ProgressCodec.toJson(state));
        assertEquals(state, restored);
        var draws = new java.util.concurrent.atomic.AtomicInteger();
        var result = GraphPipeline.resolve(GraphPipeline.plan(graph, restored, "finish", "test:work"), () -> { draws.incrementAndGet(); return .75; });
        assertTrue((result instanceof TransitionResult.Settled));
        assertEquals("test:local", ((TransitionResult.Settled) result).outputs().getFirst().item());
        assertInstanceOf(TransitionResult.Settled.class, result);
        assertEquals(1, draws.get());
        assertEquals(Map.of("quality", 3), restored.marks().values(), "Input snapshot is unchanged");
    }

    @Test void unmatchedFinishRangesKeepLenientProgressAndStrictFinishSettlesScrap() {
        var json = graphJson();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements",
            JsonParser.parseString("{\"checkpoint_counts\":{\"polish\":{\"min\":2,\"max\":4}}}"));
        var graph = GraphCodec.parse("test:ranges", json);
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "wash", "test:raw"), () -> fail("No draw"))).progress();
        state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "polish", "test:work"), () -> fail("No draw"))).progress();
        var lenient = GraphPipeline.resolve(GraphPipeline.plan(graph, state, "finish", "test:work"), () -> fail("No matching output draw"));
        assertFalse((lenient instanceof TransitionResult.Settled));
        assertEquals(List.of("wash", "polish", "finish"), ((TransitionResult.Continued) lenient).progress().checkpoints());
        var finish = json.getAsJsonArray("nodes").get(2).getAsJsonObject();
        finish.addProperty("finish_mode", "strict");
        finish.add("scrap", JsonParser.parseString("[{\"item\":\"test:scrap\"}]"));
        var strictGraph = GraphCodec.parse("test:ranges", json);
        var strict = GraphPipeline.resolve(GraphPipeline.plan(strictGraph, state, "finish", "test:work"), () -> 0);
        assertTrue((strict instanceof TransitionResult.Settled));
        assertInstanceOf(TransitionResult.Settled.class, strict);
        assertEquals("test:scrap", ((TransitionResult.Settled) strict).outputs().getFirst().item());
    }
}
