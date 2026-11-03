package dev.graphassembly.client.view;

import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphCodec;
import dev.graphassembly.core.GraphDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettlementGroupsTest {
    @Test void goldExitContainsOnlyItsOwnSuccessAndScrap() throws Exception {
        var script = Files.readString(Path.of("examples/kubejs/three_path_outputs.js"));
        int start = script.indexOf("event.custom(") + "event.custom(".length();
        int end = script.lastIndexOf(").id(");
        var groups = SettlementGroups.from(GraphCodec.parse("example:three_path_outputs",
            JsonParser.parseString(script.substring(start, end)).getAsJsonObject()));
        var gold = groups.finishes().stream().filter(finish -> finish.id().equals("finish_gold")).findFirst().orElseThrow();
        assertEquals(List.of("minecraft:gold_ingot"), gold.success().stream().map(candidate -> candidate.output().item()).toList());
        assertEquals(List.of("minecraft:gold_nugget", "minecraft:redstone"), gold.scrap().stream().map(candidate -> candidate.output().item()).toList());
        assertEquals(List.of(2, 1), gold.scrap().stream().map(candidate -> candidate.output().count()).toList());
    }

    @Test void sharedIconsKeepEveryRuleAndFinishWeightWithoutCombiningPools() {
        var groups = SettlementGroups.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f1","start":true,"finish":true,"recipe":{"type":"create:pressing"},
                "outputs":[{"results":[{"item":"minecraft:diamond","weight":7}]}]},
                {"id":"f2","finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"results":[{"item":"minecraft:diamond","weight":2}]},
                {"finish":"f1","results":[{"item":"minecraft:diamond","weight":5}]}]}
            """));
        assertEquals(1, groups.overview().size());
        var candidate = groups.overview().getFirst();
        assertEquals(List.of(new SettlementGroups.Source("f1", null, 1, 2),
            new SettlementGroups.Source("f1", null, 2, 5),
            new SettlementGroups.Source("f1", "f1", 1, 7),
            new SettlementGroups.Source("f2", null, 1, 2)), candidate.sources());
        assertEquals(2, candidate.output().weight(), "Representative output retains its original weight, never a sum");
        assertEquals(1, groups.finishes().getFirst().success().size());
        assertEquals(3, groups.finishes().getFirst().success().getFirst().sources().size());
        assertThrows(UnsupportedOperationException.class, () -> candidate.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> groups.finishes().clear());
    }

    @Test void countAndComponentsStayDistinctAndReturnedComponentsCannotMutateTheModel() {
        var groups = SettlementGroups.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"results":[{"item":"minecraft:diamond"},{"item":"minecraft:diamond","count":2},
                 {"item":"minecraft:diamond","components":{"minecraft:custom_data":{"variant":1}}},
                 {"item":"minecraft:diamond","components":{"minecraft:custom_data":{"variant":2}}}]}]}
            """));
        assertEquals(4, groups.overview().size());
        var output = groups.overview().get(2).output();
        output.components().addProperty("mutated", true);
        assertFalse(output.components().has("mutated"));
    }

    @Test void invalidRestrictionsCannotLeakAndFinishOrderAndEmptyModesRemainVisible() {
        var groups = SettlementGroups.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},
                 "outputs":[{"results":[{"item":"minecraft:coal"}]}]},
               {"id":"z","finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing"},
                 "outputs":[{"finish":"a","results":[{"item":"minecraft:diamond"}]}]},
               {"id":"a","finish":true,"recipe":{"type":"create:pressing"},"scrap":[{"item":"minecraft:stone"}]}],
             "edges":[],"outputs":[]}
            """));
        assertEquals(List.of("z", "a"), groups.finishes().stream().map(SettlementGroups.Finish::id).toList());
        assertTrue(groups.overview().isEmpty());
        assertTrue(groups.finishes().stream().allMatch(f -> f.rules().isEmpty() && f.success().isEmpty() && f.scrap().isEmpty()));
        assertEquals(dev.graphassembly.core.FinishMode.STRICT, groups.finishes().getFirst().mode());
        assertEquals(dev.graphassembly.core.FinishMode.LENIENT, groups.finishes().getLast().mode());
        var noFinishes = SettlementGroups.from(parse("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}}],"edges":[],"outputs":[]}
            """));
        assertTrue(noFinishes.finishes().isEmpty());
        assertTrue(noFinishes.overview().isEmpty());
    }

    private static GraphDefinition parse(String json) {
        return GraphCodec.parse("test:groups", JsonParser.parseString(json).getAsJsonObject());
    }
}
