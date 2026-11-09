package dev.graphassembly.client.view;

import dev.graphassembly.core.settlement.FinishMode;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.io.OutputGroup;

import java.util.ArrayList;
import java.util.List;

/** Finish-scoped display data. Candidate weights are provenance, not a combined outcome pool. */
public record SettlementGroups(List<SettlementGroups.Finish> finishes, List<SettlementGroups.Candidate> overview) {
    public SettlementGroups {
        finishes = List.copyOf(finishes);
        overview = List.copyOf(overview);
    }

    public record Finish(String id, FinishMode mode, List<SettlementViewModel.Rule> rules,
                         List<Candidate> success, List<Candidate> scrap) {
        public Finish {
            rules = List.copyOf(rules);
            success = List.copyOf(success);
            scrap = List.copyOf(scrap);
        }
    }

    public record Candidate(WeightedOutput output, List<Source> sources) {
        public Candidate { sources = List.copyOf(sources); }
    }

    /** null ruleSource means graph-level; ruleNumber 0 means the finish's strict scrap pool. */
    public record GroupContext(String path, OutputGroup.Mode mode, double weight, double chance) {}
    public record Source(String finish, String ruleSource, int ruleNumber, double weight,
                         double chance, List<GroupContext> groups) {
        public Source { groups = List.copyOf(groups); }
        public Source(String finish, String ruleSource, int ruleNumber, double weight) {
            this(finish, ruleSource, ruleNumber, weight, 1, List.of());
        }
    }

    public static SettlementGroups from(GraphDefinition<?> graph) {
        var view = SettlementViewModel.from(graph);
        List<Finish> finishes = new ArrayList<>();
        List<Candidate> overview = new ArrayList<>();
        for (var node : graph.nodes().values()) {
            if (!node.roles().finish()) continue;
            var rules = view.rules().stream().filter(rule -> rule.effectiveFinishes().contains(node.id())).toList();
            List<Candidate> success = new ArrayList<>();
            for (var rule : rules) collect(success, rule.condition().entries(), node.id(), rule.sourceNode(), rule.number(), "", List.of());
            success.forEach(candidate -> add(overview, candidate));
            List<Candidate> scrap = new ArrayList<>();
            if (node.settlement().finishMode() == FinishMode.STRICT) collect(scrap, node.settlement().scrapEntries(), node.id(), node.id(), 0, "", List.of());
            finishes.add(new Finish(node.id(), node.settlement().finishMode(), rules, success, scrap));
        }
        return new SettlementGroups(finishes, overview);
    }

    private static void collect(List<Candidate> candidates, List<OutputNode> entries, String finish,
            String source, int number, String parent, List<GroupContext> groups) {
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            String path = parent.isEmpty() ? Integer.toString(i + 1) : parent + "." + (i + 1);
            if (entry instanceof WeightedOutput output)
                add(candidates, new Candidate(output, List.of(new Source(finish, source, number, output.weight(), output.chance(), groups))));
            else {
                var group = (OutputGroup)entry;
                var ancestry = new ArrayList<>(groups);
                ancestry.add(new GroupContext(path, group.mode(), group.weight(), group.chance()));
                collect(candidates, group.children(), finish, source, number, path, ancestry);
            }
        }
    }

    private static void add(List<Candidate> candidates, Candidate addition) {
        var output = addition.output();
        for (int index = 0; index < candidates.size(); index++) {
            var previous = candidates.get(index);
            var existing = previous.output();
            if (!existing.item().equals(output.item()) || existing.count() != output.count() || existing.chance() != output.chance()
                || !existing.components().equals(output.components())) continue;
            List<Source> sources = new ArrayList<>(previous.sources());
            sources.addAll(addition.sources());
            candidates.set(index, new Candidate(existing, sources));
            return;
        }
        candidates.add(addition);
    }
}
