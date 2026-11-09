package dev.graphassembly.core.io;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputEligibilityTest {
    @Test void candidateAndAuxiliaryCountsStayWithTheCurrentInputSnapshot() {
        var graph = GraphCodec.parse("test:counts", JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
               {"id":"a","recipe":{"type":"create:cutting"},"workpiece_inputs":[
                 {"item_input":0,"requirements":{"checkpoint_counts":{"s":{"min":2,"max":2}}}},
                 {"item_input":1,"requirements":{"checkpoint_counts":{"s":{"min":1}}}}]},
               {"id":"b","recipe":{"type":"create:cutting"},"workpiece_inputs":[
                 {"item_input":0,"requirements":{"checkpoint_counts":{"s":{"min":1,"max":1}}}},
                 {"item_input":1,"requirements":{"checkpoint_counts":{"s":{"min":2,"max":2}}}}]}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """).getAsJsonObject());
        for (int count : List.of(2, 1, 0, 2)) {
            var state = new Progress(graph.id(), "s", "s", new MarkAccounting.State(Map.of(), Map.of()),
                java.util.Collections.nCopies(count, "s"), new RouteState(null, null), List.of("s"));
            var preflight = GraphPipeline.preflight(graph, state);
            assertEquals(count == 2 ? List.of("a") : count == 1 ? List.of("b") : List.of(), preflight.allowed());
            var auxiliary = InputEligibility.auxiliaries(graph, state, preflight);
            assertEquals(count == 2 ? java.util.Set.of("a", "b") : count == 1 ? java.util.Set.of("a") : java.util.Set.of(),
                auxiliary.keySet());
            auxiliary.values().forEach(roles -> assertEquals(List.of(1), roles.stream().map(WorkpieceInput::itemInput).toList()));
            assertEquals(java.util.Collections.nCopies(count, "s"), state.checkpoints());
        }
    }

    private static GraphDefinition<OperationSpec> graph() throws Exception {
        return GraphCodec.parse("test:join", JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json"))).getAsJsonObject());
    }
    private static Progress ready(GraphDefinition<OperationSpec> graph, String branch) {
        var entry = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, branch + "_start", "test:raw"), () -> 0)).progress();
        return ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, entry, branch + "_ready", "test:carrier"), () -> 0)).progress();
    }
    @Test void bothBranchesReferenceTheSameJoinWithoutPromotingAuxiliaryToPrimary() throws Exception {
        var graph = graph();
        var body = ready(graph, "body");
        var insert = ready(graph, "insert");
        assertEquals(List.of("assemble"), GraphPipeline.allowed(graph, body));
        assertTrue(GraphPipeline.allowed(graph, insert).isEmpty());
        assertEquals(PreflightResult.BlockedReason.PRIMARY_CONDITIONS,
            GraphPipeline.preflight(graph, insert).blocked().get("assemble").reason());
        var auxiliary = InputEligibility.auxiliaries(graph, insert, GraphPipeline.preflight(graph, insert));
        assertEquals(List.of(1), auxiliary.get("assemble").stream().map(WorkpieceInput::itemInput).toList());
        assertTrue(InputEligibility.auxiliaries(graph, body, GraphPipeline.preflight(graph, body)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(graph, insert, "assemble", "test:carrier"), () -> 0));
    }
    @Test void unreadyOrIncompatibleProgressIsNotPresentedAsReadyAuxiliary() throws Exception {
        var graph = graph();
        var correct = ready(graph, "insert");
        var unfinished = new Progress(graph.id(), "insert_start", "insert_ready", new MarkAccounting.State(Map.of(), Map.of()), List.of("insert_start"), new RouteState(null, null), List.of());
        var foreign = new Progress("test:other", correct.entry(), correct.current(), new MarkAccounting.State(correct.marks().values(), correct.marks().grantCounts()), correct.checkpoints(), new RouteState(null, null), correct.recentHistory());
        for (var state : List.of(unfinished, foreign))
            assertTrue(InputEligibility.auxiliaries(graph, state, GraphPipeline.preflight(graph, state)).isEmpty());
        assertTrue(InputEligibility.auxiliaries(graph, null, GraphPipeline.preflight(graph, null)).isEmpty());
    }
    @Test void matchingRoleStillRequiresAnAllowedGraphRoute() throws Exception {
        var json = JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json"))).getAsJsonObject();
        json.getAsJsonArray("edges").remove(3);
        var graph = GraphCodec.parse("test:join", json);
        var insert = ready(graph, "insert");
        assertTrue(new InputEligibility(insert).matches(graph.nodes().get("assemble").inputs().workpieceInput(1)));
        assertTrue(InputEligibility.auxiliaries(graph, insert, GraphPipeline.preflight(graph, insert)).isEmpty());
    }
}
