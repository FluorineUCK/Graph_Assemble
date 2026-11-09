package dev.graphassembly.client.view;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.core.condition.mark.MarkRequirement;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.io.OutputGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Complete localized description data, independent of a custom screen or Minecraft client. */
public record SettlementDescription(List<WeightedOutput> outputs, List<Line> paragraphs) {
    public SettlementDescription { outputs = List.copyOf(outputs); paragraphs = List.copyOf(paragraphs); }
    public record Line(String key, List<Object> arguments, int depth) {
        public Line { arguments = List.copyOf(arguments); }
        public Line(String key, List<Object> arguments) { this(key, arguments, 0); }
    }
    private static Line text(String key, Object... arguments) { return new Line(key, List.of(arguments)); }

    public static SettlementDescription from(GraphDefinition<?> graph) {
        var view = SettlementViewModel.from(graph);
        List<Line> lines = new ArrayList<>();
        lines.add(text("info.title"));
        for (var node : graph.nodes().values()) {
            if (!node.roles().finish()) continue;
            section(lines, text("finish", node.id()));
            var rules = view.rules().stream().filter(rule -> rule.effectiveFinishes().contains(node.id())).toList();
            for (var rule : rules) addRule(lines, rule, rules.size() > 1);
            if (rules.isEmpty()) lines.add(text("success.none"));
            if (node.settlement().finishMode() == dev.graphassembly.core.settlement.FinishMode.STRICT)
                addFailure(lines, node.settlement().scrapEntries());
            else lines.add(text("lenient"));
        }
        var unused = view.rules().stream().filter(rule -> rule.effectiveFinishes().isEmpty()).toList();
        if (!unused.isEmpty()) {
            section(lines, text("finish.none"));
            unused.forEach(rule -> addRule(lines, rule, true));
        }
        if (graph.nodes().values().stream().anyMatch(node -> !node.inputs().workpieceInputs().isEmpty())) {
            section(lines, text("info.inputs"));
            graph.nodes().values().forEach(node -> inputs(node).stream()
                .filter(line -> !line.key().equals("input.primary_only")).forEach(lines::add));
            lines.add(text("input.primary_only"));
        }
        var conditionalEdges=graph.topology().edges().stream().filter(edge->!edge.requirements().equals(ProgressRequirements.NONE)).toList();
        if(!conditionalEdges.isEmpty()) {
            section(lines,text("edges.title"));
            conditionalEdges.forEach(edge->{
                lines.add(text("edge.requirements",edge.from(),edge.to()));
                lines.addAll(edgeConditions(edge));
            });
            lines.add(text("edges.timing"));
        }
        boolean hasActions=graph.nodes().values().stream().anyMatch(node->!node.effects().mutations().isEmpty() || node.routing().redirect()!=null);
        if(hasActions) {
            section(lines,text("actions.title"));
            graph.nodes().values().forEach(node->lines.addAll(ProgressActionDescription.of(node)));
        }
        section(lines, text("info.notes"));
        lines.add(text(hasActions ? "actions.timing" : "timing"));
        lines.add(text("requirements.and"));
        if (view.rules().stream().anyMatch(rule -> hasRanges(rule.condition().requirements()))
                || graph.nodes().values().stream().flatMap(node -> node.inputs().workpieceInputs().stream())
                    .anyMatch(role -> hasRanges(role.requirements()))
                || graph.topology().edges().stream().anyMatch(edge->hasRanges(edge.requirements()))) lines.add(text("requirements.ranges"));
        if (graph.nodes().values().stream().filter(node -> node.roles().finish()).anyMatch(node ->
                view.rules().stream().filter(rule -> rule.effectiveFinishes().contains(node.id())).count() > 1))
            lines.add(text("merge"));
        if (view.rules().stream().anyMatch(rule -> weighted(rule.condition().entries()))
                || graph.nodes().values().stream().filter(node -> node.roles().finish()).anyMatch(node -> weighted(node.settlement().scrapEntries()))
                || lines.stream().anyMatch(line -> line.key().equals("merge"))) lines.add(text("weight_scope"));
        lines.add(text("info.graph", graph.id()));
        var outputs = new ArrayList<WeightedOutput>();
        view.rules().stream().filter(rule -> !rule.effectiveFinishes().isEmpty())
            .forEach(rule -> outputs.addAll(rule.condition().leaves()));
        view.failures().forEach(failure -> outputs.addAll(failure.scrap()));
        return new SettlementDescription(outputs, lines);
    }
    private static void section(List<Line> lines, Line heading) {
        if (lines.size() > 1) lines.add(text("info.gap"));
        lines.add(heading);
    }
    private static boolean weighted(List<OutputNode> entries) {
        return entries.size() > 1 || entries.stream().anyMatch(entry -> entry instanceof OutputGroup || entry.weight() != 1);
    }
    private static boolean hasRanges(ProgressRequirements requirements) {
        return !requirements.marks().ranges().isEmpty() || !requirements.checkpoints().counts().isEmpty();
    }
    private static void addRule(List<Line> lines, SettlementViewModel.Rule rule, boolean showSource) {
        if (showSource) lines.add(rule.sourceNode() == null ? text("source.graph", rule.number())
            : text("source.node", rule.sourceNode(), rule.number()));
        var condition = rule.condition();
        lines.add(text(condition.entries().size() > 1 || showSource ? "rule_pool.weighted" : "rule_pool"));
        addTree(lines, condition.entries(), "", 0, true, showSource);
        List<Line> requirements = new ArrayList<>();
        if (condition.start() != null) requirements.add(text("entry", condition.start()));
        addRequirements(requirements, condition.requirements());
        lines.add(text(requirements.isEmpty() ? "requirements.none" : "requirements.heading"));
        lines.addAll(requirements);
    }
    private static void addRequirements(List<Line> lines, ProgressRequirements requirements) {
        var marks = requirements.marks().match();
        if (marks != null) {
            if (!marks.values().isEmpty()) lines.add(text("marks." + marks.mode().name().toLowerCase(Locale.ROOT), markValues(marks.values())));
            else if (marks.mode() == MarkRequirement.Mode.EXACT) lines.add(text("marks.exact.empty"));
        }
        var checkpoints = requirements.checkpoints().match();
        if (checkpoints != null && (!checkpoints.nodes().isEmpty()
                || checkpoints.mode() == CheckpointMode.EXACT)) {
            String mode = checkpoints.mode().name().toLowerCase(Locale.ROOT);
            lines.add(text("checkpoints." + mode + (checkpoints.nodes().isEmpty() ? ".empty" : "")));
            if (!checkpoints.nodes().isEmpty()) {
                boolean unordered = checkpoints.mode() == CheckpointMode.UNORDERED;
                var nodes = unordered ? checkpoints.nodes().stream().distinct().toList() : checkpoints.nodes();
                lines.add(text("checkpoints.sequence", String.join(unordered ? ", " : " → ", nodes)));
            }
        }
        new java.util.TreeMap<>(requirements.marks().ranges()).forEach((key, range) ->
            lines.add(text("mark_range", key, range.min(), upper(range.max()))));
        new java.util.TreeMap<>(requirements.checkpoints().counts()).forEach((key, range) ->
            lines.add(text("checkpoint_count", key, range.min(), upper(range.max()))));
    }
    private static Object upper(int maximum) { return maximum == Integer.MAX_VALUE ? "∞" : maximum; }
    public static String markValues(java.util.Map<String, Integer> values) {
        return new java.util.TreeMap<>(values).entrySet().stream()
            .map(entry -> entry.getKey() + " × " + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
    }
    public static List<Line> edgeConditions(dev.graphassembly.core.definition.basic.Edge edge) {
        var lines=new ArrayList<Line>();
        addRequirements(lines,edge.requirements());
        return List.copyOf(lines);
    }
    public static List<Line> inputs(NodeDefinition<?> node) {
        List<Line> lines = new ArrayList<>();
        for (var role : node.inputs().workpieceInputs()) {
            lines.add(text("input.role", node.id(), role.itemInput()));
            if (!role.currentNodes().isEmpty()) lines.add(text("input.current", String.join(", ", role.currentNodes())));
            addRequirements(lines, role.requirements());
        }
        if (!lines.isEmpty()) lines.add(text("input.primary_only"));
        return List.copyOf(lines);
    }
    private static void addFailure(List<Line> lines, List<OutputNode> entries) {
        if (entries.isEmpty()) lines.add(text("failure.empty"));
        else {
            lines.add(text("failure.pool"));
            addTree(lines, entries, "", 0, true, false);
        }
    }
    private static void addOutput(List<Line> lines, WeightedOutput output, int depth, boolean showWeight) {
        lines.add(atDepth(depth, showWeight ? text("output", output.item(), output.count(), Double.toString(output.weight()))
            : text("output.plain", output.item(), output.count())));
        if (output.chance() != 1) lines.add(atDepth(depth, text("chance.item", percent(output.chance()), output.count())));
        if (!output.components().isEmpty()) lines.add(atDepth(depth, text("components", output.components().toString())));
    }
    private static Line atDepth(int depth, Line line) { return new Line(line.key(), line.arguments(), depth); }

    public static String percent(double value) {
        return java.math.BigDecimal.valueOf(value).movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
    private static void addTree(List<Line> lines, List<OutputNode> entries, String parent, int depth,
                                boolean weightedParent, boolean otherRules) {
        for (int i = 0; i < entries.size(); i++) {
            String path = parent.isEmpty() ? Integer.toString(i + 1) : parent + "." + (i + 1);
            var entry = entries.get(i);
            boolean showWeight = weightedParent && (entries.size() > 1 || otherRules || entry.weight() != 1);
            if (entry instanceof WeightedOutput output) {
                addOutput(lines, output, depth, showWeight);
            } else {
                var group = (OutputGroup)entry;
                String key = "tree." + group.mode().name().toLowerCase(Locale.ROOT);
                lines.add(atDepth(depth, showWeight ? text(key, path, group.weight()) : text(key + ".plain", path)));
                if (group.chance() != 1) lines.add(atDepth(depth, text("chance.group", percent(group.chance()))));
                if (group.children().isEmpty()) lines.add(atDepth(depth + 1, text("tree.empty")));
                addTree(lines, group.children(), path, depth + 1, group.mode() == OutputGroup.Mode.WEIGHTED, false);
            }
        }
    }
}
