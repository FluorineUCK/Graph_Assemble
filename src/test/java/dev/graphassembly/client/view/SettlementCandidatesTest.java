package dev.graphassembly.client.view;

import com.google.gson.JsonObject;
import dev.graphassembly.core.WeightedOutput;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettlementCandidatesTest {
    @Test void perItemChanceUsesRangeButGroupChanceDoesNotInventIntermediateCounts() {
        var graph = dev.graphassembly.core.GraphCodec.parse("test:chance", com.google.gson.JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"f","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
             "outputs":[{"results":[{"item":"minecraft:diamond","count":64,"chance":0.5},
               {"mode":"all","chance":0.5,"results":[{"item":"minecraft:emerald","count":9}]}]}]}
            """).getAsJsonObject());
        var groups = SettlementCandidates.group(SettlementGroups.from(graph).overview());
        assertEquals(List.of("0-64", "0,9"), groups.stream().map(SettlementCandidates.Group::quantityLabel).toList());
    }
    @Test void quantityStressFixtureBecomesThreePreviewsWithoutLosingOutcomes() {
        var candidates = new ArrayList<SettlementGroups.Candidate>();
        for (int count = 1; count <= 64; count++) {
            candidates.add(candidate("iron_ingot", count, "exit_1", 1));
            candidates.add(candidate("gold_ingot", count, "exit_1", 1));
        }
        for (int count = 2; count <= 9; count++) candidates.add(candidate("diamond", count, "exit_" + count, 1));
        var grouped = SettlementCandidates.group(candidates);
        assertEquals(3, grouped.size());
        assertEquals(List.of("1-64", "1-64", "2-9"), grouped.stream().map(SettlementCandidates.Group::quantityLabel).toList());
        assertEquals(136, grouped.stream().mapToInt(group -> group.variants().size()).sum());
        assertEquals(1, grouped.getFirst().sources().size(), "One source can describe a continuous quantity range");
        assertEquals("1-64", grouped.getFirst().sources().getFirst().quantities());
        assertEquals(8, grouped.get(2).sources().size(), "Different exits must not lose their quantity mapping");
    }

    private static SettlementGroups.Candidate candidate(String item, int count, String finish, double weight) {
        return new SettlementGroups.Candidate(new WeightedOutput("minecraft:" + item, count, weight, new JsonObject()),
            List.of(new SettlementGroups.Source(finish, finish, 1, weight)));
    }

    @Test void sparseQuantitiesStayExplicitIncludingTheLargestInteger() {
        assertEquals("1,4,9", SettlementCandidates.quantities(List.of(9, 1, 4, 4)));
        assertEquals("1-3,7,9-10", SettlementCandidates.quantities(List.of(10, 9, 7, 3, 2, 1)));
        assertEquals("2147483646-2147483647", SettlementCandidates.quantities(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE - 1)));
    }

    @Test void groupingKeepsComponentsDistinctAndPreservesQuantitySpecificWeightsAndExits() {
        var a = candidate("iron_ingot", 1, "a", 2);
        var b = candidate("iron_ingot", 4, "a", 7);
        var c = candidate("iron_ingot", 9, "b", 2);
        var tagged = new JsonObject(); tagged.addProperty("custom", "different");
        var d = new SettlementGroups.Candidate(new WeightedOutput("minecraft:iron_ingot", 1, 3, tagged), a.sources());
        var raw = List.of(a, b, c, d);
        var groups = SettlementCandidates.group(raw);
        assertEquals(2, groups.size());
        assertEquals("1,4,9", groups.getFirst().quantityLabel());
        assertEquals(List.of(a, b, c), groups.getFirst().variants());
        assertEquals(List.of("1", "4", "9"), groups.getFirst().sources().stream().map(SettlementCandidates.QuantitySource::quantities).toList());
        assertEquals(List.of(2.0, 7.0, 2.0), groups.getFirst().sources().stream().map(source -> source.source().weight()).toList());
        assertEquals(4, raw.size());
        assertThrows(UnsupportedOperationException.class, () -> groups.getFirst().variants().clear());
    }
}
