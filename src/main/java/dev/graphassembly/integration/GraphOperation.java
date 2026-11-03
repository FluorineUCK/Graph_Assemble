package dev.graphassembly.integration;

import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.Node;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Identity-scoped metadata for fresh native recipes; values never retain the weak recipe key. */
public record GraphOperation(Level level, GraphDefinition graph, Node node, ItemStack primary, List<ItemStack> auxiliary) {
    private static final Map<Recipe<?>, GraphOperation> OPERATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    public GraphOperation {
        primary = primary.copyWithCount(1);
        auxiliary = auxiliary.stream().map(s -> s.copyWithCount(1)).toList();
    }
    @Override public ItemStack primary() { return primary.copy(); }
    @Override public List<ItemStack> auxiliary() { return auxiliary.stream().map(ItemStack::copy).toList(); }
    public static GraphOperation get(Recipe<?> recipe) { return OPERATIONS.get(recipe); }
    public static void attach(Recipe<?> recipe, GraphOperation operation) { OPERATIONS.put(recipe, operation); }
    public boolean current(Level level) { return level == this.level && GraphIndex.get(level).graphs().get(graph.id()) == graph; }
    public boolean matchesPrimary(Level level, ItemStack input) {
        return !input.isEmpty() && current(level) && ItemStack.isSameItemSameComponents(primary, input)
            && WorkpieceInputs.matches(level, graph, node, 0, input);
    }
    public boolean matchesHeld(Level level, ItemStack input, ItemStack held) {
        return matchesPrimary(level, input) && auxiliary.size() == 1 && !held.isEmpty()
            && ItemStack.isSameItemSameComponents(auxiliary.getFirst(), held)
            && WorkpieceInputs.matches(level, graph, node, 1, held);
    }
}
