package dev.graphassembly.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class WorkpieceInputTest {
    static GraphDefinition graph(String roles) {
        JsonObject json = RangeRequirementsTest.graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject()
            .add("workpiece_inputs", JsonParser.parseString(roles));
        return GraphCodec.parse("test:roles", json);
    }

    @Test void declaringPrimaryInputRequiresExistingProgressEvenAtStart() {
        var graph = graph("[{\"item_input\":0}]");
        assertTrue(GraphEngine.allowed(graph, null).isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> GraphEngine.complete(graph, null, "wash", "test:raw", () -> fail("No draw")));
    }

    @Test void auxiliaryTraversalIgnoresPrimaryConditionsAndHypotheticalRewardOverflow() {
        JsonObject json = RangeRequirementsTest.graphJson();
        var node = json.getAsJsonArray("nodes").get(1).getAsJsonObject();
        node.add("rewards", JsonParser.parseString("{\"quality\":1}"));
        node.add("workpiece_inputs", JsonParser.parseString("""
            [{"item_input":0,"requirements":{"mark_ranges":{"quality":{"min":2}}}}]
            """));
        var graph = GraphCodec.parse("test:roles", json);
        for (int quality : List.of(0, Integer.MAX_VALUE)) {
            var state = state(graph, quality);
            assertFalse(GraphEngine.allowed(graph, state).contains("polish"));
            assertTrue(GraphEngine.canEnter(graph, state, "polish"));
            assertFalse(GraphEngine.canEnter(graph, state, "finish"));
            assertFalse(GraphEngine.canEnter(graph, state, "missing"));
        }
        assertTrue(GraphEngine.canEnter(graph, null, "wash"));
        assertFalse(GraphEngine.canEnter(graph, null, "polish"));
        var foreign = new Progress("test:other", "wash", "wash", Map.of(), List.of(), Map.of(), null, List.of());
        assertFalse(GraphEngine.canEnter(graph, foreign, "polish"));
    }

    private static Progress state(GraphDefinition graph, int quality) {
        return new Progress(graph.id(), "wash", "wash", Map.of("quality", quality), List.of("wash"), Map.of(), null, List.of("wash"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "[{\"item_input\":1,\"current_nodes\":[\"missing\"]}] | current_nodes[0]",
        "[{\"item_input\":1,\"requirements\":{\"checkpoint_counts\":{\"missing\":{\"min\":1}}}}] | requirements.checkpoint_counts.missing",
        "[{\"item_input\":1,\"requirements\":{\"checkpoints\":{\"mode\":\"partial\",\"nodes\":[\"missing\"]}}}] | requirements.checkpoints.nodes[0]"
    })
    void roleReferencesMustExistInTheGraph(String roles, String suffix) {
        var error = assertThrows(IllegalArgumentException.class, () -> graph(roles));
        assertTrue(error.getMessage().contains("Graph test:roles:"), error.getMessage());
        assertTrue(error.getMessage().contains("nodes[0].workpiece_inputs[0]." + suffix), error.getMessage());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "null | nodes[0].workpiece_inputs",
        "{} | nodes[0].workpiece_inputs",
        "[null] | nodes[0].workpiece_inputs[0]",
        "[{}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":-1}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":1.5}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":2147483648}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":null}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":\"1\"}] | nodes[0].workpiece_inputs[0].item_input",
        "[{\"item_input\":0},{\"item_input\":0}] | nodes[0].workpiece_inputs[1].item_input",
        "[{\"item_input\":1,\"typo\":true}] | nodes[0].workpiece_inputs[0].typo",
        "[{\"item_input\":1,\"current_nodes\":[]}] | nodes[0].workpiece_inputs[0].current_nodes",
        "[{\"item_input\":1,\"current_nodes\":null}] | nodes[0].workpiece_inputs[0].current_nodes",
        "[{\"item_input\":1,\"current_nodes\":[0]}] | nodes[0].workpiece_inputs[0].current_nodes[0]",
        "[{\"item_input\":1,\"current_nodes\":[\"wash\",\"wash\"]}] | nodes[0].workpiece_inputs[0].current_nodes[1]",
        "[{\"item_input\":1,\"requirements\":null}] | nodes[0].workpiece_inputs[0].requirements"
    })
    void malformedRolesAreRejectedWithPrecisePaths(String roles, String path) {
        var error = assertThrows(IllegalArgumentException.class, () -> graph(roles));
        assertTrue(error.getMessage().contains("Graph test:roles:"), error.getMessage());
        assertTrue(error.getMessage().contains(path), error.getMessage());
    }

    @Test void rolesUseLogicalIndicesAndImmutableCurrentNodeAndRequirementFilters() {
        var graph = graph("""
            [{"item_input":2,"current_nodes":["wash"],"requirements":{
               "mark_ranges":{"quality":{"min":2}},"checkpoint_counts":{"polish":{"min":2}}}},
             {"item_input":1}]
            """);
        var node = graph.nodes().get("wash");
        assertEquals(List.of(2, 1), node.workpieceInputs().stream().map(WorkpieceInput::itemInput).toList());
        assertNull(node.workpieceInput(0));
        assertNull(node.workpieceInput(3));
        var input = node.workpieceInput(2);
        var state = new Progress(graph.id(), "wash", "wash", Map.of("quality", 2),
            List.of("polish", "polish", "wash"), Map.of("polish", 0), null, List.of("wash"));
        assertTrue(input.matches(state));
        assertFalse(input.matches(null));
        assertFalse(input.matches(state(graph, 2)), "Recent history and reward counts are not checkpoint counts");
        var wrongCurrent = new Progress(graph.id(), "wash", "polish", state.marks(), state.checkpoints(), Map.of(), null, List.of());
        assertFalse(input.matches(wrongCurrent));
        assertTrue(node.workpieceInput(1).matches(wrongCurrent));
        assertThrows(UnsupportedOperationException.class, () -> node.workpieceInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.currentNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.requirements().markRanges().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.requirements().checkpointCounts().clear());
    }

    @Test void omittedAndEmptyRolesPreserveOrdinaryPrimaryStarts() {
        for (var graph : List.of(graph("[]"), GraphCodec.parse("test:roles", RangeRequirementsTest.graphJson()))) {
            assertTrue(graph.nodes().get("wash").workpieceInputs().isEmpty());
            assertNull(graph.nodes().get("wash").workpieceInput(1));
            assertEquals(List.of("wash"), GraphEngine.allowed(graph, null));
        }
    }

    @Test void primaryInputConditionsReadBeforeRewardsAndCannotBeSatisfiedByTheCurrentOperation() {
        JsonObject json = RangeRequirementsTest.graphJson();
        var polish = json.getAsJsonArray("nodes").get(1).getAsJsonObject();
        polish.add("rewards", JsonParser.parseString("{\"quality\":1}"));
        polish.add("workpiece_inputs", JsonParser.parseString("""
            [{"item_input":0,"current_nodes":["wash"],"requirements":{"mark_ranges":{"quality":{"min":2}}}}]
            """));
        var graph = GraphCodec.parse("test:roles", json);
        var poor = state(graph, 1);
        assertFalse(GraphEngine.allowed(graph, poor).contains("polish"));
        assertTrue(GraphEngine.blockedSteps(graph, poor).get("polish").contains("primary workpiece"));
        assertThrows(IllegalArgumentException.class,
            () -> GraphEngine.complete(graph, poor, "polish", "test:work", () -> fail("No random draw")));
        var result = GraphEngine.complete(graph, state(graph, 2), "polish", "test:work", () -> fail("No random draw"));
        assertEquals(Map.of("quality", 3), result.state().marks());
        assertEquals(List.of("wash", "polish"), result.state().checkpoints());
        assertFalse(GraphEngine.allowed(graph, result.state()).contains("polish"), "Current-node role filter is applied at the next preflight");
    }

    @Test void auxiliaryTraversalRespectsPersistedRandomLocksAndProgressCompatibility() {
        JsonObject json = RangeRequirementsTest.graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("branch_mode", "random");
        json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"wash\",\"to\":\"finish\"}"));
        var graph = GraphCodec.parse("test:roles", json);
        var locked = new Progress(graph.id(), "wash", "wash", Map.of(), List.of(), Map.of(), "polish", List.of());
        assertTrue(GraphEngine.canEnter(graph, locked, "polish"));
        assertFalse(GraphEngine.canEnter(graph, locked, "finish"));
        for (var invalid : List.of(
            new Progress(graph.id(), "wash", "wash", Map.of(), List.of(), Map.of(), null, List.of()),
            new Progress(graph.id(), "polish", "wash", Map.of(), List.of(), Map.of(), "polish", List.of()),
            new Progress(graph.id(), "wash", "missing", Map.of(), List.of(), Map.of(), null, List.of()),
            new Progress(graph.id(), "wash", "polish", Map.of(), List.of(), Map.of(), "finish", List.of())))
            assertFalse(GraphEngine.canEnter(graph, invalid, "polish"));
        assertFalse(GraphEngine.canEnter(null, locked, "polish"));
    }
}
