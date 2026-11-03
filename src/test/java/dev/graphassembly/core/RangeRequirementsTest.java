package dev.graphassembly.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RangeRequirementsTest {
    static JsonObject graphJson() {
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

    static CompletionRequirements requirements(String fields) {
        var json = graphJson();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject()
            .add("requirements", JsonParser.parseString(fields));
        return GraphCodec.parse("test:ranges", json).outputs().getFirst().requirements();
    }

    @Test void rangesCombineWithOrderedCheckpointsAndCountRepeatedVisits() {
        var requirements = requirements("""
            {"mark_ranges":{"quality":{"min":3,"max":5}},
             "checkpoints":{"mode":"partial","nodes":["wash","finish"]},
             "checkpoint_counts":{"polish":{"min":2,"max":4}}}
            """);
        for (int quality : List.of(3, 4, 5))
            assertTrue(requirements.matches(Map.of("quality", quality, "other", 10),
                List.of("wash", "polish", "polish", "finish")));
        for (int quality : List.of(0, 2, 6))
            assertFalse(requirements.matches(Map.of("quality", quality), List.of("wash", "polish", "polish", "finish")));
        assertFalse(requirements.matches(Map.of("quality", 4), List.of("finish", "polish", "polish", "wash")));
        assertFalse(requirements.matches(Map.of("quality", 4), List.of("wash", "polish", "finish")));
        assertFalse(requirements.matches(Map.of("quality", 4),
            List.of("wash", "polish", "polish", "polish", "polish", "polish", "finish")));
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
        assertTrue(zero.matches(Map.of("unrelated", 10), List.of("wash", "finish")));
        assertFalse(zero.matches(Map.of("damage", 1), List.of()));
        assertFalse(zero.matches(Map.of(), List.of("polish")));
        var minimum = requirements("{\"mark_ranges\":{\"quality\":{\"min\":2147483647}}}");
        assertTrue(minimum.matches(Map.of("quality", Integer.MAX_VALUE), List.of()));
        assertFalse(minimum.matches(Map.of("quality", Integer.MAX_VALUE - 1), List.of()));
        var empty = requirements("{\"mark_ranges\":{},\"checkpoint_counts\":{}}");
        assertEquals(new CompletionRequirements(null, null), empty);
        assertTrue(empty.matches(Map.of("extra", 1), List.of("wash")));
        assertFalse(requirements("{\"marks\":{},\"mark_ranges\":{}}").matches(Map.of("extra", 1), List.of()));
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
        assertTrue(bounded.matches(Map.of("quality", 3, "extra", 8), List.of("polish", "wash", "polish")));
        assertFalse(bounded.matches(Map.of("quality", 4), List.of("polish", "polish")));
        assertFalse(bounded.matches(Map.of("quality", 1), List.of("polish", "polish")));
        assertFalse(bounded.matches(Map.of("quality", 2), List.of("polish")));
        assertTrue(requirements("{\"checkpoints\":{\"mode\":\"unordered\",\"nodes\":[\"polish\",\"polish\"]}}")
            .matches(Map.of(), List.of("polish")), "Legacy unordered still ignores duplicate counts");
    }

    @Test void finishRangesReadPostRewardFullCheckpointsAndMergeMatchingPoolsWithOneDraw() {
        var json = graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("rewards", JsonParser.parseString("{\"quality\":3}"));
        var finish = json.getAsJsonArray("nodes").get(2).getAsJsonObject();
        finish.add("rewards", JsonParser.parseString("{\"quality\":2}"));
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
        var state = GraphEngine.complete(graph, null, "wash", "test:raw", () -> fail("No draw before finish")).state();
        for (int i = 0; i < 4; i++)
            state = GraphEngine.complete(graph, state, "polish", "test:work", () -> fail("No draw before finish")).state();
        assertEquals(List.of("polish", "polish", "polish"), state.history());
        var restored = Progress.fromJson(state.toJson());
        assertEquals(state, restored);
        var draws = new java.util.concurrent.atomic.AtomicInteger();
        var result = GraphEngine.complete(graph, restored, "finish", "test:work", () -> { draws.incrementAndGet(); return .75; });
        assertTrue(result.settled());
        assertEquals("test:local", result.output().item());
        assertNull(result.state());
        assertEquals(1, draws.get());
        assertEquals(Map.of("quality", 3), restored.marks(), "Input snapshot is unchanged");
    }

    @Test void unmatchedFinishRangesKeepLenientProgressAndStrictFinishSettlesScrap() {
        var json = graphJson();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements",
            JsonParser.parseString("{\"checkpoint_counts\":{\"polish\":{\"min\":2,\"max\":4}}}"));
        var graph = GraphCodec.parse("test:ranges", json);
        var state = GraphEngine.complete(graph, null, "wash", "test:raw", () -> fail("No draw")).state();
        state = GraphEngine.complete(graph, state, "polish", "test:work", () -> fail("No draw")).state();
        var lenient = GraphEngine.complete(graph, state, "finish", "test:work", () -> fail("No matching output draw"));
        assertFalse(lenient.settled());
        assertEquals(List.of("wash", "polish", "finish"), lenient.state().checkpoints());
        var finish = json.getAsJsonArray("nodes").get(2).getAsJsonObject();
        finish.addProperty("finish_mode", "strict");
        finish.add("scrap", JsonParser.parseString("[{\"item\":\"test:scrap\"}]"));
        var strictGraph = GraphCodec.parse("test:ranges", json);
        var strict = GraphEngine.complete(strictGraph, state, "finish", "test:work", () -> 0);
        assertTrue(strict.settled());
        assertNull(strict.state());
        assertEquals("test:scrap", strict.output().item());
    }
}
