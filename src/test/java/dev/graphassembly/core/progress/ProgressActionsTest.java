package dev.graphassembly.core.progress;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.GraphTopology;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.route.RouteResolver;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ProgressActionsTest {
    public static GraphDefinition<OperationSpec> graph(String actions) {
        return configured(actions, ignored -> {});
    }
    static GraphDefinition<OperationSpec> configured(String actions, java.util.function.Consumer<com.google.gson.JsonObject> edit) {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
               {"id":"clean","recipe":{"type":"create:cutting"},"progress_actions":%s},
               {"id":"target","recipe":{"type":"create:pressing"}},
               {"id":"normal","recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"s","to":"clean"},{"from":"s","to":"target"},{"from":"clean","to":"normal"}],"outputs":[]}
            """.formatted(actions)).getAsJsonObject();
        edit.accept(json);
        return GraphCodec.parse("test:actions",json);
    }
    static Progress before() { return new Progress("test:actions", "s", "s", new MarkAccounting.State(Map.of("defect",5,"quality",2), Map.of("s",1)), List.of("s","target","s"), new RouteState(null, null), List.of("s")); }
    static TransitionResult clean(GraphDefinition<OperationSpec> graph) { return GraphPipeline.resolve(GraphPipeline.plan(graph, before(), "clean", "minecraft:iron_nugget"), () -> { throw new AssertionError("Must not draw randomness"); }); }
    @Test void clearsRequestedQuantityWithoutChangingUnrelatedProgress() {
        var result = clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\",\"count\":2}]"));
        assertEquals(Map.of("defect",3,"quality",2),((TransitionResult.Continued) result).progress().marks().values());
        assertEquals(before().marks().grantCounts(),((TransitionResult.Continued) result).progress().marks().grantCounts());
        assertEquals(before().checkpoints(),((TransitionResult.Continued) result).progress().checkpoints());
        assertEquals("s",((TransitionResult.Continued) result).progress().entry());
    }
    @Test void clearAllZeroAndSaturatingAmounts() {
        assertEquals(Map.of("quality",2),((TransitionResult.Continued) clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\"}]"))).progress().marks().values());
        for (int count : new int[]{0,3,5,9,Integer.MAX_VALUE}) {
            var marks=((TransitionResult.Continued) clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\",\"count\":"+count+"}]"))).progress().marks().values();
            assertEquals(Math.max(0,5-count),marks.getOrDefault("defect",0));
            assertEquals(count<5,marks.containsKey("defect"));
        }
        assertEquals(before().marks().values(),((TransitionResult.Continued) clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"absent\",\"count\":3}]"))).progress().marks().values());
    }
    @Test void checkpointDeletionIsOrderedAndDoesNotChangeEntryOrRecentHistory() {
        var result=((TransitionResult.Continued) clean(graph("""
            [{"type":"clear_checkpoint","node":"s"},{"type":"pop_checkpoint"},{"type":"pop_checkpoint"}]
            """))).progress();
        assertTrue(result.checkpoints().isEmpty());
        assertEquals("s",result.entry());
        assertEquals(List.of("s","clean"),result.recentHistory());
        assertEquals(List.of("target"),((TransitionResult.Continued) clean(graph("[{\"type\":\"clear_checkpoint\",\"node\":\"s\"}]"))).progress().checkpoints());
    }
    @Test void rejectsInvalidActionFieldsAmountsAndReferences() {
        for (String action : List.of("{\"type\":\"clear_mark\",\"mark\":\"x\",\"count\":-1}",
                "{\"type\":\"clear_mark\",\"mark\":\"x\",\"count\":1.5}",
                "{\"type\":\"clear_checkpoint\",\"node\":\"absent\"}",
                "{\"type\":\"pop_checkpoint\",\"node\":\"s\"}", "{\"type\":\"typo\"}"))
            assertTrue(assertThrows(IllegalArgumentException.class,()->graph("["+action+"]")).getMessage().contains("progress_actions[0]"));
    }
    @Test void redirectSelectsTargetWithoutPretendingItWasProcessed() {
        var graph=graph("[{\"type\":\"goto_node\",\"node\":\"target\"}]");
        var state=((TransitionResult.Continued) clean(graph)).progress();
        assertEquals(List.of("target"),GraphPipeline.allowed(graph,state));
        assertEquals("clean",state.current());
        assertEquals(before().marks().values(),state.marks().values());
        assertEquals(before().checkpoints(),state.checkpoints());
    }
    @Test void routeSurvivesBothSerializersAndIsConsumedOnlyByTargetCompletion() {
        for (String action : List.of("{\"type\":\"goto_node\",\"node\":\"target\"}",
                "{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}")) {
            var graph=graph("["+action+"]");var state=((TransitionResult.Continued) clean(graph)).progress();
            assertEquals(state,ProgressCodec.fromJson(ProgressCodec.toJson(state)));
            assertEquals(state,ProgressCodec.fromJson(JsonParser.parseString(ProgressCodec.toJsonString(state)).getAsJsonObject()));
            assertEquals(ProgressCodec.toJson(state),JsonParser.parseString(ProgressCodec.toJsonString(state)));
            assertThrows(IllegalArgumentException.class,()->GraphPipeline.plan(graph,state,"normal","minecraft:iron_nugget"));
            var next=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "target", "minecraft:iron_nugget"), ()->0)).progress();
            assertNull(next.routing().pendingRoute());assertEquals("target",next.current());
            assertEquals(List.of("s","clean","target"),next.recentHistory());
        }
        assertNull(ProgressCodec.fromJson(ProgressCodec.toJson(before())).routing().pendingRoute());
    }
    @Test void explicitRouteOverridesRandomWithoutDrawingOrForgingEdgeSourceHistory() {
        var graph=configured("[{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}]",
            json->json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("branch_mode","random"));
        var state=((TransitionResult.Continued) clean(graph)).progress();assertNull(state.routing().lockedNext());
        assertEquals(List.of("target"),GraphPipeline.allowed(graph,state));
        assertEquals(before().checkpoints(),state.checkpoints());
        assertEquals(List.of("s","clean"),state.recentHistory());
        assertTrue(((TransitionPlan.ContinuationPlan) GraphPipeline.plan(graph,before(),"clean","minecraft:iron_nugget")).randomBranches().isEmpty());
    }
    @Test void routeStillChecksTargetInputConditionsAndIsNotAnAuxiliaryProgressMerge() {
        var graph=configured("[{\"type\":\"goto_node\",\"node\":\"target\"}]",json->
            json.getAsJsonArray("nodes").get(2).getAsJsonObject().add("workpiece_inputs",JsonParser.parseString("""
                [{"item_input":0,"requirements":{"mark_ranges":{"quality":{"min":3}}}}]
                """)));
        var state=((TransitionResult.Continued) clean(graph)).progress();assertTrue(GraphPipeline.allowed(graph,state).isEmpty());
        assertEquals(PreflightResult.BlockedReason.PRIMARY_CONDITIONS,GraphPipeline.preflight(graph,state).blocked().get("target").reason());
        assertTrue(RouteResolver.canEnter(graph,state,"target"),"Auxiliary role reachability remains structural");
    }
    @Test void changedRouteAndRemovedEdgeStopInsteadOfFallingBack() {
        var graph=graph("[{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}]");var state=((TransitionResult.Continued) clean(graph)).progress();
        assertNotNull(RouteResolver.incompatibility(graph("[]"),state));
        var removed=new GraphDefinition<>(graph.id(), new GraphDefaults(graph.defaults().ingredient(), graph.defaults().transitionalItem()), graph.nodes(), new GraphTopology(graph.topology().edges().stream().filter(e->!e.to().equals("target")).toList()), graph.outputRules(), graph.layout());
        assertNotNull(RouteResolver.incompatibility(removed,state));
        assertTrue(GraphPipeline.allowed(removed,state).isEmpty());
        assertEquals("target",state.routing().pendingRoute().node());
    }
    @Test void validatesDirectedEdgeAndRejectsMultipleRedirects() {
        for(String actions:List.of("[{\"type\":\"goto_node\",\"node\":\"absent\"}]",
            "[{\"type\":\"enter_edge\",\"from\":\"target\",\"to\":\"s\"}]",
            "[{\"type\":\"goto_node\",\"node\":\"s\"},{\"type\":\"goto_node\",\"node\":\"target\"}]"))
            assertThrows(IllegalArgumentException.class,()->graph(actions));
    }
    @Test void markGrantsAndCurrentCheckpointAreEditedBeforeSettlementAndRouteIsIgnoredOnSuccess() {
        var graph=configured("""
            [{"type":"clear_mark","mark":"defect","count":7},{"type":"clear_checkpoint","node":"s"},
             {"type":"pop_checkpoint"},{"type":"goto_node","node":"target"}]
            """,json->{
                var node=json.getAsJsonArray("nodes").get(1).getAsJsonObject();node.addProperty("finish",true);
                node.add("mark_amounts",JsonParser.parseString("{\"defect\":2}"));
                node.add("outputs",JsonParser.parseString("""
                    [{"requirements":{"marks":{"quality":2},"checkpoints":["target"]},"results":[{"item":"minecraft:diamond"}]}]
                    """));
            });
        var completion=GraphPipeline.resolve(GraphPipeline.plan(graph, before(), "clean", "minecraft:iron_nugget"), ()->0);
        assertTrue((completion instanceof TransitionResult.Settled));assertInstanceOf(TransitionResult.Settled.class, completion);assertEquals("minecraft:diamond",((TransitionResult.Settled) completion).outputs().getFirst().item());
    }
    @Test void clearDoesNotResetRewardLimitAndPreviewDoesNotMutateInput() {
        var graph=configured("[{\"type\":\"clear_mark\",\"mark\":\"defect\"},{\"type\":\"goto_node\",\"node\":\"clean\"}]",json->{
            var node=json.getAsJsonArray("nodes").get(1).getAsJsonObject();node.addProperty("max_mark_applications",1);
            node.add("mark_amounts",JsonParser.parseString("{\"defect\":2}"));
        });
        var original=before();GraphPipeline.plan(graph,original,"clean","minecraft:iron_nugget");
        assertEquals(5,original.marks().values().get("defect"));
        var first=((TransitionResult.Continued) clean(graph)).progress();var second=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, first, "clean", "minecraft:iron_nugget"), ()->0)).progress();
        assertEquals(1,second.marks().grantCounts().get("clean"));assertFalse(second.marks().values().containsKey("defect"));
    }
}
