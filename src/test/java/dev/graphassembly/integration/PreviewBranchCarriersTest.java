package dev.graphassembly.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphCodec;
import dev.graphassembly.core.GraphDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewBranchCarriersTest {
    private static JsonObject fixture() {
        return JsonParser.parseString("""
            {"ingredient":{"tag":"test:raw_filter"},"transitional_item":"test:filter",
             "nodes":[
               {"id":"f0","start":true,"recipe":{"type":"create:pressing"}},
               {"id":"f1","recipe":{"type":"create:cutting"}},
               {"id":"i0","start":true,"ingredient":[{"item":"test:alloy"},{"item":"test:alternative"}],
                "transitional_item":"test:insert","recipe":{"type":"create:mixing","ingredients":[{"tag":"test:infested"}]}},
               {"id":"i1","recipe":{"type":"create:pressing"}},
               {"id":"join","recipe":{"type":"create:compacting","ingredients":[{"item":"test:insert"}]},
                "workpiece_inputs":[{"item_input":0,"current_nodes":["f1"]}]}],
             "edges":[{"from":"f0","to":"f1"},{"from":"i0","to":"i1"},
                      {"from":"i1","to":"join"},{"from":"f1","to":"join"}]}
            """).getAsJsonObject();
    }
    private static GraphDefinition graph(JsonObject json) { return GraphCodec.parse("test:carriers", json); }
    private static JsonObject view(GraphDefinition graph, String node) {
        return PreviewRecipeData.forNode(graph, graph.nodes().get(node));
    }
    private static String input(JsonObject view) { return view.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString(); }
    private static String output(JsonObject view) { return view.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString(); }

    @Test void rawStartTagsAndAlternativesAreNotReplacedByTheirFutureCarrier() {
        var json = fixture(); var before = json.deepCopy(); var graph = graph(json);
        var filter = view(graph, "f0"); var insert = view(graph, "i0");
        assertEquals(json.get("ingredient"), filter.getAsJsonArray("ingredients").get(0));
        assertEquals(json.getAsJsonArray("nodes").get(2).getAsJsonObject().get("ingredient"), insert.getAsJsonArray("ingredients").get(0));
        assertEquals("test:filter", output(filter)); assertEquals("test:insert", output(insert));
        assertEquals(2, insert.getAsJsonArray("ingredients").size(), "Raw primary + one auxiliary; no extra transition input");
        insert.getAsJsonArray("ingredients").get(0).getAsJsonArray().get(0).getAsJsonObject().addProperty("item", "test:mutated");
        assertEquals(before, json);
        assertEquals("test:alloy", view(graph, "i0").getAsJsonArray("ingredients").get(0).getAsJsonArray().get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test void downstreamNodesInheritTheirBranchAndJoinUsesOnlyDeclaredPrimaryPredecessor() {
        var graph = graph(fixture());
        assertEquals("test:filter", input(view(graph, "f1")));
        assertEquals("test:insert", input(view(graph, "i1")));
        assertEquals("test:insert", output(view(graph, "i1")));
        assertEquals(List.of("test:filter"), PreviewRecipeData.carriedInputs(graph, graph.nodes().get("join")));
        var join = view(graph, "join");
        assertEquals("test:filter", input(join)); assertEquals("test:filter", output(join));
        assertEquals("test:insert", join.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
    }

    @Test void aNodeCarrierOverrideChangesItsOutputNotItsInputAndContinuesThroughCycles() {
        var json = fixture();
        json.getAsJsonArray("nodes").get(3).getAsJsonObject().addProperty("transitional_item", "test:polished_insert");
        json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"i1\",\"to\":\"i1\"}"));
        var graph = graph(json); var preview = view(graph, "i1");
        assertEquals(List.of("test:insert", "test:polished_insert"), PreviewRecipeData.carriedInputs(graph, graph.nodes().get("i1")));
        assertEquals("test:insert", input(preview)); assertEquals("test:polished_insert", output(preview));
        assertEquals(1, preview.getAsJsonArray("ingredients").size(), "Alternative states are not extra consumed inputs");
    }

    @Test void strictSettlementCannotSendACarrierToDownstreamPreview() {
        var json = fixture(); var first = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        first.addProperty("finish", true); first.addProperty("finish_mode", "strict");
        var graph = graph(json);
        assertTrue(PreviewRecipeData.carriedInputs(graph, graph.nodes().get("f1")).isEmpty());
    }

    @Test void componentSensitiveEntranceIngredientIsPreservedWithoutAliasing() {
        var json = fixture();
        var ingredient = JsonParser.parseString("""
            {"type":"neoforge:components","items":["test:filter"],"components":{"minecraft:custom_name":"raw"}}
            """);
        json.add("ingredient", ingredient);
        var preview = view(graph(json), "f0");
        assertEquals(ingredient, preview.getAsJsonArray("ingredients").get(0));
        assertEquals("test:filter", output(preview), "Raw material may intentionally have the same item ID as its carrier");
    }
}
