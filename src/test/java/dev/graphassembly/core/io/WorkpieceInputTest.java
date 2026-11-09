package dev.graphassembly.core.io;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.condition.RangeRequirementsTest;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteResolver;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class WorkpieceInputTest {
    static GraphDefinition<OperationSpec> graph(String roles) {
        JsonObject json = RangeRequirementsTest.graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject()
            .add("workpiece_inputs", JsonParser.parseString(roles));
        return GraphCodec.parse("test:roles", json);
    }

    @Test void declaringPrimaryInputRequiresExistingProgressEvenAtStart() {
        var graph = graph("[{\"item_input\":0}]");
        assertTrue(GraphPipeline.allowed(graph, null).isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> GraphPipeline.resolve(GraphPipeline.plan(graph, null, "wash", "test:raw"), () -> fail("No draw")));
    }

    @Test void auxiliaryTraversalIgnoresPrimaryConditionsAndHypotheticalRewardOverflow() {
        JsonObject json = RangeRequirementsTest.graphJson();
        var node = json.getAsJsonArray("nodes").get(1).getAsJsonObject();
        node.add("mark_amounts", JsonParser.parseString("{\"quality\":1}"));
        node.add("workpiece_inputs", JsonParser.parseString("""
            [{"item_input":0,"requirements":{"mark_ranges":{"quality":{"min":2}}}}]
            """));
        var graph = GraphCodec.parse("test:roles", json);
        for (int quality : List.of(0, Integer.MAX_VALUE)) {
            var state = state(graph, quality);
            assertFalse(GraphPipeline.allowed(graph, state).contains("polish"));
            assertTrue(RouteResolver.canEnter(graph, state, "polish"));
            assertFalse(RouteResolver.canEnter(graph, state, "finish"));
            assertFalse(RouteResolver.canEnter(graph, state, "missing"));
        }
        assertTrue(RouteResolver.canEnter(graph, null, "wash"));
        assertFalse(RouteResolver.canEnter(graph, null, "polish"));
        var foreign = new Progress("test:other", "wash", "wash", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState(null, null), List.of());
        assertFalse(RouteResolver.canEnter(graph, foreign, "polish"));
    }

    private static Progress state(GraphDefinition<OperationSpec> graph, int quality) {
        return new Progress(graph.id(), "wash", "wash", new MarkAccounting.State(Map.of("quality", quality), Map.of()), List.of("wash"), new RouteState(null, null), List.of("wash"));
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
        assertEquals(List.of(2, 1), node.inputs().workpieceInputs().stream().map(WorkpieceInput::itemInput).toList());
        assertNull(node.inputs().workpieceInput(0));
        assertNull(node.inputs().workpieceInput(3));
        var input = node.inputs().workpieceInput(2);
        var state = new Progress(graph.id(), "wash", "wash", new MarkAccounting.State(Map.of("quality", 2), Map.of("polish", 0)), List.of("polish", "polish", "wash"), new RouteState(null, null), List.of("wash"));
        assertTrue(new InputEligibility(state).matches(input));
        assertFalse(new InputEligibility(null).matches(input));
        assertFalse(new InputEligibility(state(graph, 2)).matches(input), "Recent history and reward counts are not checkpoint counts");
        var wrongCurrent = new Progress(graph.id(), "wash", "polish", new MarkAccounting.State(state.marks().values(), Map.of()), state.checkpoints(), new RouteState(null, null), List.of());
        assertFalse(new InputEligibility(wrongCurrent).matches(input));
        assertTrue(new InputEligibility(wrongCurrent).matches(node.inputs().workpieceInput(1)));
        assertThrows(UnsupportedOperationException.class, () -> node.inputs().workpieceInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.currentNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.requirements().marks().ranges().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.requirements().checkpoints().counts().clear());
    }

    @Test void omittedAndEmptyRolesPreserveOrdinaryPrimaryStarts() {
        for (var graph : List.of(graph("[]"), GraphCodec.parse("test:roles", RangeRequirementsTest.graphJson()))) {
            assertTrue(graph.nodes().get("wash").inputs().workpieceInputs().isEmpty());
            assertNull(graph.nodes().get("wash").inputs().workpieceInput(1));
            assertEquals(List.of("wash"), GraphPipeline.allowed(graph, null));
        }
    }

    @Test void primaryInputConditionsReadBeforeRewardsAndCannotBeSatisfiedByTheCurrentOperation() {
        JsonObject json = RangeRequirementsTest.graphJson();
        var polish = json.getAsJsonArray("nodes").get(1).getAsJsonObject();
        polish.add("mark_amounts", JsonParser.parseString("{\"quality\":1}"));
        polish.add("workpiece_inputs", JsonParser.parseString("""
            [{"item_input":0,"current_nodes":["wash"],"requirements":{"mark_ranges":{"quality":{"min":2}}}}]
            """));
        var graph = GraphCodec.parse("test:roles", json);
        var poor = state(graph, 1);
        assertFalse(GraphPipeline.allowed(graph, poor).contains("polish"));
        assertTrue(GraphPipeline.preflight(graph, poor).blocked().get("polish").message().contains("primary workpiece"));
        assertThrows(IllegalArgumentException.class,
            () -> GraphPipeline.resolve(GraphPipeline.plan(graph, poor, "polish", "test:work"), () -> fail("No random draw")));
        var result = GraphPipeline.resolve(GraphPipeline.plan(graph, state(graph, 2), "polish", "test:work"), () -> fail("No random draw"));
        assertEquals(Map.of("quality", 3), ((TransitionResult.Continued) result).progress().marks().values());
        assertEquals(List.of("wash", "polish"), ((TransitionResult.Continued) result).progress().checkpoints());
        assertFalse(GraphPipeline.allowed(graph, ((TransitionResult.Continued) result).progress()).contains("polish"), "Current-node role filter is applied at the next preflight");
    }

    @Test void auxiliaryTraversalRespectsPersistedRandomLocksAndProgressCompatibility() {
        JsonObject json = RangeRequirementsTest.graphJson();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("branch_mode", "random");
        json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"wash\",\"to\":\"finish\"}"));
        var graph = GraphCodec.parse("test:roles", json);
        var locked = new Progress(graph.id(), "wash", "wash", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState("polish", null), List.of());
        assertTrue(RouteResolver.canEnter(graph, locked, "polish"));
        assertFalse(RouteResolver.canEnter(graph, locked, "finish"));
        for (var invalid : List.of(
            new Progress(graph.id(), "wash", "wash", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState(null, null), List.of()),
            new Progress(graph.id(), "polish", "wash", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState("polish", null), List.of()),
            new Progress(graph.id(), "wash", "missing", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState(null, null), List.of()),
            new Progress(graph.id(), "wash", "polish", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState("finish", null), List.of())))
            assertFalse(RouteResolver.canEnter(graph, invalid, "polish"));
        assertFalse(RouteResolver.canEnter(null, locked, "polish"));
    }
}
