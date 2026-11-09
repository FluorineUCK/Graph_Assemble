package dev.graphassembly.core.condition.checkpoint;

import com.google.gson.JsonParser;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointRequirementTest {
    @ParameterizedTest
    @EnumSource(CheckpointMode.class)
    void nestedModesWorkForBothWorkpieceAndSettlementConditions(CheckpointMode mode) {
        String wireMode = mode == CheckpointMode.SUBSEQUENCE ? "partial" : mode.name().toLowerCase(java.util.Locale.ROOT);
        var requirement = JsonParser.parseString("""
            {"marks":{"mode":"partial","values":{"quality":2}},
             "checkpoints":{"mode":"%s","nodes":["s","s"]},
             "checkpoint_counts":{"s":{"min":1}}}
            """.formatted(wireMode));
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"},
                       "workpiece_inputs":[{"item_input":0}]}],
             "edges":[],"outputs":[{"results":[{"item":"test:result"}]}]}
            """).getAsJsonObject();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("workpiece_inputs")
            .get(0).getAsJsonObject().add("requirements", requirement.deepCopy());
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements", requirement.deepCopy());
        var graph = GraphCodec.parse("test:nested_requirement_modes", json);
        var input = graph.nodes().get("s").inputs().workpieceInput(0).requirements();
        var settlement = graph.outputRules().getFirst().requirements();
        assertEquals(input, settlement);
        assertEquals(mode, input.checkpoints().match().mode());
        for (var requirements : List.of(input, settlement)) {
            assertTrue(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 3), List.of("s", "s"))));
            assertEquals(mode == CheckpointMode.UNORDERED,
                ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 3), List.of("s"))));
            assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 1), List.of("s", "s"))));
            assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("quality", 3), List.of())));
        }
    }
}
