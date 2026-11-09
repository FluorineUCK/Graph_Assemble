package dev.graphassembly.client.view;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.condition.mark.MarkRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettlementViewModelTest {
    @Test void preservesEffectiveRequirementsWithoutConflatingDisabledAndExplicitEmpty() {
        var view = SettlementViewModel.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[
               {"results":[{"item":"minecraft:diamond"}]},
               {"requirements":{"marks":{},"checkpoints":[]},"results":[{"item":"minecraft:diamond"}]},
               {"requirements":{"mode":"partial","marks":{},"checkpoints":[]},"results":[{"item":"minecraft:diamond"}]},
               {"requirements":{"mode":"partial","marks":{"mode":"exact","values":{"x":2}},
                 "checkpoints":{"mode":"unordered","nodes":["s"]}},"results":[{"item":"minecraft:diamond"}]}]}
            """));
        var rules = view.rules();
        assertNull(rules.get(0).condition().requirements().marks().match());
        assertNull(rules.get(0).condition().requirements().checkpoints().match());
        assertEquals(List.of(MarkRequirement.Mode.EXACT, MarkRequirement.Mode.PARTIAL, MarkRequirement.Mode.EXACT),
            rules.subList(1, 4).stream().map(rule -> rule.condition().requirements().marks().match().mode()).toList());
        assertEquals(List.of(CheckpointMode.EXACT, CheckpointMode.SUBSEQUENCE, CheckpointMode.UNORDERED),
            rules.subList(1, 4).stream().map(rule -> rule.condition().requirements().checkpoints().match().mode()).toList());
        assertTrue(rules.get(1).condition().requirements().marks().match().values().isEmpty());
        assertTrue(rules.get(1).condition().requirements().checkpoints().match().nodes().isEmpty());
        assertEquals(List.of("s"), rules.get(0).effectiveFinishes(), "Unconditional still has a reachable finish and result");
        assertEquals("minecraft:diamond", rules.get(0).condition().leaves().getFirst().item());
    }
    @Test void keepsGraphAndNodeRulesSeparateWithEffectiveFinishTargets() {
        var graph = parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[
               {"id":"s","start":true,"recipe":{"type":"create:pressing"}},
               {"id":"f1","finish":true,"recipe":{"type":"create:cutting"},"outputs":[
                 {"start":"s","requirements":{"marks":{"worked":2},"checkpoints":["s","s","f1"]},
                  "results":[{"item":"minecraft:gold_ingot","weight":3}]}]},
               {"id":"f2","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[],"outputs":[{"requirements":{"marks":{}},"results":[{"item":"minecraft:diamond"}]}]}
            """);

        var view = SettlementViewModel.from(graph);

        assertEquals(2, view.rules().size(), "Graph and node pools must remain distinct, conditional rules");
        var global = view.rules().get(0);
        assertNull(global.sourceNode());
        assertEquals(List.of("f1", "f2"), global.effectiveFinishes());
        assertEquals(Map.of(), global.condition().requirements().marks().match().values(), "Empty marks mean EXACTLY no marks, not unrestricted");
        assertNull(global.condition().requirements().checkpoints().match(), "Omitted checkpoints must remain visibly unrestricted");
        var local = view.rules().get(1);
        assertEquals("f1", local.sourceNode());
        assertEquals(List.of("f1"), local.effectiveFinishes());
        assertEquals("s", local.condition().start());
        assertEquals(Map.of("worked", 2), local.condition().requirements().marks().match().values());
        assertEquals(List.of("s", "s", "f1"), local.condition().requirements().checkpoints().match().nodes());
    }

    @Test void exposesOnlyReachableStrictFailurePoolsIncludingNoOutputConsumption() {
        var graph = parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[
               {"id":"s","start":true,"recipe":{"type":"create:pressing"},"finish_mode":"strict",
                "scrap":[{"item":"minecraft:coal"}]},
               {"id":"lenient","finish":true,"recipe":{"type":"create:cutting"},
                "scrap":[{"item":"minecraft:stone"}]},
               {"id":"strict","finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"},
                "scrap":[{"item":"minecraft:flint","count":2,"weight":3},{"item":"minecraft:gravel","weight":1}]},
               {"id":"consume","finish":true,"finish_mode":"strict","recipe":{"type":"create:cutting"}}],
             "edges":[],"outputs":[]}
            """);

        var view = SettlementViewModel.from(graph);

        assertEquals(List.of("strict", "consume"), view.failures().stream().map(SettlementViewModel.Failure::finish).toList());
        assertEquals(List.of(3.0, 1.0), view.failures().getFirst().scrap().stream().map(output -> output.weight()).toList());
        assertEquals(2, view.failures().getFirst().scrap().getFirst().count());
        assertTrue(view.failures().getLast().scrap().isEmpty(), "An empty strict pool must still have a visible no-output failure card");
    }

    @Test void finishRestrictionsDoNotMakeNodeRulesReachOtherFinishes() {
        var view = SettlementViewModel.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[
               {"id":"s","start":true,"recipe":{"type":"create:pressing"},
                "outputs":[{"results":[{"item":"minecraft:coal"}]}]},
               {"id":"f1","finish":true,"recipe":{"type":"create:cutting"},
                "outputs":[{"finish":"f2","results":[{"item":"minecraft:gold_ingot"}]}]},
               {"id":"f2","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[],"outputs":[{"finish":"f2","results":[{"item":"minecraft:diamond"}]}]}
            """));

        assertEquals(List.of("f2"), view.rules().getFirst().effectiveFinishes());
        assertEquals("s", view.rules().get(1).sourceNode());
        assertTrue(view.rules().get(1).effectiveFinishes().isEmpty(), "Outputs on a non-finish node are not reachable");
        assertTrue(view.rules().get(2).effectiveFinishes().isEmpty(), "A local rule cannot match another finish");
        assertEquals("minecraft:gold_ingot", view.rules().get(2).condition().leaves().getFirst().item(),
            "An unreachable rule must remain inspectable rather than silently disappearing");
    }

    @Test void retainsAllCheckpointModesAndFullLongListsWeightsAndComponents() {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
             "edges":[],"outputs":[]}
            """).getAsJsonObject();
        String longText = "保存完整组件数据🧭".repeat(1_000);
        var checkpoints = new JsonArray();
        for (int visit = 0; visit < 600; visit++) checkpoints.add("s");
        checkpoints.add("f");
        for (CheckpointMode mode : CheckpointMode.values()) {
            var rule = new JsonObject();
            var requirement = new JsonObject();
            requirement.addProperty("mode", mode == CheckpointMode.SUBSEQUENCE ? "partial" : mode.name().toLowerCase(java.util.Locale.ROOT));
            requirement.add("nodes", checkpoints.deepCopy());
            var conditions = new JsonObject();
            conditions.add("marks", new JsonObject());
            conditions.add("checkpoints", requirement);
            rule.add("requirements", conditions);
            var results = JsonParser.parseString("""
                [{"item":"minecraft:diamond","count":3,"weight":2.5},
                 {"item":"minecraft:gold_ingot","weight":9.75}]
                """).getAsJsonArray();
            var components = new JsonObject();
            var customData = new JsonObject();
            customData.addProperty("memo", longText);
            components.add("minecraft:custom_data", customData);
            results.get(0).getAsJsonObject().add("components", components);
            rule.add("results", results);
            json.getAsJsonArray("outputs").add(rule);
        }

        var view = SettlementViewModel.from(GraphCodec.parse("test:long_rules", json));

        assertEquals(3, view.rules().size(), "Three checkpoint modes remain three conditional pools");
        for (int index = 0; index < CheckpointMode.values().length; index++) {
            var rule = view.rules().get(index).condition();
            assertEquals(CheckpointMode.values()[index], rule.requirements().checkpoints().match().mode());
            assertEquals(601, rule.requirements().checkpoints().match().nodes().size());
            assertEquals(600, rule.requirements().checkpoints().match().nodes().stream().filter("s"::equals).count());
            assertEquals("f", rule.requirements().checkpoints().match().nodes().getLast());
            assertEquals(2, rule.leaves().size());
            assertEquals(3, rule.leaves().getFirst().count());
            assertEquals(2.5, rule.leaves().getFirst().weight());
            assertEquals(9.75, rule.leaves().getLast().weight());
            assertEquals(longText, rule.leaves().getFirst().components().getAsJsonObject("minecraft:custom_data").get("memo").getAsString());
        }
    }

    private static GraphDefinition<OperationSpec> parse(String json) {
        return GraphCodec.parse("test:settlement_view", JsonParser.parseString(json).getAsJsonObject());
    }
}
