package dev.graphassembly.client.view;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.InputEligibility;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.io.WorkpieceInput;
import dev.graphassembly.core.progress.Progress;

import dev.graphassembly.integration.vanilla.brewing.BrewingDefinition;
import dev.graphassembly.integration.vanilla.brewing.BrewingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Recipe;
import java.util.*;
import java.util.function.Function;

/** Candidate roles for tooltip display, never machine execution authorization. */
public record WorkpieceTooltipRoles(Set<String> primary, Map<String, List<WorkpieceInput>> auxiliary, Set<String> allowed) {
    public static WorkpieceTooltipRoles select(GraphDefinition<OperationSpec> graph, Progress state, PreflightResult preflight,
                                               PotionBrewing brewing, ItemStack stack, Function<String, Recipe<?>> resolve) {
        var primary = new LinkedHashSet<>(preflight.allowed());
        var auxiliary = new LinkedHashMap<>(InputEligibility.auxiliaries(graph, state, preflight));
        var candidates = new LinkedHashSet<>(primary);
        candidates.addAll(auxiliary.keySet());
        for (var id : candidates) {
            if (!BrewingDefinition.SERIALIZER.equals(graph.nodes().get(id).operation().recipe().get("type").getAsString())) continue;
            try {
                if (resolve.apply(id) instanceof BrewingRecipe descriptor) {
                    if (primary.contains(id) && !descriptor.matchesInput(brewing, 0, stack)) primary.remove(id);
                    var roles = auxiliary.getOrDefault(id, List.of()).stream()
                        .filter(role -> descriptor.matchesInput(brewing, role.itemInput(), stack)).toList();
                    if (roles.isEmpty()) auxiliary.remove(id);
                    else auxiliary.put(id, roles);
                    continue;
                }
            } catch (RuntimeException unavailable) {
                // Invalid/unavailable descriptor or ingredient cannot establish a tooltip role.
            }
            primary.remove(id);
            auxiliary.remove(id);
        }
        var allowed = new LinkedHashSet<>(primary);
        allowed.addAll(auxiliary.keySet());
        return new WorkpieceTooltipRoles(Collections.unmodifiableSet(primary),
            Collections.unmodifiableMap(auxiliary), Collections.unmodifiableSet(allowed));
    }
}
