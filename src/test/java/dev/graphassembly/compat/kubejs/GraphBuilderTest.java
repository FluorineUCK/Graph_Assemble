package dev.graphassembly.compat.kubejs;

import com.google.gson.JsonParser;
import dev.graphassembly.core.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphBuilderTest {
    @Test void nodeOptionsPreserveCampfireConditionBesideRecipe() {
        var options = JsonParser.parseString("{\"start\":true,\"campfire\":\"soul\"}").getAsJsonObject();
        var builder = new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:potato\"}"))
            .node("s", JsonParser.parseString("{\"type\":\"minecraft:campfire_cooking\",\"recipe_id\":\"minecraft:campfire_potato\"}")
                .getAsJsonObject(), options);
        options.addProperty("campfire", "normal");
        var node = GraphCodec.parse("test:campfire", builder.build()).nodes().get("s");
        assertEquals(CampfireKind.SOUL, node.campfire());
        assertFalse(node.recipe().has("campfire"));
    }
    @Test void publishedProgressActionExampleHasWorkingReworkRepairAndFailurePaths() throws Exception {
        var graph=GraphCodec.parse("test:progress_actions",JsonParser.parseString(Files.readString(Path.of("examples/datapack/progress-actions.json"))).getAsJsonObject());
        var start=GraphEngine.complete(graph,null,"start","minecraft:phantom_membrane",()->0).state();
        var inspected=GraphEngine.complete(graph,start,"inspect","minecraft:iron_nugget",()->0).state();
        assertEquals("minecraft:flint",GraphEngine.complete(graph,inspected,"finish","minecraft:iron_nugget",()->0).output().item());
        var reworked=GraphEngine.complete(graph,inspected,"rework","minecraft:iron_nugget",()->0).state();
        assertEquals(Map.of("defect",1),reworked.marks());assertTrue(reworked.checkpoints().isEmpty());
        assertEquals(List.of("inspect"),GraphEngine.allowed(graph,reworked));
        var again=GraphEngine.complete(graph,reworked,"inspect","minecraft:iron_nugget",()->0).state();
        assertEquals("minecraft:diamond",GraphEngine.complete(graph,again,"finish","minecraft:iron_nugget",()->0).output().item());
        var repaired=GraphEngine.complete(graph,inspected,"repair","minecraft:iron_nugget",()->0).state();
        assertTrue(repaired.marks().isEmpty());assertEquals("repair",repaired.current());
        assertEquals(new PendingRoute("finish","inspect"),repaired.pendingRoute());
        assertEquals("minecraft:diamond",GraphEngine.complete(graph,repaired,"finish","minecraft:iron_nugget",()->0).output().item());
    }
    @Test void nodeOptionsRetainActionsAndValidateThroughSharedCodec() {
        var options=JsonParser.parseString("""
            {"start":true,"progress_actions":[{"type":"clear_mark","mark":"defect","count":3},
              {"type":"goto_node","node":"s"}]}
            """).getAsJsonObject();
        var builder=new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .node("s",JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),options);
        options.getAsJsonArray("progress_actions").remove(0);
        var graph=GraphCodec.parse("test:actions",builder.build());
        assertEquals(List.of(new ProgressAction.ClearMark("defect",3),new ProgressAction.GoToNode("s")),graph.nodes().get("s").progressActions());
    }
    @Test void edgeOptionsAreCopiedProtectEndpointsAndUseTheSharedCodec() {
        var options = JsonParser.parseString("""
            {"from":"wrong","to":"wrong","weight":3,"waypoints":[[10,20],[30,40]]}
            """).getAsJsonObject();
        var builder = twoNodeBuilder().edge("a", "b", options);
        options.addProperty("weight", 99);
        options.getAsJsonArray("waypoints").get(0).getAsJsonArray().set(0, JsonParser.parseString("999"));
        var built = builder.build();
        var edgeJson = built.getAsJsonArray("edges").get(0).getAsJsonObject();
        assertEquals("a", edgeJson.get("from").getAsString());
        assertEquals("b", edgeJson.get("to").getAsString());
        assertEquals(3, edgeJson.get("weight").getAsDouble());
        var graph = GraphCodec.parse("test:script", built);
        assertEquals(List.of(new Edge.Waypoint(10, 20), new Edge.Waypoint(30, 40)), graph.edges().getFirst().waypoints());
        edgeJson.getAsJsonArray("waypoints").get(0).getAsJsonArray().set(0, JsonParser.parseString("777"));
        assertEquals(graph, GraphCodec.parse("test:script", builder.build()));
    }

    @Test void edgeOptionsUseCodecDefaultsAndValidationWithoutChangingLegacyOverloads() {
        var automatic = GraphCodec.parse("test:script", twoNodeBuilder().edge("a", "b").build());
        var options = new com.google.gson.JsonObject();
        assertEquals(automatic, GraphCodec.parse("test:script", twoNodeBuilder().edge("a", "b", options).build()));
        assertEquals(new Edge("a", "b", 4), GraphCodec.parse("test:script",
            twoNodeBuilder().weightedEdge("a", "b", 4).build()).edges().getFirst());
        assertEquals(List.of(new Edge("a", "b", 1), new Edge("b", "a", 1)), GraphCodec.parse("test:script",
            twoNodeBuilder().bidirectionalEdge("a", "b").build()).edges());
        options.addProperty("bidirectional", true);
        options.add("waypoints", JsonParser.parseString("[[10,20],[30,40]]"));
        var routed = GraphCodec.parse("test:script", twoNodeBuilder().edge("a", "b", options).build());
        assertEquals(List.of(new Edge.Waypoint(30, 40), new Edge.Waypoint(10, 20)), routed.edges().get(1).waypoints());
        for (String invalid : List.of("{\"weight\":0}", "{\"waypoints\":null}", "{\"waypoints\":[[0,1000001]]}")) {
            var invalidOptions = JsonParser.parseString(invalid).getAsJsonObject();
            var error = assertThrows(IllegalArgumentException.class,
                () -> twoNodeBuilder().edge("a", "b", invalidOptions).build());
            assertTrue(error.getMessage().contains("Graph graph_assembly:script_validation:"), error.getMessage());
            assertTrue(error.getMessage().contains("edges[0]."), error.getMessage());
        }
    }

    private static GraphBuilder twoNodeBuilder() {
        return new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .node("a", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),
                JsonParser.parseString("{\"start\":true}").getAsJsonObject())
            .node("b", JsonParser.parseString("{\"type\":\"create:cutting\"}").getAsJsonObject(),
                JsonParser.parseString("{\"finish\":true}").getAsJsonObject());
    }

    @Test void retainedKubejsExamplesUseCurrentCallsAndEquivalentBuilderGraphs() throws Exception {
        var basicScript = Files.readString(Path.of("examples", "kubejs", "graph_assembly.js"));
        var itemScript = Files.readString(Path.of("examples", "kubejs", "item_first.js"));
        assertTrue(basicScript.contains("requirements:"));
        assertTrue(basicScript.contains("marks: {}"));
        assertFalse(itemScript.contains("formatVersion("));
        assertTrue(itemScript.contains("requirements:"));

        var basic = new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .node("start", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),
                JsonParser.parseString("{\"start\":true}").getAsJsonObject())
            .node("finish", JsonParser.parseString("{\"type\":\"create:cutting\",\"processing_time\":40}").getAsJsonObject(),
                JsonParser.parseString("{\"finish\":true}").getAsJsonObject())
            .edge("start", "finish")
            .output(JsonParser.parseString("{\"requirements\":{\"marks\":{},\"checkpoints\":[\"start\",\"finish\"]},\"results\":[{\"item\":\"minecraft:gold_ingot\"}]}").getAsJsonObject());
        var basicGraph = GraphCodec.parse("example:script_graph", basic.build());
        assertEquals(2, basicGraph.outputs().getFirst().requirements().checkpoints().nodes().size());
        assertFalse(basicGraph.outputs().getFirst().requirements().matches(Map.of("extra", 1), List.of("start", "finish")));
        assertEquals("minecraft:gold_ingot", basicGraph.outputs().getFirst().results().getFirst().item());

        var item = new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"))
            .node("start", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),
                JsonParser.parseString("{\"start\":true,\"rewards\":{\"precision\":2}}").getAsJsonObject())
            .node("finish", JsonParser.parseString("{\"type\":\"create:cutting\",\"processing_time\":40}").getAsJsonObject(),
                JsonParser.parseString("{\"finish\":true}").getAsJsonObject())
            .edge("start", "finish")
            .output(JsonParser.parseString("{\"requirements\":{\"mode\":\"partial\",\"marks\":{\"precision\":1},\"checkpoints\":[\"start\",\"finish\"]},\"results\":[{\"item\":\"minecraft:diamond\"}]}").getAsJsonObject());
        var itemGraph = GraphCodec.parse("example:item_first", item.build());
        assertTrue(itemGraph.outputs().getFirst().requirements().matches(Map.of("precision", 2), List.of("start", "finish")));
        assertEquals("minecraft:diamond", itemGraph.outputs().getFirst().results().getFirst().item());
    }
    @Test void constructorBuildsCurrentFormatAndDefensivelyCopiesOutput() {
        var builder = new GraphBuilder("minecraft:iron_nugget")
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .node("s", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),
                JsonParser.parseString("{\"start\":true,\"finish\":true,\"rewards\":{\"x\":2}}").getAsJsonObject())
            .output(JsonParser.parseString("{\"results\":[{\"item\":\"minecraft:diamond\"}]}").getAsJsonObject());
        var built = builder.build();
        assertFalse(built.has("format_version"));
        assertTrue(GraphEngine.complete(GraphCodec.parse("test:b", built), null, "s", "minecraft:iron_ingot", () -> 0).settled());
        built.addProperty("transitional_item", "test:mutated");
        assertEquals("minecraft:iron_nugget", builder.build().get("transitional_item").getAsString());
        assertFalse(builder.build().has("format_version"));
    }
    @Test void builderAndDatapackUseIdenticalDefaultsAndSettlement() {
        var recipe = JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject();
        var options = JsonParser.parseString("{\"start\":true,\"finish\":true}").getAsJsonObject();
        var rule = JsonParser.parseString("{\"results\":[{\"item\":\"minecraft:gold_ingot\"}]}").getAsJsonObject();
        var built = new GraphBuilder("minecraft:iron_nugget").ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .node("press", recipe, options).output(rule).build();
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"press","recipe":{"type":"create:pressing"},"start":true,"finish":true}],
             "edges":[],"outputs":[{"results":[{"item":"minecraft:gold_ingot"}]}]}
            """).getAsJsonObject();
        var a = GraphCodec.parse("test:graph", built); var b = GraphCodec.parse("test:graph", json);
        assertEquals(a, b);
        assertEquals(GraphEngine.complete(a, null, "press", "minecraft:iron_ingot", () -> 0),
            GraphEngine.complete(b, null, "press", "minecraft:iron_ingot", () -> 0));
    }
}
