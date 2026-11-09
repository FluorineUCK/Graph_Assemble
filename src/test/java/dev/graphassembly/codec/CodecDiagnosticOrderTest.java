package dev.graphassembly.codec;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Characterizes delayed action validation and the shared-validator diagnostics. */
class CodecDiagnosticOrderTest {
    private static JsonObject graph(String actions) {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}}],
             "edges":[]}
            """).getAsJsonObject();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject()
            .add("progress_actions", JsonParser.parseString(actions));
        return json;
    }

    private static void graphError(JsonObject json, String message) {
        assertEquals("Graph test:priority: " + message,
            assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:priority", json)).getMessage());
    }

    @Test void duplicateRoutesRemainDelayedBehindAllStructuralParsingAndGlobalReferences() {
        var json = graph("""
            [{"type":"goto_node","node":"s"},{"type":"goto_node","node":"missing"}]
            """);
        json.getAsJsonArray("nodes").add(JsonParser.parseString("""
            {"id":"later","recipe":{"type":null}}
            """));
        graphError(json, "nodes[1].recipe.type: must be a nonempty string");
        json.getAsJsonArray("nodes").remove(1);
        json.add("edges", JsonParser.parseString("[{\"from\":\"s\",\"to\":\"missing\"}]"));
        graphError(json, "edges[0].to: unknown node: missing");
        json.add("edges", JsonParser.parseString("[]"));
        json.add("outputs", JsonParser.parseString("[{\"start\":\"absent\",\"results\":[{\"item\":\"test:ok\"}]}]"));
        graphError(json, "graph.outputs[0].start: unknown node: absent");
        json.remove("outputs");
        graphError(json, "nodes[0].progress_actions[1]: at most one route action per node");
    }

    @Test void actionFieldsParseBeforeDelayedReferencesAndRetainOriginalArrayIndexes() {
        var json = graph("""
            [{"type":"clear_checkpoint","node":"missing"},
             {"type":"clear_mark","mark":"x","extra":true,"count":-1}]
            """);
        graphError(json, "nodes[0].progress_actions[1].extra: unknown field");
        var action = json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("progress_actions").get(1).getAsJsonObject();
        action.remove("extra");
        graphError(json, "nodes[0].progress_actions[1].count: must be at least 0");
        action.addProperty("count", 0);
        graphError(json, "nodes[0].progress_actions[0].node: unknown node: missing");
    }

    @Test void delayedReferencesKeepActionOrderAndSecondRoutePrecedesItsTargetValidation() {
        graphError(graph("""
            [{"type":"pop_checkpoint"},{"type":"goto_node","node":"missing"},
             {"type":"clear_checkpoint","node":"absent"}]
            """), "nodes[0].progress_actions[1]: unknown target: missing");
        graphError(graph("""
            [{"type":"clear_mark","mark":"x"},{"type":"enter_edge","from":"absent","to":"s"},
             {"type":"goto_node","node":"missing"}]
            """), "nodes[0].progress_actions[1]: unknown directed edge: absent -> s");
        graphError(graph("""
            [{"type":"pop_checkpoint"},{"type":"goto_node","node":"s"},
             {"type":"enter_edge","from":"absent","to":"missing"}]
            """), "nodes[0].progress_actions[2]: at most one route action per node");
    }

    @Test void perNodeActionsPrecedeLocalReferencesAndNextNodeActions() {
        var json = graph("[{\"type\":\"clear_checkpoint\",\"node\":\"missing\"}]");
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", JsonParser.parseString("""
            [{"start":"absent","results":[{"item":"test:ok"}]}]
            """));
        json.getAsJsonArray("nodes").add(JsonParser.parseString("""
            {"id":"later","recipe":{"type":"create:pressing"},
             "progress_actions":[{"type":"goto_node","node":"elsewhere"}]}
            """));
        graphError(json, "nodes[0].progress_actions[0].node: unknown node: missing");
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("progress_actions");
        graphError(json, "nodes[0].outputs[0].start: unknown node: absent");
    }

    @Test void progressCountsAndNodesKeepLegacyPathsAndPriority() {
        var json = JsonParser.parseString("""
            {"graph_id":"test:g","entry":"s","current":"s","marks":{"x":-1},
             "checkpoints":[null],"mark_grant_counts":{"y":-1},"history":[null]}
            """).getAsJsonObject();
        progressError(json, "progress.counts.x: must be at least 0");
        json.add("marks", new JsonObject());
        progressError(json, "progress.nodes: must be a nonempty string");
        json.add("checkpoints", JsonParser.parseString("[]"));
        progressError(json, "progress.counts.y: must be at least 0");
        json.add("mark_grant_counts", new JsonObject());
        progressError(json, "progress.nodes: must be a nonempty string");
    }

    private static void progressError(JsonObject json, String message) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> ProgressCodec.fromJson(json)).getMessage());
    }
}
