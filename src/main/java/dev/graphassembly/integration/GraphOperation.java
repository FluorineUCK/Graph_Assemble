package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Identity-scoped metadata for fresh native recipes; values never retain the weak recipe key. */
public record GraphOperation(Level level, GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> node, ItemStack primary, List<ItemStack> auxiliary, dev.graphassembly.integration.sequence.NativeSequences.Stage sequence) {
    private static final Map<Recipe<?>, GraphOperation> OPERATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    public GraphOperation(Level level,GraphDefinition<OperationSpec> graph,NodeDefinition<OperationSpec> node,ItemStack primary,List<ItemStack> auxiliary) {
        this(level,graph,node,primary,auxiliary,dev.graphassembly.integration.sequence.NativeSequences.stage(level,graph));
    }
    public GraphOperation {
        primary = primary.copyWithCount(1);
        auxiliary = auxiliary.stream().map(s -> s.copyWithCount(1)).toList();
    }
    @Override public ItemStack primary() { return primary.copy(); }
    @Override public List<ItemStack> auxiliary() { return auxiliary.stream().map(ItemStack::copy).toList(); }
    public static GraphOperation get(Recipe<?> recipe) { return OPERATIONS.get(recipe); }
    public static void attach(Recipe<?> recipe, GraphOperation operation) { OPERATIONS.put(recipe, operation); }
    public boolean current(Level level) { return level == this.level && (sequence!=null?sequence.current(level):GraphIndex.get(level).graphs().get(graph.id()) == graph); }
    public String definitionIdentity(Level level) {
        var holder=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(graph.id())).orElseThrow();
        if(holder.value() instanceof dev.graphassembly.recipe.GraphRecipe recipe)return recipe.json().toString();
        return Recipe.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),holder.value()).getOrThrow().toString();
    }
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
