package dev.graphassembly.client.view;

import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.WeightedOutput;
import dev.graphassembly.core.GraphCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementDescriptionTest {
    @Test void progressActionsShareOrderedCopyWithNodeTooltipsAndExplainSettlementTiming() {
        var graph=parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"},
              "progress_actions":[{"type":"clear_mark","mark":"defect","count":2},
               {"type":"clear_mark","mark":"other"},{"type":"clear_checkpoint","node":"s"},
               {"type":"pop_checkpoint"},{"type":"goto_node","node":"s"}]}],"edges":[],"outputs":[]}
            """);
        var expected=ProgressActionDescription.of(graph.nodes().get("s"));
        assertEquals(List.of("actions.node","actions.subtract_mark","actions.clear_mark","actions.clear_checkpoint",
            "actions.pop_checkpoint","actions.goto_node"),expected.stream().map(SettlementDescription.Line::key).toList());
        assertEquals(List.of("defect",2),expected.get(1).arguments());
        var lines=SettlementDescription.from(graph).paragraphs();
        int start=lines.indexOf(expected.getFirst());
        assertEquals(expected,lines.subList(start,start+expected.size()));
        assertTrue(lines.stream().anyMatch(l->l.key().equals("actions.timing")));
        assertFalse(lines.stream().anyMatch(l->l.key().equals("timing")));
    }
    @Test void simpleInfoLeadsWithItsExitAndResultNotDisabledChecksOrGenericTutorials() {
        var graph = parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"}}],
             "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """);
        var lines = SettlementDescription.from(graph).paragraphs();
        var keys = lines.stream().map(SettlementDescription.Line::key).toList();
        int result = keys.indexOf("output.plain");
        assertTrue(result >= 0 && result < 5, "A simple result must be visible on the first native Info screen");
        assertTrue(keys.indexOf("finish") < result);
        assertTrue(keys.indexOf("info.graph") > result, "Technical identity belongs after the recipe");
        for (String redundant : List.of("overview", "entry.any", "marks.disabled", "checkpoints.disabled", "lenient", "tree.semantics"))
            assertFalse(keys.contains(redundant), redundant);
        assertTrue(keys.contains("requirements.none"));
        assertTrue(keys.contains("failure.empty"));
    }
    @Test void unorderedRequirementsAreASetNotAnOrderedPathOrVisitCount() {
        var lines = SettlementDescription.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"wash","checkpoint":true,"recipe":{"type":"create:pressing"}},
                      {"id":"f","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"requirements":{"checkpoints":{"mode":"unordered","nodes":["wash","wash","f"]}},
                         "results":[{"item":"minecraft:diamond"}]}]}
            """)).paragraphs();
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("checkpoints.sequence")
            && p.arguments().equals(List.of("wash, f"))));
    }
    @Test void conditionsPrecedeSharedExplanationAndWeightsAreExplainedOnlyOnce() {
        var document = SettlementDescription.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"requirements":{"marks":{"quality":2}},"results":[{"item":"minecraft:diamond"}]},
                        {"results":[{"item":"minecraft:emerald"}]}]}
            """));
        var keys = document.paragraphs().stream().map(SettlementDescription.Line::key).toList();
        assertTrue(keys.indexOf("source.graph") < keys.indexOf("merge"));
        assertEquals(1, keys.stream().filter("weight_scope"::equals).count());
        assertFalse(keys.contains("requirements.defaults"), "Resolved conditions do not need a configuration tutorial");
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("marks.exact")
            && p.arguments().equals(List.of("quality × 2"))));
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("finish")
            && p.arguments().equals(List.of("f"))));
    }
    @Test void recursiveOutputInfoRetainsGroupModesLocalWeightsAndBothChanceSemantics() {
        var document = SettlementDescription.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"},
               "scrap":[{"mode":"all","results":[{"item":"minecraft:coal"}]}]}],"edges":[],
             "outputs":[{"results":[{"mode":"all","chance":0.5,"results":[
               {"item":"minecraft:diamond","count":3,"chance":0.25},
               {"mode":"weighted","weight":7,"results":[{"item":"minecraft:emerald"}]}]}]}]}
            """));
        var lines = document.paragraphs();
        assertEquals(2, lines.stream().filter(p -> p.key().startsWith("tree.all")).count());
        assertTrue(lines.stream().anyMatch(p -> p.key().startsWith("tree.weighted")));
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("chance.item") && p.arguments().contains("25%")));
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("chance.group") && p.arguments().contains("50%")));
    }
    @Test void allGroupDoesNotAdvertiseIgnoredChildWeights() {
        var lines = SettlementDescription.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"results":[{"mode":"all","results":[
                {"item":"minecraft:diamond","weight":99},
                {"mode":"weighted","weight":7,"results":[{"item":"minecraft:emerald","weight":3},{"item":"minecraft:coal"}]}]}]}]}
            """)).paragraphs();
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("output.plain")
            && p.arguments().equals(List.of("minecraft:diamond", 1))));
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("tree.weighted.plain")));
        assertTrue(lines.stream().anyMatch(p -> p.key().equals("output")
            && p.arguments().equals(List.of("minecraft:emerald", 1, "3.0"))));
        assertFalse(lines.stream().anyMatch(p -> p.key().equals("tree.path")));
        assertEquals(1, lines.stream().filter(p -> p.key().equals("output.plain")).findFirst().orElseThrow().depth());
        assertEquals(2, lines.stream().filter(p -> p.key().equals("output")).findFirst().orElseThrow().depth());
    }
    @Test void rangesAndInputRolesReachTheInfoDocumentWithoutLosingZeroBounds() {
        var graph=parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:deploying"},
               "workpiece_inputs":[{"item_input":1,"requirements":{"mark_ranges":{"quality":{"min":2}}}}]}],
             "edges":[],"outputs":[{"requirements":{"mark_ranges":{"damage":{"max":0}},"checkpoint_counts":{"s":{"min":1,"max":3}}},
               "results":[{"item":"minecraft:diamond"}]}]}
            """);
        var lines=SettlementDescription.from(graph).paragraphs();
        assertTrue(lines.stream().anyMatch(p->p.key().equals("mark_range") && p.arguments().equals(List.of("damage",0,0))));
        assertTrue(lines.stream().anyMatch(p->p.key().equals("checkpoint_count") && p.arguments().equals(List.of("s",1,3))));
        assertTrue(lines.stream().anyMatch(p->p.key().equals("input.role") && p.arguments().equals(List.of("s",1))));
    }
    @Test void infoAssociatesReachableSuccessAndStrictScrapAndIdentifiesItsGraph() {
        var graph = parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[
               {"id":"s","start":true,"recipe":{"type":"create:pressing"},"outputs":[{"results":[{"item":"minecraft:dirt"}]}]},
               {"id":"f","finish":true,"finish_mode":"strict","recipe":{"type":"create:cutting"},"scrap":[{"item":"minecraft:flint"}]}],
             "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """);
        var document = SettlementDescription.from(graph);
        assertEquals(List.of("minecraft:diamond", "minecraft:flint"), document.outputs().stream().map(WeightedOutput::item).toList(),
            "Only reachable outputs should advertise this description in JEI");
        assertTrue(document.paragraphs().contains(new SettlementDescription.Line("info.graph", List.of(graph.id()))),
            "Different graphs producing the same item must remain distinguishable");
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("finish.none")),
            "Unreachable configured rules must still be explained, not silently deleted");
    }
    private static GraphDefinition parse(String json) {
        return GraphCodec.parse("test:info", JsonParser.parseString(json).getAsJsonObject());
    }
    @Test void fullLongCheckpointSequenceAndOutputComponentsReachTheInfoDocumentUnabridged() {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
             "edges":[],"outputs":[{"start":"s","requirements":{"marks":{},"checkpoints":{"mode":"partial","nodes":[]}},
               "results":[{"item":"minecraft:diamond","count":3,"weight":2.5,"components":{"minecraft:custom_data":{"memo":""}}}]}]}
            """).getAsJsonObject();
        var rule = json.getAsJsonArray("outputs").get(0).getAsJsonObject();
        var visits = rule.getAsJsonObject("requirements").getAsJsonObject("checkpoints").getAsJsonArray("nodes");
        for (int i = 0; i < 600; i++) visits.add("s");
        visits.add("f");
        String memo = "完整说明🧭".repeat(1000) + "END_OF_COMPONENT";
        rule.getAsJsonArray("results").get(0).getAsJsonObject().getAsJsonObject("components")
            .getAsJsonObject("minecraft:custom_data").addProperty("memo", memo);
        var graph = GraphCodec.parse("test:long_info", json);
        var document = SettlementDescription.from(graph);
        var sequence = document.paragraphs().stream().filter(p -> p.key().equals("checkpoints.sequence")).findFirst().orElseThrow();
        assertEquals(String.join(" → ", graph.outputs().getFirst().requirements().checkpoints().nodes()), sequence.arguments().getFirst(),
            "Player-facing paths retain every visit without Java collection brackets");
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("marks.exact.empty")));
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("checkpoints.subsequence")));
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("output") && p.arguments().equals(List.of("minecraft:diamond", 3, "2.5"))));
        assertTrue(document.paragraphs().stream().filter(p -> p.key().equals("components"))
            .anyMatch(p -> p.arguments().getFirst().toString().contains(memo)));
    }
    @Test void noOutputStrictFailureStillHasAReadableDocumentForTheFilterFallback() {
        var graph = parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"}}],
             "edges":[],"outputs":[]}
            """);
        var document = SettlementDescription.from(graph);
        assertTrue(document.outputs().isEmpty());
        assertEquals("info.title", document.paragraphs().getFirst().key());
        assertTrue(document.paragraphs().stream().anyMatch(p -> p.key().equals("failure.empty")));
    }
}
