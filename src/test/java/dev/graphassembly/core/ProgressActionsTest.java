package dev.graphassembly.core;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressActionsTest {
    static GraphDefinition graph(String actions) {
        return configured(actions, ignored -> {});
    }
    static GraphDefinition configured(String actions, java.util.function.Consumer<com.google.gson.JsonObject> edit) {
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
    static Progress before() { return new Progress("test:actions","s","s",Map.of("defect",5,"quality",2),
        List.of("s","target","s"),Map.of("s",1),null,List.of("s")); }
    static Completion clean(GraphDefinition graph) { return GraphEngine.complete(graph,before(),"clean","minecraft:iron_nugget",
        () -> { throw new AssertionError("Must not draw randomness"); }); }
    @Test void clearsRequestedQuantityWithoutChangingUnrelatedProgress() {
        var result = clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\",\"count\":2}]"));
        assertEquals(Map.of("defect",3,"quality",2),result.state().marks());
        assertEquals(before().rewardCounts(),result.state().rewardCounts());
        assertEquals(before().checkpoints(),result.state().checkpoints());
        assertEquals("s",result.state().entry());
    }
    @Test void clearAllZeroAndSaturatingAmounts() {
        assertEquals(Map.of("quality",2),clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\"}]")).state().marks());
        for (int count : new int[]{0,3,5,9,Integer.MAX_VALUE}) {
            var marks=clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"defect\",\"count\":"+count+"}]")).state().marks();
            assertEquals(Math.max(0,5-count),marks.getOrDefault("defect",0));
            assertEquals(count<5,marks.containsKey("defect"));
        }
        assertEquals(before().marks(),clean(graph("[{\"type\":\"clear_mark\",\"mark\":\"absent\",\"count\":3}]")).state().marks());
    }
    @Test void checkpointDeletionIsOrderedAndDoesNotChangeEntryOrRecentHistory() {
        var result=clean(graph("""
            [{"type":"clear_checkpoint","node":"s"},{"type":"pop_checkpoint"},{"type":"pop_checkpoint"}]
            """)).state();
        assertTrue(result.checkpoints().isEmpty());
        assertEquals("s",result.entry());
        assertEquals(List.of("s","clean"),result.history());
        assertEquals(List.of("target"),clean(graph("[{\"type\":\"clear_checkpoint\",\"node\":\"s\"}]")).state().checkpoints());
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
        var state=clean(graph).state();
        assertEquals(List.of("target"),GraphEngine.allowed(graph,state));
        assertEquals("clean",state.current());
        assertEquals(before().marks(),state.marks());
        assertEquals(before().checkpoints(),state.checkpoints());
    }
    @Test void routeSurvivesBothSerializersAndIsConsumedOnlyByTargetCompletion() {
        for (String action : List.of("{\"type\":\"goto_node\",\"node\":\"target\"}",
                "{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}")) {
            var graph=graph("["+action+"]");var state=clean(graph).state();
            assertEquals(state,Progress.fromJson(state.toJson()));
            assertEquals(state,Progress.fromJson(JsonParser.parseString(state.toJsonString()).getAsJsonObject()));
            assertEquals(state.toJson(),JsonParser.parseString(state.toJsonString()));
            assertThrows(IllegalArgumentException.class,()->GraphEngine.plan(graph,state,"normal","minecraft:iron_nugget"));
            var next=GraphEngine.complete(graph,state,"target","minecraft:iron_nugget",()->0).state();
            assertNull(next.pendingRoute());assertEquals("target",next.current());
            assertEquals(List.of("s","clean","target"),next.history());
        }
        assertNull(Progress.fromJson(before().toJson()).pendingRoute());
    }
    @Test void explicitRouteOverridesRandomWithoutDrawingOrForgingEdgeSourceHistory() {
        var graph=configured("[{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}]",
            json->json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("branch_mode","random"));
        var state=clean(graph).state();assertNull(state.lockedNext());
        assertEquals(List.of("target"),GraphEngine.allowed(graph,state));
        assertEquals(before().checkpoints(),state.checkpoints());
        assertEquals(List.of("s","clean"),state.history());
        assertTrue(GraphEngine.plan(graph,before(),"clean","minecraft:iron_nugget").branches().isEmpty());
    }
    @Test void routeStillChecksTargetInputConditionsAndIsNotAnAuxiliaryProgressMerge() {
        var graph=configured("[{\"type\":\"goto_node\",\"node\":\"target\"}]",json->
            json.getAsJsonArray("nodes").get(2).getAsJsonObject().add("workpiece_inputs",JsonParser.parseString("""
                [{"item_input":0,"requirements":{"mark_ranges":{"quality":{"min":3}}}}]
                """)));
        var state=clean(graph).state();assertTrue(GraphEngine.allowed(graph,state).isEmpty());
        assertEquals(GraphEngine.BlockedReason.PRIMARY_CONDITIONS,GraphEngine.preflight(graph,state).blockedReasons().get("target"));
        assertTrue(GraphEngine.canEnter(graph,state,"target"),"Auxiliary role reachability remains structural");
    }
    @Test void changedRouteAndRemovedEdgeStopInsteadOfFallingBack() {
        var graph=graph("[{\"type\":\"enter_edge\",\"from\":\"s\",\"to\":\"target\"}]");var state=clean(graph).state();
        assertNotNull(GraphEngine.incompatibility(graph("[]"),state));
        var removed=new GraphDefinition(graph.id(),graph.ingredient(),graph.transitionalItem(),graph.nodes(),
            graph.edges().stream().filter(e->!e.to().equals("target")).toList(),graph.outputs());
        assertNotNull(GraphEngine.incompatibility(removed,state));
        assertTrue(GraphEngine.allowed(removed,state).isEmpty());
        assertEquals("target",state.pendingRoute().node());
    }
    @Test void validatesDirectedEdgeAndRejectsMultipleRedirects() {
        for(String actions:List.of("[{\"type\":\"goto_node\",\"node\":\"absent\"}]",
            "[{\"type\":\"enter_edge\",\"from\":\"target\",\"to\":\"s\"}]",
            "[{\"type\":\"goto_node\",\"node\":\"s\"},{\"type\":\"goto_node\",\"node\":\"target\"}]"))
            assertThrows(IllegalArgumentException.class,()->graph(actions));
    }
    @Test void rewardsAndCurrentCheckpointAreEditedBeforeSettlementAndRouteIsIgnoredOnSuccess() {
        var graph=configured("""
            [{"type":"clear_mark","mark":"defect","count":7},{"type":"clear_checkpoint","node":"s"},
             {"type":"pop_checkpoint"},{"type":"goto_node","node":"target"}]
            """,json->{
                var node=json.getAsJsonArray("nodes").get(1).getAsJsonObject();node.addProperty("finish",true);
                node.add("rewards",JsonParser.parseString("{\"defect\":2}"));
                node.add("outputs",JsonParser.parseString("""
                    [{"requirements":{"marks":{"quality":2},"checkpoints":["target"]},"results":[{"item":"minecraft:diamond"}]}]
                    """));
            });
        var completion=GraphEngine.complete(graph,before(),"clean","minecraft:iron_nugget",()->0);
        assertTrue(completion.settled());assertNull(completion.state());assertEquals("minecraft:diamond",completion.output().item());
    }
    @Test void clearDoesNotResetRewardLimitAndPreviewDoesNotMutateInput() {
        var graph=configured("[{\"type\":\"clear_mark\",\"mark\":\"defect\"},{\"type\":\"goto_node\",\"node\":\"clean\"}]",json->{
            var node=json.getAsJsonArray("nodes").get(1).getAsJsonObject();node.addProperty("reward_limit",1);
            node.add("rewards",JsonParser.parseString("{\"defect\":2}"));
        });
        var original=before();GraphEngine.plan(graph,original,"clean","minecraft:iron_nugget");
        assertEquals(5,original.marks().get("defect"));
        var first=clean(graph).state();var second=GraphEngine.complete(graph,first,"clean","minecraft:iron_nugget",()->0).state();
        assertEquals(1,second.rewardCounts().get("clean"));assertFalse(second.marks().containsKey("defect"));
    }
}
