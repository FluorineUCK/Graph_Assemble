package dev.graphassembly.client.view;

import dev.graphassembly.core.BranchMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NextStepPresentationTest {
    @Test void aSingleAvailableStepIsNotAChoice() {
        assertEquals("graph_assembly.next.single", NextStepPresentation.workpieceKey(1, false));
        assertEquals("graph_assembly.branch.single", NextStepPresentation.branchKey(BranchMode.CHOICE, 1));
        assertEquals("graph_assembly.edge.single", NextStepPresentation.edgeKey(BranchMode.CHOICE, 1));
    }
    @Test void multipleTargetsKeepChoiceAndRandomLockKeepsItsMeaning() {
        assertEquals("graph_assembly.next.choice", NextStepPresentation.workpieceKey(2, false));
        assertEquals("graph_assembly.next.locked", NextStepPresentation.workpieceKey(1, true));
        assertEquals("graph_assembly.branch.choice", NextStepPresentation.branchKey(BranchMode.CHOICE, 2));
        assertEquals("graph_assembly.edge.choice", NextStepPresentation.edgeKey(BranchMode.CHOICE, 2));
        assertEquals("graph_assembly.branch.random", NextStepPresentation.branchKey(BranchMode.RANDOM, 2));
        assertEquals("graph_assembly.edge.random", NextStepPresentation.edgeKey(BranchMode.RANDOM, 1));
    }
    @Test void noSuccessorDoesNotAdvertiseAChoiceOrDuplicateAHeader() {
        assertEquals("graph_assembly.next.none", NextStepPresentation.workpieceKey(0, false));
        assertEquals("graph_assembly.next.none", NextStepPresentation.workpieceKey(0, true));
        for (var mode : BranchMode.values())
            assertEquals("graph_assembly.branch.none", NextStepPresentation.branchKey(mode, 0));
    }
    @Test void workpieceCountsAvailableStepsRatherThanAllDrawnEdges() {
        var graph = dev.graphassembly.core.GraphCodec.parse("test:next_step", com.google.gson.JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"a","recipe":{"type":"create:cutting","processing_time":20}},
                      {"id":"b","recipe":{"type":"create:pressing"},
                       "workpiece_inputs":[{"item_input":0,"current_nodes":["a"]}]}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """).getAsJsonObject());
        var state = dev.graphassembly.core.GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> 0).state();
        var allowed = dev.graphassembly.core.GraphEngine.allowed(graph, state);
        assertEquals(java.util.List.of("a"), allowed);
        assertEquals("graph_assembly.next.single", NextStepPresentation.workpieceKey(allowed.size(), false));
        assertEquals("graph_assembly.branch.choice", NextStepPresentation.branchKey(BranchMode.CHOICE, graph.outgoing("s").size()),
            "A static graph describes topology, not a particular workpiece's progress");
    }
}
