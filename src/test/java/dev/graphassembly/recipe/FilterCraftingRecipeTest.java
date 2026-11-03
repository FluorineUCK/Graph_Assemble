package dev.graphassembly.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.graphassembly.core.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FilterCraftingRecipeTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
    }
    private static GraphDefinition graph() throws Exception {
        return GraphCodec.parse("graph_assembly:graph_filter", json("data/graph_assembly/recipe/graph_filter.json"));
    }

    @Test void bundledGraphHasExactlyFiveOperationsAndFourDisplayRoutedEdges() throws Exception {
        var graph = graph();
        assertEquals(Set.of("body_start", "body_ready", "insert_start", "insert_ready", "assemble"), graph.nodes().keySet());
        assertEquals(4, graph.edges().size());
        assertEquals(List.of("body_start", "insert_start"), GraphEngine.allowed(graph, null));
        assertEquals(List.of(4, 6, 9, 1), graph.edges().stream().map(e -> e.waypoints().size()).toList());
        assertTrue(graph.nodes().values().stream().allMatch(n -> n.position().size() == 2));
    }

    @Test void sevenInfestedVariantsAndSuppliedIntermediateTexturesAreBundled() throws Exception {
        var tag = json("data/graph_assembly/tags/item/filter_infested_blocks.json");
        var values = new java.util.HashSet<String>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        assertEquals(Set.of("minecraft:infested_stone", "minecraft:infested_cobblestone", "minecraft:infested_deepslate",
            "minecraft:infested_stone_bricks", "minecraft:infested_mossy_stone_bricks", "minecraft:infested_cracked_stone_bricks",
            "minecraft:infested_chiseled_stone_bricks"), values);
        for (var pair : List.of(List.of("incomplete_graph_filter", "processed_filter"), List.of("infested_filter_insert", "processed_sus"), List.of("graph_filter", "graph_filter"))) {
            var model = json("assets/graph_assembly/models/item/" + pair.get(0) + ".json");
            if (!model.has("textures")) model = json("assets/graph_assembly/models/item/" + model.get("parent").getAsString().substring("graph_assembly:item/".length()) + ".json");
            assertEquals("graph_assembly:item/" + pair.get(1), model.getAsJsonObject("textures").get("layer0").getAsString());
            assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/graph_assembly/textures/item/" + pair.get(1) + ".png")));
            for (String locale : List.of("en_us", "zh_cn"))
                assertTrue(json("assets/graph_assembly/lang/" + locale + ".json").has("item.graph_assembly." + pair.get(0)));
        }
    }

    @Test void expandedNativeLayoutsHaveSpaceForModelsSlotsAndHoverTargets() throws Exception {
        var nodes = new java.util.ArrayList<>(graph().nodes().values());
        // Create 6.0.10 CreateJEI: mixing/packing 177x103; sequence steps
        // use the 49x84 envelope (25px machine width + 12px padding per side).
        for (double zoom : new double[]{1.10, 1.25, 2, 3}) {
            for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
                var a = nodes.get(i); var b = nodes.get(j);
                boolean basinA = Set.of("create:mixing", "create:compacting").contains(a.recipe().get("type").getAsString());
                boolean basinB = Set.of("create:mixing", "create:compacting").contains(b.recipe().get("type").getAsString());
                double halfWidth = ((basinA ? 177 : 49) + (basinB ? 177 : 49)) / 2.0;
                double halfHeight = ((basinA ? 103 : 84) + (basinB ? 103 : 84)) / 2.0;
                double dx = Math.abs(a.position().get(0) - b.position().get(0));
                double dy = Math.abs(a.position().get(1) - b.position().get(1));
                assertTrue((dx - halfWidth) * zoom >= 12 || (dy - halfHeight) * zoom >= 12,
                    () -> a.id() + " / " + b.id() + " overlap or lack a 12px gutter at zoom " + zoom);
            }
        }
    }

    @Test void authoredContourDoesNotCrossUnrelatedNativePreviews() throws Exception {
        var graph = graph();
        var projected = new java.util.LinkedHashMap<String, dev.graphassembly.client.view.GraphProjection.Projected>();
        dev.graphassembly.client.view.GraphProjection.layout(graph, false).forEach((id, p) ->
            projected.put(id, new dev.graphassembly.client.view.GraphProjection.Projected(p.x(), p.y(), 1)));
        for (var path : dev.graphassembly.client.view.GraphEdges.layout(graph.edges(), projected, false)) {
            for (var node : graph.nodes().values()) {
                if (node.id().equals(path.edge().from()) || node.id().equals(path.edge().to())) continue;
                boolean basin = Set.of("create:mixing", "create:compacting").contains(node.recipe().get("type").getAsString());
                double halfWidth = (basin ? 177 : 49) / 2.0 + 12;
                double halfHeight = (basin ? 103 : 84) / 2.0 + 12;
                var center = projected.get(node.id());
                for (int i = 1; i < path.points().size(); i++) {
                    var a = path.points().get(i - 1); var b = path.points().get(i);
                    assertNull(dev.graphassembly.client.view.GraphProjection.clipSegment(a.x(), a.y(), b.x(), b.y(),
                        center.x() - halfWidth, center.y() - halfHeight, center.x() + halfWidth, center.y() + halfHeight),
                        path.edge().from() + " -> " + path.edge().to() + " crosses " + node.id());
                }
            }
        }
    }

    @Test void entrancesConsumeExactlyOneFilterOneAlloyAndTwoTaggedAuxiliaries() throws Exception {
        var graph = graph();
        assertEquals("create:attribute_filter", graph.ingredient().getAsJsonObject().get("item").getAsString());
        assertEquals("graph_assembly:incomplete_graph_filter", graph.transitionalItem());
        assertEquals("graph_assembly:incomplete_graph_filter", graph.nodes().get("body_start").transitionalItem());
        assertEquals("create:andesite_alloy", graph.nodes().get("insert_start").ingredient().getAsJsonObject().get("item").getAsString());
        assertEquals("graph_assembly:infested_filter_insert", graph.nodes().get("insert_start").transitionalItem());
        var auxiliaries = graph.nodes().get("insert_start").recipe().getAsJsonArray("ingredients");
        assertEquals(2, auxiliaries.size());
        auxiliaries.forEach(i -> assertEquals("graph_assembly:filter_infested_blocks", i.getAsJsonObject().get("tag").getAsString()));
        assertEquals("none", graph.nodes().get("insert_start").recipe().get("heat_requirement").getAsString());
        assertEquals(List.of("create:pressing", "create:cutting", "create:mixing", "create:pressing", "create:compacting"),
            graph.nodes().values().stream().map(n -> n.recipe().get("type").getAsString()).toList());
    }

    private static Progress route(GraphDefinition graph, String start, String ready, String raw, String carrier) {
        var first = GraphEngine.complete(graph, null, start, raw, () -> { throw new AssertionError("Entrance cannot draw RNG"); });
        assertEquals(carrier, first.item());
        assertEquals(List.of(ready), GraphEngine.allowed(graph, first.state()));
        return GraphEngine.complete(graph, first.state(), ready, carrier, () -> { throw new AssertionError("Readiness cannot draw RNG"); }).state();
    }

    @Test void finalRolesRequireTheirOwnReadyNodeAndExactOrderedCheckpointHistory() throws Exception {
        var graph = graph();
        var body = route(graph, "body_start", "body_ready", "create:attribute_filter", "graph_assembly:incomplete_graph_filter");
        var insert = route(graph, "insert_start", "insert_ready", "create:andesite_alloy", "graph_assembly:infested_filter_insert");
        var finish = graph.nodes().get("assemble");
        assertTrue(graph.nodes().get("body_ready").checkpoint());
        assertTrue(graph.nodes().get("insert_ready").checkpoint());
        assertEquals(List.of("body_ready"), finish.workpieceInput(0).currentNodes());
        assertEquals(List.of("insert_ready"), finish.workpieceInput(1).currentNodes());
        assertTrue(finish.workpieceInput(0).matches(body));
        assertTrue(finish.workpieceInput(1).matches(insert));
        assertFalse(finish.workpieceInput(0).matches(insert));
        assertFalse(finish.workpieceInput(1).matches(body));
        assertFalse(finish.workpieceInput(1).matches(null));
        var reversed = new Progress(insert.graphId(), insert.entry(), insert.current(), Map.of(), List.of("insert_ready", "insert_start"), Map.of(), null, List.of());
        assertFalse(finish.workpieceInput(1).matches(reversed));
        var missing = new Progress(body.graphId(), body.entry(), body.current(), Map.of(), List.of("body_ready"), Map.of(), null, List.of());
        assertFalse(finish.workpieceInput(0).matches(missing));
        assertEquals(List.of("assemble"), GraphEngine.allowed(graph, body));
        assertTrue(GraphEngine.allowed(graph, insert).isEmpty(), "Auxiliary may reach join structurally but must never become final primary");
        assertTrue(GraphEngine.canEnter(graph, insert, "assemble"));
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, body, "insert_ready", "graph_assembly:incomplete_graph_filter", () -> 0));
    }

    @Test void successIsAlwaysOneUnconfiguredFilterAndBendsCannotChangeGameplay() throws Exception {
        var graph = graph();
        assertTrue(graph.outputs().isEmpty(), "No unconditional alternative output pool");
        assertTrue(graph.nodes().values().stream().allMatch(n -> n.scrapEntries().isEmpty()));
        var strippedJson = json("data/graph_assembly/recipe/graph_filter.json");
        strippedJson.getAsJsonArray("edges").forEach(e -> e.getAsJsonObject().remove("waypoints"));
        var stripped = GraphCodec.parse(graph.id(), strippedJson);
        var body = route(graph, "body_start", "body_ready", "create:attribute_filter", "graph_assembly:incomplete_graph_filter");
        var plainBody = route(stripped, "body_start", "body_ready", "create:attribute_filter", "graph_assembly:incomplete_graph_filter");
        assertEquals(body, plainBody);
        for (double draw : new double[]{0, .5, Math.nextDown(1.0)}) {
            var result = GraphEngine.complete(graph, body, "assemble", "graph_assembly:incomplete_graph_filter", () -> draw);
            assertEquals(result, GraphEngine.complete(stripped, plainBody, "assemble", "graph_assembly:incomplete_graph_filter", () -> draw));
            assertTrue(result.settled());
            assertNull(result.state());
            assertEquals(1, result.outputs().size());
            assertEquals("graph_assembly:graph_filter", result.output().item());
            assertEquals(1, result.output().count());
            assertEquals(new JsonObject(), result.output().components());
        }
    }

    @Test void optionalKubejsExampleReplacesSameBundledRecipeWithoutChangingDefinition() throws Exception {
        var script = Files.readString(Path.of("examples/kubejs/filter_crafting.js"));
        assertTrue(script.contains("event.remove({ id: 'graph_assembly:graph_filter' })"));
        assertTrue(script.contains(").id('graph_assembly:graph_filter')"));
        int start = script.indexOf("event.custom(") + "event.custom(".length();
        int end = script.lastIndexOf(").id(");
        var example = JsonParser.parseString(script.substring(start, end)).getAsJsonObject();
        assertFalse(example.has("format_version"));
        assertEquals(graph(), GraphCodec.parse("graph_assembly:graph_filter", example));
        assertEquals(json("data/graph_assembly/recipe/graph_filter.json"), example);
    }
}
