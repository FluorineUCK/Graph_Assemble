package dev.graphassembly.core.pipeline;

import com.google.gson.JsonParser;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ConditionalEdgesTest {
    @Test void choiceOnlyOffersEdgesWhoseMarkAndCheckpointConditionsMatch() {
        var graph=GraphCodec.parse("test:conditions",JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"mark_amounts":{"heat":2},"recipe":{"type":"create:pressing"}},
                      {"id":"yes","recipe":{"type":"create:pressing"}},
                      {"id":"no","recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"s","to":"yes","requirements":{"marks":{"heat":2},"checkpoints":["s"]}},
                      {"from":"s","to":"no","requirements":{"marks":{"heat":3}}}]}
            """).getAsJsonObject());
        var result=(TransitionResult.Continued)GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"),()->fail("choice must not draw"));
        assertEquals(List.of("yes"),GraphPipeline.allowed(graph,result.progress()));
        assertThrows(IllegalArgumentException.class,()->GraphPipeline.plan(graph,result.progress(),"no","test:work"));
    }

    private dev.graphassembly.core.definition.basic.GraphDefinition<?> randomGraph(String edges,String extra) {
        return GraphCodec.parse("test:conditions",JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"branch_mode":"random","mark_amounts":{"heat":2},
                        "progress_actions":[{"type":"clear_mark","mark":"heat","count":1}],"recipe":{"type":"create:pressing"}},
                      {"id":"yes","recipe":{"type":"create:pressing"}},
                      {"id":"no","recipe":{"type":"create:pressing"}%s}],"edges":%s}
            """.formatted(extra,edges)).getAsJsonObject());
    }
    @Test void randomFiltersAfterActionsAndBeforeDrawingAndPersistsItsTarget() {
        var graph=randomGraph("""
            [{"from":"s","to":"no","weight":1000,"requirements":{"marks":{"heat":2}}},
             {"from":"s","to":"yes","requirements":{"marks":{"heat":1},"checkpoints":["s"]}}]
            """, "");
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var state=((TransitionResult.Continued)GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"),()->{calls.incrementAndGet();return 0;})).progress();
        assertEquals("yes",state.routing().lockedNext());
        assertEquals(1,calls.get());
        assertEquals(List.of("yes"),GraphPipeline.allowed(graph,state));
        assertEquals(List.of("yes"),GraphPipeline.allowed(graph,dev.graphassembly.codec.ProgressCodec.fromJson(dev.graphassembly.codec.ProgressCodec.toJson(state))));
    }
    @Test void randomDoesNotSelectTargetWithUnmetPrimaryConditions() {
        var graph=randomGraph("""
            [{"from":"s","to":"no","weight":1000},{"from":"s","to":"yes"}]
            """, """
            ,"workpiece_inputs":[{"item_input":0,"requirements":{"marks":{"missing":1}}}]
            """);
        var state=((TransitionResult.Continued)GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"),()->0)).progress();
        assertEquals("yes",state.routing().lockedNext());
    }
    @Test void noEligibleRandomEdgeStopsWithoutDrawingOrInventingCorruptProgress() {
        var graph=randomGraph("""
            [{"from":"s","to":"no","requirements":{"marks":{"heat":99}}}]
            """, "");
        var state=((TransitionResult.Continued)GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"),()->fail("empty pool must not draw"))).progress();
        assertNull(state.routing().lockedNext());
        assertNull(GraphPipeline.preflight(graph,state).incompatibility());
        assertTrue(GraphPipeline.allowed(graph,state).isEmpty());
    }
    @Test void codecRejectsUnknownEdgeConditionsAndMissingCheckpointReferences() {
        for(String value:List.of("\"requirement\":{}", "\"requirements\":{\"checkpoints\":[\"missing\"]}")) {
            assertThrows(IllegalArgumentException.class,()->randomGraph("[{\"from\":\"s\",\"to\":\"yes\","+value+"}]", ""));
        }
    }
    @Test void bidirectionalCopiesAdmissionInBothDirections() {
        var graph=randomGraph("""
            [{"from":"s","to":"yes","bidirectional":true,"requirements":{"marks":{"heat":1}}}]
            """, "");
        assertEquals(graph.topology().outgoing("s").getFirst().requirements(),graph.topology().outgoing("yes").getFirst().requirements());
    }

    @Test void emptyRandomPoolCannotBeTraversedAsAnAuxiliaryWorkpiece() {
        var graph=randomGraph("[{\"from\":\"s\",\"to\":\"no\"}]", """
            ,"workpiece_inputs":[{"item_input":0,"requirements":{"marks":{"missing":1}}},{"item_input":1}]
            """);
        var state=((TransitionResult.Continued)GraphPipeline.resolve(GraphPipeline.plan(graph,null,"s","test:raw"),()->fail("no eligible primary"))).progress();
        assertNull(GraphPipeline.preflight(graph,state).incompatibility());
        assertFalse(dev.graphassembly.core.io.InputEligibility.canParticipate(graph,graph.nodes().get("no"),1,state));
    }
}
