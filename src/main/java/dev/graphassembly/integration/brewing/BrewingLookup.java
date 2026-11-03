package dev.graphassembly.integration.brewing;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.integration.GraphOperation;
import dev.graphassembly.integration.WorkpieceInputs;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** A read-only round proposal. Callers must validate their live transaction separately. */
public final class BrewingLookup {
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final Lookup PASS = new Lookup(false, null, List.of());
    private static final Lookup REJECT = new Lookup(true, null, List.of());

    private BrewingLookup() {}

    public record Lookup(boolean handled, BrewingRecipe.Primary primary, List<Bottle> bottles) {
        public Lookup { bottles = List.copyOf(bottles); }
    }

    public record Bottle(int slot, GraphOperation operation, ItemStack nativeResult) {
        public Bottle { nativeResult = nativeResult.copy(); }
        @Override public ItemStack nativeResult() { return nativeResult.copy(); }
    }

    private record Candidate(BrewingRecipe.Primary mode, GraphOperation operation) {}

    /** Input is the physical five-slot inventory. No live stack is passed to recipe callbacks. */
    public static Lookup find(Level level, List<ItemStack> inputs) {
        if (inputs.size() != FUEL_SLOT + 1) throw new IllegalArgumentException("Expected five brewing slots");
        try {
            return select(level, inputs.stream().map(ItemStack::copy).toList());
        } catch (RuntimeException invalidPreparation) {
            return REJECT;
        }
    }

    private static Lookup select(Level level, List<ItemStack> inputs) {
        var material = inputs.get(INGREDIENT_SLOT);
        boolean trackedMaterial = Workpiece.has(material);
        if (Workpiece.has(inputs.get(FUEL_SLOT)) || trackedMaterial && material.getCount() != 1) return REJECT;
        if (material.isEmpty())
            return inputs.subList(0, INGREDIENT_SLOT).stream().anyMatch(Workpiece::has) ? REJECT : PASS;

        var index = GraphIndex.get(level);
        var brewing = level.potionBrewing();
        var materialEntries = entries(level, index, material);
        BrewingRecipe.Primary mode = null;
        var result = new ArrayList<Bottle>();
        var unclaimed = new ArrayList<Integer>();
        for (int slot = 0; slot < INGREDIENT_SLOT; slot++) {
            var bottle = inputs.get(slot);
            if (bottle.isEmpty()) continue;
            var candidates = new ArrayList<Candidate>();
            collect(level, entries(level, index, bottle), BrewingRecipe.Primary.BOTTLE, bottle, material, candidates);
            collect(level, materialEntries, BrewingRecipe.Primary.INGREDIENT, material, bottle, candidates);
            var nativeResult = candidates.isEmpty() ? ItemStack.EMPTY : mix(brewing, bottle, material);
            if (nativeResult.isEmpty()) {
                if (Workpiece.has(bottle)) return REJECT;
                unclaimed.add(slot);
                continue;
            }
            if (bottle.getCount() != 1 || candidates.size() != 1) return REJECT;
            var candidate = candidates.getFirst();
            if (mode != null && mode != candidate.mode()) return REJECT;
            mode = candidate.mode();
            result.add(new Bottle(slot, candidate.operation(), nativeResult));
        }
        if (mode == null) return trackedMaterial ? REJECT : PASS;

        // Only bottle-primary plans with ordinary material may also brew unclaimed ordinary bottles.
        if (mode == BrewingRecipe.Primary.BOTTLE && !trackedMaterial) {
            for (int slot : unclaimed) {
                var mixed = mix(brewing, inputs.get(slot), material);
                if (!mixed.isEmpty()) {
                    if (inputs.get(slot).getCount() != 1) return REJECT;
                    result.add(new Bottle(slot, null, mixed));
                }
            }
            result.sort(java.util.Comparator.comparingInt(Bottle::slot));
        }
        return new Lookup(true, mode, result);
    }

    private static List<GraphIndex.Entry> entries(Level level, GraphIndex index, ItemStack primary) {
        var type = GraphAssembly.BREWING_TYPE.get();
        if (!Workpiece.has(primary)) return index.entries(type);
        var progress = Workpiece.read(level, primary);
        if (progress == null) return List.of();
        var graph = index.graphs().get(progress.graphId());
        if (graph == null) return List.of();
        return GraphEngine.allowed(graph, progress).stream()
            .filter(id -> index.nativeType(graph.id(), id) == type)
            .map(id -> new GraphIndex.Entry(graph, graph.nodes().get(id), null)).toList();
    }

    private static void collect(Level level, List<GraphIndex.Entry> entries, BrewingRecipe.Primary mode,
                                ItemStack primary, ItemStack auxiliary, List<Candidate> result) {
        for (var entry : entries) {
            var descriptor = BrewingDefinition.create(level, entry.node());
            if (descriptor.primary() != mode) continue;
            if (entry.ingredient() != null && !entry.ingredient().test(primary.copy())) continue;
            if (!WorkpieceInputs.matches(level, entry.graph(), entry.node(), 0, primary)
                || !WorkpieceInputs.matches(level, entry.graph(), entry.node(), 1, auxiliary)) continue;
            if (!descriptor.matchesAddition(auxiliary)) continue;
            result.add(new Candidate(mode, new GraphOperation(level, entry.graph(), entry.node(), primary, List.of(auxiliary))));
        }
    }

    private static ItemStack mix(PotionBrewing brewing, ItemStack bottle, ItemStack material) {
        if (!brewing.isInput(bottle.copy()) || !brewing.isIngredient(material.copy())
            || !brewing.hasMix(bottle.copy(), material.copy())) return ItemStack.EMPTY;
        var offered = bottle.copy();
        var mixed = brewing.mix(material.copy(), offered);
        // Vanilla returns the unchanged offered instance when there was no conversion.
        // A registered recipe can explicitly return a new, value-equal stack.
        return mixed == offered && ItemStack.matches(bottle, mixed) ? ItemStack.EMPTY : mixed.copy();
    }
}
