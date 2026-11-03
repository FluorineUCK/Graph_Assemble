package dev.graphassembly.core;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkpieceParticipationTest {
    private static GraphDefinition graph() throws Exception {
        return GraphCodec.parse("test:join", JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json"))).getAsJsonObject());
    }
    private static Progress ready(GraphDefinition graph, String branch) {
        var entry = GraphEngine.complete(graph, null, branch + "_start", "test:raw", () -> 0).state();
        return GraphEngine.complete(graph, entry, branch + "_ready", "test:carrier", () -> 0).state();
    }
    @Test void bothBranchesReferenceTheSameJoinWithoutPromotingAuxiliaryToPrimary() throws Exception {
        var graph = graph();
        var body = ready(graph, "body");
        var insert = ready(graph, "insert");
        assertEquals(List.of("assemble"), GraphEngine.allowed(graph, body));
        assertTrue(GraphEngine.allowed(graph, insert).isEmpty());
        assertEquals(GraphEngine.BlockedReason.PRIMARY_CONDITIONS,
            GraphEngine.preflight(graph, insert).blockedReasons().get("assemble"));
        var auxiliary = WorkpieceParticipation.auxiliaries(graph, insert, GraphEngine.preflight(graph, insert));
        assertEquals(List.of(1), auxiliary.get("assemble").stream().map(WorkpieceInput::itemInput).toList());
        assertTrue(WorkpieceParticipation.auxiliaries(graph, body, GraphEngine.preflight(graph, body)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, insert, "assemble", "test:carrier", () -> 0));
    }
    @Test void unreadyOrIncompatibleProgressIsNotPresentedAsReadyAuxiliary() throws Exception {
        var graph = graph();
        var correct = ready(graph, "insert");
        var unfinished = new Progress(graph.id(), "insert_start", "insert_ready", Map.of(),
            List.of("insert_start"), Map.of(), null, List.of());
        var foreign = new Progress("test:other", correct.entry(), correct.current(), correct.marks(),
            correct.checkpoints(), correct.rewardCounts(), null, correct.history());
        for (var state : List.of(unfinished, foreign))
            assertTrue(WorkpieceParticipation.auxiliaries(graph, state, GraphEngine.preflight(graph, state)).isEmpty());
        assertTrue(WorkpieceParticipation.auxiliaries(graph, null, GraphEngine.preflight(graph, null)).isEmpty());
    }
    @Test void matchingRoleStillRequiresAnAllowedGraphRoute() throws Exception {
        var json = JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json"))).getAsJsonObject();
        json.getAsJsonArray("edges").remove(3);
        var graph = GraphCodec.parse("test:join", json);
        var insert = ready(graph, "insert");
        assertTrue(graph.nodes().get("assemble").workpieceInput(1).matches(insert));
        assertTrue(WorkpieceParticipation.auxiliaries(graph, insert, GraphEngine.preflight(graph, insert)).isEmpty());
    }
}
