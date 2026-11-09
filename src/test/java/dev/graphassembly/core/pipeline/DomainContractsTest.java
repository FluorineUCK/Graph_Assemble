package dev.graphassembly.core.pipeline;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.InputEligibility;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.io.NestedOutputsTest;
import dev.graphassembly.core.io.OutputItem;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.ProgressActionsTest;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.PendingRoute;
import dev.graphassembly.core.route.RouteResolver;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainContractsTest {
    private static GraphDefinition<OperationSpec> graph() {
        return GraphCodec.parse("test:contracts", JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
               {"id":"b","recipe":{"type":"create:cutting"},"mark_amounts":{"x":1},
                "workpiece_inputs":[{"item_input":0,"requirements":{"mark_ranges":{"y":{"min":1}}}},
                  {"item_input":1,"current_nodes":["s"]}]}],"edges":[{"from":"s","to":"b"}]}
            """).getAsJsonObject());
    }

    private static Progress progress(RouteState route, Map<String,Integer> marks) {
        return new Progress("test:contracts","s","s",new MarkAccounting.State(marks,Map.of()),
            List.of("s"),route,List.of("s"));
    }

    @Test void continuationPlansRequireCarrierAndMutuallyExclusiveRoutes() {
        var plain = progress(new RouteState(null,null),Map.of());
        var explicit = progress(new RouteState(null,new PendingRoute("b",null)),Map.of());
        var locked = progress(new RouteState("b",null),Map.of());
        var both = progress(new RouteState("b",new PendingRoute("b",null)),Map.of());
        var branches = List.of(new Edge("s","b",1));
        assertThrows(NullPointerException.class,()->new TransitionPlan.ContinuationPlan(null,"test:work",List.of()));
        assertThrows(NullPointerException.class,()->new TransitionPlan.ContinuationPlan(plain,null,List.of()));
        for (var invalid : List.of(explicit,locked,both))
            assertThrows(IllegalArgumentException.class,()->new TransitionPlan.ContinuationPlan(invalid,"test:work",branches));
        assertThrows(IllegalArgumentException.class,()->new TransitionPlan.ContinuationPlan(both,"test:work",List.of()));
        assertEquals(List.of(),new TransitionPlan.ContinuationPlan(explicit,"test:work",List.of()).randomBranches());
        assertThrows(NullPointerException.class,()->new TransitionResult.Continued(null,"test:work"));
        assertThrows(NullPointerException.class,()->new TransitionResult.Continued(plain,null));
        assertEquals(List.of(),assertInstanceOf(TransitionResult.Settled.class,
            GraphPipeline.resolve(new TransitionPlan.SettlementPlan(List.of()),()->fail("Empty settlement must not draw"))).outputs());
    }

    @Test void damagedPersistedRoutesRemainAvailableForCompatibilityDiagnostics() {
        var both = progress(new RouteState("b",new PendingRoute("b",null)),Map.of());
        var decoded = ProgressCodec.fromJson(ProgressCodec.toJson(both));
        assertEquals(both,decoded);
        assertEquals("Both random and explicit routes are saved",RouteResolver.incompatibility(graph(),decoded));
    }

    @Test void roleEligibilityKeepsAuxiliaryChecksSeparateFromPrimaryCapacityAndConditions() {
        var graph = graph();
        var target = graph.nodes().get("b");
        assertTrue(InputEligibility.canParticipate(graph,graph.nodes().get("s"),0,null));
        assertTrue(InputEligibility.canParticipate(graph,target,2,null));
        assertFalse(InputEligibility.canParticipate(graph,target,0,null));
        assertFalse(InputEligibility.canParticipate(graph,target,1,null));
        for (int y : List.of(0,1)) {
            var before = progress(new RouteState(null,null),Map.of("x",Integer.MAX_VALUE,"y",y));
            assertFalse(InputEligibility.canParticipate(graph,target,0,before));
            assertTrue(InputEligibility.canParticipate(graph,target,1,before));
            assertFalse(InputEligibility.canParticipate(graph,target,2,before));
            assertEquals(y==0?PreflightResult.BlockedReason.PRIMARY_CONDITIONS:PreflightResult.BlockedReason.MARK_CAPACITY,
                GraphPipeline.preflight(graph,before).blocked().get("b").reason());
        }
    }

    @Test void actionReferenceDiagnosticsRetainOriginalArrayIndicesAfterSplitting() {
        var error = assertThrows(IllegalArgumentException.class,()->ProgressActionsTest.graph("""
            [{"type":"clear_mark","mark":"x"},{"type":"goto_node","node":"target"},
             {"type":"pop_checkpoint"},{"type":"goto_node","node":"s"}]
            """));
        assertTrue(error.getMessage().contains("progress_actions[3]: at most one route action"),error.getMessage());
    }

    @Test void nestedRandomTracePreservesSelectionGateAndPerItemOrder() {
        var graph = NestedOutputsTest.graph("""
            [{"mode":"all","chance":0.5,"results":[
              {"item":"test:never","chance":0},
              {"item":"test:fixed","chance":1},
              {"item":"test:trials","count":2,"chance":0.25},
              {"mode":"weighted","results":[{"item":"test:left"},{"item":"test:right"}]}]}]
            """);
        var plan = assertInstanceOf(TransitionPlan.SettlementPlan.class,GraphPipeline.plan(graph,null,"finish","test:raw"));
        double[] trace = {0,0.5,0.25,0.26,0.9};
        var index = new AtomicInteger();
        var settled = assertInstanceOf(TransitionResult.Settled.class,GraphPipeline.resolve(plan,()->trace[index.getAndIncrement()]));
        assertEquals(trace.length,index.get());
        assertEquals(List.of("test:fixed","test:trials","test:right"),settled.outputs().stream().map(OutputItem::item).toList());
        assertEquals(List.of(1,1,1),settled.outputs().stream().map(OutputItem::count).toList());
    }
}
