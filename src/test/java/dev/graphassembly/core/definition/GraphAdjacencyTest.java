package dev.graphassembly.core.definition;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

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
        assertSame(graph.topology().outgoing("a"), graph.topology().outgoing("a"));
        assertEquals(List.of("b", "c"), graph.topology().outgoing("a").stream().map(Edge::to).toList());
        assertEquals(List.of("a"), graph.topology().outgoing("c").stream().map(Edge::to).toList());
        assertThrows(UnsupportedOperationException.class, () -> graph.topology().outgoing("a").clear());
    }

    @Test
    void sparseGraphsKeepUnrelatedEdgesOutOfTheCurrentAdjacencyAndSuccessors() {
        var base = GraphCodec.parse("test:sparse", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting"}},
                      {"id":"b","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """).getAsJsonObject());
        var small = base;
        var edges = new ArrayList<>(base.topology().edges());
        for (int i = 0; i < 2048; i++) edges.add(new Edge("a", "b", 1));
        var large = new GraphDefinition<>(base.id(), new GraphDefaults(base.defaults().ingredient(), base.defaults().transitionalItem()), base.nodes(), new GraphTopology(edges), base.outputRules(), base.layout());
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(base, null, "s", "minecraft:stone"), () -> fail("No random draw"))).progress();
        for (var graph : List.of(small, large)) {
            assertEquals(2, graph.topology().outgoing("s").size());
            assertSame(graph.topology().outgoing("s"), graph.topology().outgoing("s"));
            assertEquals(List.of("a", "b"), GraphPipeline.allowed(graph, state));
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
        var graph = new GraphDefinition<>(base.id(), new GraphDefaults(base.defaults().ingredient(), base.defaults().transitionalItem()), callerNodes, new GraphTopology(callerEdges), base.outputRules(), base.layout());
        var equal = new GraphDefinition<>(base.id(), new GraphDefaults(base.defaults().ingredient(), base.defaults().transitionalItem()), base.nodes(), new GraphTopology(callerEdges), base.outputRules(), base.layout());
        callerEdges.clear(); callerNodes.clear();
        assertEquals(equal, graph);
        assertEquals(equal.hashCode(), graph.hashCode());
        assertEquals(List.of("a", "a", "b"), graph.topology().outgoing("s").stream().map(Edge::to).toList());
        assertEquals(List.of("s", "a", "b"), new ArrayList<>(graph.nodes().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        graph.defaults().ingredient().getAsJsonObject().addProperty("item", "minecraft:dirt");
        assertEquals("minecraft:stone", graph.defaults().ingredient().getAsJsonObject().get("item").getAsString());
        for (double draw : new double[] {0, .49, .5, .99}) {
            var completed = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "minecraft:stone"), () -> draw);
            assertEquals(draw < .5 ? "a" : "b", ((TransitionResult.Continued) completed).progress().routing().lockedNext());
            assertEquals(List.of(((TransitionResult.Continued) completed).progress().routing().lockedNext()), GraphPipeline.allowed(graph, ((TransitionResult.Continued) completed).progress()));
            assertEquals(((TransitionResult.Continued) completed).progress(), ProgressCodec.fromJson(ProgressCodec.toJson(((TransitionResult.Continued) completed).progress())));
        }
        assertNotEquals(graph, base);
        assertEquals(List.of("a", "a", "b"), graph.topology().edges().stream().map(Edge::to).toList());
    }

    @Test
    void reciprocalAndSelfLoopAdjacencyRetainsCodecEncounterOrder() {
        var graph = GraphCodec.parse("test:loops", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:stone"},"transitional_item":"minecraft:stone",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a","bidirectional":true},{"from":"s","to":"s"}]}
            """).getAsJsonObject());
        assertEquals(List.of("a", "s"), graph.topology().outgoing("s").stream().map(Edge::to).toList());
        assertEquals(List.of("s"), graph.topology().outgoing("a").stream().map(Edge::to).toList());
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "minecraft:stone"), () -> fail("Choice path does not draw"))).progress();
        assertEquals(List.of("a", "s"), GraphPipeline.allowed(graph, state));
    }
}
