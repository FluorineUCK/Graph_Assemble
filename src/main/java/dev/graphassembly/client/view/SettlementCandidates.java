package dev.graphassembly.client.view;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Display-only quantity grouping; raw settlement candidates and their pools remain unchanged. */
public final class SettlementCandidates {
    private SettlementCandidates() {}

    public record QuantitySource(SettlementGroups.Source source, String quantities) {}
    public record Group(List<SettlementGroups.Candidate> variants, String quantityLabel, List<QuantitySource> sources) {
        public Group { variants = List.copyOf(variants); sources = List.copyOf(sources); }
    }
    private record Identity(String item, JsonObject components) {}
    private record Range(int min, int max) {}

    public static List<Group> group(List<SettlementGroups.Candidate> candidates) {
        Map<Identity, List<SettlementGroups.Candidate>> grouped = new LinkedHashMap<>();
        for (var candidate : candidates) {
            var output = candidate.output();
            grouped.computeIfAbsent(new Identity(output.item(), output.components()), ignored -> new ArrayList<>()).add(candidate);
        }
        List<Group> result = new ArrayList<>();
        for (var variants : grouped.values()) {
            Map<SettlementGroups.Source, List<Range>> bySource = new LinkedHashMap<>();
            List<Range> all = new ArrayList<>();
            for (var variant : variants) for (var source : variant.sources())
                bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).addAll(ranges(variant, source));
            bySource.values().forEach(all::addAll);
            result.add(new Group(variants, compress(all),
                bySource.entrySet().stream().map(entry -> new QuantitySource(entry.getKey(), compress(entry.getValue()))).toList()));
        }
        return List.copyOf(result);
    }

    /** Compress only consecutive integers; sparse outcomes must never imply missing quantities. */
    public static String quantities(Collection<Integer> counts) {
        return compress(counts.stream().map(count -> new Range(count, count)).toList());
    }

    private static List<Range> ranges(SettlementGroups.Candidate candidate, SettlementGroups.Source source) {
        var item = candidate.output();
        if (item.chance() == 0 || source.groups().stream().anyMatch(group -> group.chance() == 0)) return List.of(new Range(0, 0));
        if (item.chance() < 1) return List.of(new Range(0, item.count()));
        var count = new Range(item.count(), item.count());
        return source.groups().stream().anyMatch(group -> group.chance() < 1) ? List.of(new Range(0, 0), count) : List.of(count);
    }

    private static String compress(Collection<Range> values) {
        var sorted = values.stream().sorted(java.util.Comparator.comparingInt(Range::min)).toList();
        List<String> ranges = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int first = sorted.get(i).min(), last = sorted.get(i).max();
            while (i + 1 < sorted.size() && (long) sorted.get(i + 1).min() <= (long) last + 1) last = Math.max(last, sorted.get(++i).max());
            ranges.add(first == last ? Integer.toString(first) : first + "-" + last);
        }
        return String.join(",", ranges);
    }
}
