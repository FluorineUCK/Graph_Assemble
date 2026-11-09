package dev.graphassembly.core.pipeline;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.PendingRoute;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Baseline observations captured before replacing the monolithic engine. */
class PipelineCharacterizationTest {
    private GraphDefinition<OperationSpec> graph(String body) {
        return GraphCodec.parse("test:trace", JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
            %s}
            """.formatted(body)).getAsJsonObject());
    }

    @Test void planningAppliesGrantCheckpointAndOrderedCleanupBeforeSettlementWithoutDrawing() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"},
            "mark_amounts":{"zero":0,"x":2},"progress_actions":[{"type":"clear_mark","mark":"x","count":1},
            {"type":"pop_checkpoint"}],"outputs":[{"requirements":{"marks":{"zero":0,"x":1},"checkpoints":[]},
            "results":[{"item":"test:success"}]}]}],"edges":[]
            """);
        var plan = GraphPipeline.plan(graph, null, "s", "test:raw");
        assertTrue((plan instanceof TransitionPlan.SettlementPlan));
        AtomicInteger calls = new AtomicInteger();
        var result = GraphPipeline.resolve(plan, () -> { calls.incrementAndGet(); return 0; });
        assertEquals("test:success", ((TransitionResult.Settled) result).outputs().getFirst().item());
        assertEquals(1, calls.get());
    }

    @Test void explicitRouteConsumesNoDrawAndDoesNotFabricateCompletedHistory() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"branch_mode":"random",
            "mark_amounts":{"zero":0},"progress_actions":[{"type":"goto_node","node":"b"}]},
            {"id":"b","recipe":{"type":"create:cutting"}}],"edges":[{"from":"s","to":"b"}]
            """);
        var result = GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"), () -> fail("explicit routes do not draw"));
        assertEquals("s", ((TransitionResult.Continued) result).progress().current());
        assertEquals(List.of("s"), ((TransitionResult.Continued) result).progress().recentHistory());
        assertEquals(Map.of("zero",0), ((TransitionResult.Continued) result).progress().marks().values());
        assertEquals(Map.of("s",1), ((TransitionResult.Continued) result).progress().marks().grantCounts());
        assertEquals(new PendingRoute("b",null),((TransitionResult.Continued) result).progress().routing().pendingRoute());
        assertEquals(((TransitionResult.Continued) result).progress(),ProgressCodec.fromJson(ProgressCodec.toJson(((TransitionResult.Continued) result).progress())));
    }

    @Test void preflightDoesNotLetCleanupRescueOverflowAndPrimaryReasonWins() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
            {"id":"b","recipe":{"type":"create:cutting"},"mark_amounts":{"x":1},
            "workpiece_inputs":[{"item_input":0,"requirements":{"marks":{"missing":1}}}],
            "progress_actions":[{"type":"clear_mark","mark":"x"}]}],"edges":[{"from":"s","to":"b"}]
            """);
        var before = new Progress(graph.id(), "s", "s", new MarkAccounting.State(Map.of("x",Integer.MAX_VALUE), Map.of()), List.of("s"), new RouteState(null, null), List.of("s"));
        var saved = ProgressCodec.toJson(before);
        assertEquals(PreflightResult.BlockedReason.PRIMARY_CONDITIONS,GraphPipeline.preflight(graph,before).blocked().get("b").reason());
        assertEquals(saved,ProgressCodec.toJson(before));
    }
}
