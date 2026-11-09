package dev.graphassembly.core.definition;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class EdgeWaypointsTest {
    @Test void retainsImmutableSeparatedDisplayCoordinates() {
        var json = graphJson();
        var raw = JsonParser.parseString("[[10,20],[30,40]]").getAsJsonArray();
        json.getAsJsonArray("edges").get(0).getAsJsonObject().add("waypoints", raw);
        var graph = GraphCodec.parse("test:waypoints",json);
        var edge = graph.topology().edges().getFirst();
        var points = List.of(new GraphLayout.Waypoint(10, 20), new GraphLayout.Waypoint(30, 40));
        assertEquals(points, graph.layout().edge(edge).waypoints());
        raw.get(0).getAsJsonArray().set(0, JsonParser.parseString("999"));
        assertEquals(points, graph.layout().edge(edge).waypoints());
        assertThrows(UnsupportedOperationException.class, () -> graph.layout().edge(edge).waypoints().clear());

        var mutable = new ArrayList<>(points);
        var direct = new GraphLayout.EdgeLayout(mutable);
        mutable.clear();
        assertEquals(points, direct.waypoints());
        assertEquals(List.of(), graph.layout().edge(new Edge("unknown","target",2)).waypoints());
    }

    static Stream<Arguments> invalidWaypoints() {
        return Stream.of(
            Arguments.of("null", "waypoints"),
            Arguments.of("{}", "waypoints"),
            Arguments.of("\"points\"", "waypoints"),
            Arguments.of("[null]", "waypoints[0]"),
            Arguments.of("[{}]", "waypoints[0]"),
            Arguments.of("[[]]", "waypoints[0]"),
            Arguments.of("[[1]]", "waypoints[0]"),
            Arguments.of("[[1,2,3]]", "waypoints[0]"),
            Arguments.of("[[null,2]]", "waypoints[0][0]"),
            Arguments.of("[[1,\"2\"]]", "waypoints[0][1]"),
            Arguments.of("[[true,2]]", "waypoints[0][0]"),
            Arguments.of("[[1,[]]]", "waypoints[0][1]"),
            Arguments.of("[[1000000.01,2]]", "waypoints[0][0]"),
            Arguments.of("[[1,-1000000.01]]", "waypoints[0][1]"),
            Arguments.of("[[1e999,2]]", "waypoints[0][0]"));
    }

    @ParameterizedTest @MethodSource("invalidWaypoints")
    void rejectsMalformedCoordinatesWithGraphIdAndFullFieldPath(String value, String field) {
        var json = graphJson();
        json.getAsJsonArray("edges").get(0).getAsJsonObject().add("waypoints", JsonParser.parseString(value));
        assertInvalid(json, "edges[0]." + field);
    }

    @Test void enforcesCountBoundAndRejectsProgrammaticNonfiniteNumbers() {
        var json = graphJson();
        var raw = new JsonArray();
        for (int i = 0; i < 64; i++) raw.add(JsonParser.parseString("[-1000000,1000000]"));
        json.getAsJsonArray("edges").get(0).getAsJsonObject().add("waypoints", raw);
        assertEquals(64, GraphCodec.parse("test:waypoints",json).layout().edge(new Edge("a","b",2)).waypoints().size());
        raw.add(JsonParser.parseString("[0,0]"));
        assertInvalid(json, "edges[0].waypoints");
        raw.remove(64);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            raw.get(63).getAsJsonArray().set(1, new JsonPrimitive(invalid));
            assertInvalid(json, "edges[0].waypoints[63][1]");
        }
    }

    private static void assertInvalid(JsonObject json, String path) {
        var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:waypoints", json));
        assertTrue(error.getMessage().contains("Graph test:waypoints:"), error.getMessage());
        assertTrue(error.getMessage().contains(path + ":"), error.getMessage());
    }

    @Test void bidirectionalEdgesReverseDisplayTraversalWithoutAddingSemanticSteps() {
        var json = graphJson();
        var rawEdge = json.getAsJsonArray("edges").get(0).getAsJsonObject();
        rawEdge.add("waypoints", JsonParser.parseString("[[10,20],[30,40]]"));
        rawEdge.addProperty("bidirectional", true);
        var graph = GraphCodec.parse("test:waypoints", json);
        assertEquals(2, graph.nodes().size());
        assertEquals(2, graph.topology().edges().size());
        assertEquals(List.of(new GraphLayout.Waypoint(30, 40), new GraphLayout.Waypoint(10, 20)),
            graph.layout().edge(graph.topology().edges().get(1)).waypoints());
        assertEquals("b", graph.topology().edges().get(1).from());
        assertEquals("a", graph.topology().edges().get(1).to());
        assertEquals(2, graph.topology().edges().get(1).weight());

        rawEdge.remove("waypoints");
        var automatic = GraphCodec.parse("test:waypoints", json);
        var routedStart = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "a", "minecraft:iron_ingot"), () -> 0);
        var automaticStart = GraphPipeline.resolve(GraphPipeline.plan(automatic, null, "a", "minecraft:iron_ingot"), () -> 0);
        assertEquals(automaticStart, routedStart);
        assertEquals(List.of("b"), GraphPipeline.allowed(graph, ((TransitionResult.Continued) routedStart).progress()));
        assertEquals(GraphPipeline.allowed(automatic, ((TransitionResult.Continued) automaticStart).progress()), GraphPipeline.allowed(graph, ((TransitionResult.Continued) routedStart).progress()));
        assertEquals(GraphPipeline.resolve(GraphPipeline.plan(automatic, ((TransitionResult.Continued) automaticStart).progress(), "b", "minecraft:iron_nugget"), () -> 0),
            GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) routedStart).progress(), "b", "minecraft:iron_nugget"), () -> 0));
    }

    @Test void absentAndEmptyWaypointsPreserveAutomaticRoutingAndSelfLoopsStaySingle() {
        var json = graphJson();
        var absent = GraphCodec.parse("test:waypoints", json);
        var rawEdge = json.getAsJsonArray("edges").get(0).getAsJsonObject();
        rawEdge.add("waypoints", new JsonArray());
        assertEquals(absent, GraphCodec.parse("test:waypoints", json));
        rawEdge.addProperty("to", "a");
        rawEdge.addProperty("bidirectional", true);
        rawEdge.add("waypoints", JsonParser.parseString("[[10,20]]"));
        var loop = GraphCodec.parse("test:waypoints", json);
        assertEquals(1, loop.topology().edges().size());
        assertEquals(List.of(new GraphLayout.Waypoint(10, 20)), loop.layout().edge(loop.topology().edges().getFirst()).waypoints());
    }

    private static JsonObject graphJson() {
        return JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"b","finish":true,"recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"a","to":"b","weight":2}],
             "outputs":[{"results":[{"item":"minecraft:gold_ingot"}]}]}
            """).getAsJsonObject();
    }
}
