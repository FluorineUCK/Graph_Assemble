package dev.graphassembly.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GraphAdjacencyTest {
    @Test
    void outgoingIsImmutableOrderedAndReusedForTheSameNode() {
        var graph = GraphCodec.parse("test:adjacency", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"b","recipe":{"type":"create:cutting"}},
                      {"id":"c","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"a","to":"b"},{"from":"c","to":"a"},{"from":"a","to":"c"}]}
            """).getAsJsonObject());
        assertSame(graph.outgoing("a"), graph.outgoing("a"));
        assertEquals(List.of("b", "c"), graph.outgoing("a").stream().map(Edge::to).toList());
        assertEquals(List.of("a"), graph.outgoing("c").stream().map(Edge::to).toList());
        assertThrows(UnsupportedOperationException.class, () -> graph.outgoing("a").clear());
    }

    @Test
    void sparseSuccessorVisitCountDependsOnCurrentOutdegreeNotTotalEdges() {
        var base = GraphCodec.parse("test:sparse", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting"}},
                      {"id":"b","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """).getAsJsonObject());
        var small = base;
        var edges = new ArrayList<>(base.edges());
        for (int i = 0; i < 2048; i++) edges.add(new Edge("a", "b", 1));
        var large = new GraphDefinition(base.id(), base.ingredient(), base.transitionalItem(), base.nodes(), edges, base.outputs());
        var state = GraphEngine.complete(base, null, "s", "minecraft:stone", () -> fail("No random draw")).state();
        for (var graph : List.of(small, large)) {
            var visits = new AtomicInteger();
            try (var ignored = GraphEngine.observeEdges(visits::addAndGet)) {
                assertEquals(List.of("a", "b"), GraphEngine.allowed(graph, state));
            }
            assertEquals(2, visits.get());
        }
    }

    @Test
    void duplicateOutgoingWeightsRemainInRandomPoolAndValueCopiesStayIsolated() {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting"}},
                      {"id":"b","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """).getAsJsonObject();
        var base = GraphCodec.parse("test:duplicate", json);
        var callerEdges = new ArrayList<>(List.of(new Edge("s", "a", 1), new Edge("s", "a", 1), new Edge("s", "b", 2)));
        var callerNodes = new java.util.LinkedHashMap<>(base.nodes());
        var graph = new GraphDefinition(base.id(), base.ingredient(), base.transitionalItem(), callerNodes, callerEdges, base.outputs());
        var equal = new GraphDefinition(base.id(), base.ingredient(), base.transitionalItem(), base.nodes(), callerEdges, base.outputs());
        callerEdges.clear(); callerNodes.clear();
        assertEquals(equal, graph);
        assertEquals(equal.hashCode(), graph.hashCode());
        assertEquals(List.of("a", "a", "b"), graph.outgoing("s").stream().map(Edge::to).toList());
        assertEquals(List.of("s", "a", "b"), new ArrayList<>(graph.nodes().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        graph.ingredient().getAsJsonObject().addProperty("item", "minecraft:dirt");
        assertEquals("minecraft:stone", graph.ingredient().getAsJsonObject().get("item").getAsString());
        for (double draw : new double[] {0, .49, .5, .99}) {
            var completed = GraphEngine.complete(graph, null, "s", "minecraft:stone", () -> draw);
            assertEquals(draw < .5 ? "a" : "b", completed.state().lockedNext());
            assertEquals(List.of(completed.state().lockedNext()), GraphEngine.allowed(graph, completed.state()));
            assertEquals(completed.state(), Progress.fromJson(completed.state().toJson()));
        }
        assertNotEquals(graph, base);
        assertEquals(List.of("a", "a", "b"), graph.edges().stream().map(Edge::to).toList());
    }

    @Test
    void reciprocalAndSelfLoopAdjacencyRetainsCodecEncounterOrder() {
        var graph = GraphCodec.parse("test:loops", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a","bidirectional":true},{"from":"s","to":"s"}]}
            """).getAsJsonObject());
        assertEquals(List.of("a", "s"), graph.outgoing("s").stream().map(Edge::to).toList());
        assertEquals(List.of("s"), graph.outgoing("a").stream().map(Edge::to).toList());
        var state = GraphEngine.complete(graph, null, "s", "minecraft:stone", () -> fail("Choice path does not draw")).state();
        assertEquals(List.of("a", "s"), GraphEngine.allowed(graph, state));
    }
}
