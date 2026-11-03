package dev.graphassembly.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Evaluation preserves every local choice and gate; flattening is only for ingredient indexing. */
public final class OutputTrees {
    private OutputTrees() {}
    public static List<WeightedOutput> leaves(List<OutputNode> nodes) {
        List<WeightedOutput> result = new ArrayList<>();
        for (var node : nodes) {
            if (node instanceof WeightedOutput item) result.add(item);
            else result.addAll(leaves(((OutputGroup)node).results()));
        }
        return List.copyOf(result);
    }
    public static List<WeightedOutput> roll(List<OutputNode> pool, DoubleSupplier random) {
        if (pool.isEmpty()) return List.of();
        List<WeightedOutput> result = new ArrayList<>();
        evaluate(GraphEngine.choose(pool, OutputNode::weight, random), random, result);
        return List.copyOf(result);
    }
    private static void evaluate(OutputNode node, DoubleSupplier random, List<WeightedOutput> result) {
        if (node instanceof WeightedOutput item) {
            int count = item.count();
            if (item.chance() == 0) return;
            if (item.chance() < 1) {
                count = 0;
                for (int i = 0; i < item.count(); i++) if (passes(item.chance(), random)) count++;
            }
            if (count > 0) result.add(new WeightedOutput(item.item(), count, item.weight(), item.components()));
        } else {
            var group = (OutputGroup)node;
            if (!passes(group.chance(), random)) return;
            if (group.mode() == OutputGroup.Mode.ALL) {
                for (var child : group.results()) evaluate(child, random, result);
            } else evaluate(GraphEngine.choose(group.results(), OutputNode::weight, random), random, result);
        }
    }
    private static boolean passes(double chance, DoubleSupplier random) {
        if (chance == 0) return false;
        if (chance == 1) return true;
        double draw = random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0 || draw >= 1) throw new IllegalArgumentException("Random value must be in [0, 1)");
        return draw <= chance; // Create ProcessingOutput rolls each item and rejects draws greater than chance.
    }

    /** A chance can remove items, never add them; reserve maximum quantities before drawing RNG. */
    public static int maxStacks(List<OutputNode> pool) {
        return pool.stream().mapToInt(OutputTrees::maxStacks).max().orElse(0);
    }
    private static int maxStacks(OutputNode node) {
        if (node.chance() == 0) return 0;
        if (node instanceof WeightedOutput) return 1;
        var group = (OutputGroup)node;
        return group.mode() == OutputGroup.Mode.WEIGHTED ? maxStacks(group.results())
            : group.results().stream().mapToInt(OutputTrees::maxStacks).reduce(0, Math::addExact);
    }

    /** Lazily visit compatible maximum bundles, not a flattened union or an exponential stored list. */
    public static boolean allMaximalOutcomesFit(List<OutputNode> pool, java.util.function.Predicate<List<WeightedOutput>> fits) {
        if (pool.isEmpty()) return fits.test(List.of());
        var items = new ArrayList<WeightedOutput>();
        for (var root : pool) if (!visit(root, items, () -> fits.test(List.copyOf(items)))) return false;
        return true;
    }
    private static boolean visit(OutputNode node, List<WeightedOutput> items, java.util.function.BooleanSupplier next) {
        if (node.chance() == 0) return next.getAsBoolean();
        if (node instanceof WeightedOutput item) {
            items.add(item);
            try { return next.getAsBoolean(); }
            finally { items.removeLast(); }
        }
        var group = (OutputGroup)node;
        if (group.mode() == OutputGroup.Mode.ALL) return visitAll(group.results(), 0, items, next);
        for (var child : group.results()) if (!visit(child, items, next)) return false;
        return true;
    }
    private static boolean visitAll(List<OutputNode> nodes, int index, List<WeightedOutput> items, java.util.function.BooleanSupplier next) {
        if (index == nodes.size()) return next.getAsBoolean();
        return visit(nodes.get(index), items, () -> visitAll(nodes, index + 1, items, next));
    }
}
