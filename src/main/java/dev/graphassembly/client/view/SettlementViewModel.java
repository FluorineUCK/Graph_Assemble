package dev.graphassembly.client.view;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.settlement.FinishMode;
import dev.graphassembly.core.settlement.OutputRule;
import dev.graphassembly.core.io.WeightedOutput;

import java.util.ArrayList;
import java.util.List;

/** Minecraft-independent presentation of independent success rules and strict-failure pools. */
public record SettlementViewModel(List<Rule> rules, List<Failure> failures) {
    public SettlementViewModel {
        rules = List.copyOf(rules);
        failures = List.copyOf(failures);
    }

    public record Rule(String sourceNode, int number, List<String> effectiveFinishes, OutputRule condition) {
        public Rule { effectiveFinishes = List.copyOf(effectiveFinishes); }
    }

    public record Failure(String finish, List<WeightedOutput> scrap) {
        public Failure { scrap = List.copyOf(scrap); }
    }

    public static SettlementViewModel from(GraphDefinition<?> graph) {
        List<Rule> rules = new ArrayList<>();
        var finishes = graph.nodes().values().stream().filter(node -> node.roles().finish()).map(node -> node.id()).toList();
        addRules(rules, null, finishes, graph.outputRules());
        graph.nodes().values().forEach(node -> addRules(rules, node.id(), node.roles().finish() ? List.of(node.id()) : List.of(), node.settlement().outputRules()));
        var failures = graph.nodes().values().stream()
            .filter(node -> node.roles().finish() && node.settlement().finishMode() == FinishMode.STRICT)
            .map(node -> new Failure(node.id(), node.settlement().scrapLeaves())).toList();
        return new SettlementViewModel(rules, failures);
    }

    private static void addRules(List<Rule> destination, String sourceNode, List<String> finishes, List<OutputRule> conditions) {
        for (int index = 0; index < conditions.size(); index++) {
            var condition = conditions.get(index);
            var effective = finishes.stream().filter(id -> condition.finish() == null || condition.finish().equals(id)).toList();
            destination.add(new Rule(sourceNode, index + 1, effective, condition));
        }
    }
}
