package dev.graphassembly.compat.jei;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.client.view.SettlementGroups;
import dev.graphassembly.client.view.SettlementCandidates;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.NativeSteps;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Single-ingredient native slots; the complete JEI index is registered separately. */
final class SettlementSlots {
    record Entry(IRecipeSlotDrawable slot, ItemStack stack, List<Component> provenance, String quantityLabel) {}
    final List<Entry> inputs = new ArrayList<>(), overview = new ArrayList<>();
    final Map<String, List<Entry>> success = new LinkedHashMap<>(), scrap = new LinkedHashMap<>();
    final List<IRecipeSlotDrawable> all = new ArrayList<>();
    SettlementSlots(GraphDefinition<OperationSpec> graph, SettlementGroups groups) {
        var level = Minecraft.getInstance().level;
        if (level == null || GraphJeiPlugin.runtime() == null) return;
        for (var node : graph.nodes().values()) if (node.roles().start()) {
            for (var stack : NativeSteps.ingredient(level, node.inputs().ingredientOverride() == null ? graph.defaults().ingredient() : node.inputs().ingredientOverride()).getItems()) {
                var existing = inputs.stream().filter(entry -> ItemStack.isSameItemSameComponents(entry.stack(), stack)
                    && entry.stack().getCount() == stack.getCount()).findFirst();
                if (existing.isPresent()) existing.get().provenance().add(SettlementInfo.text("footer.start", node.id()));
                else inputs.add(create(stack.copy(), RecipeIngredientRole.INPUT, new ArrayList<>(List.of(
                    SettlementInfo.text("footer.input_hint"), SettlementInfo.text("footer.start", node.id())))));
            }
        }
        overview.addAll(candidates(groups.overview(), false));
        for (var finish : groups.finishes()) {
            success.put(finish.id(), candidates(finish.success(), false));
            scrap.put(finish.id(), candidates(finish.scrap(), true));
        }
    }
    private List<Entry> candidates(List<SettlementGroups.Candidate> candidates, boolean failure) {
        return SettlementCandidates.group(candidates).stream().map(group -> {
            List<Component> lines = new ArrayList<>();
            lines.add(SettlementInfo.text(failure ? "footer.failure_hint" : "footer.candidate_hint"));
            lines.add(SettlementInfo.text("footer.quantities", group.quantityLabel()));
            for (var quantitySource : group.sources()) {
                var source = quantitySource.source();
                var provenance = source.ruleNumber() == 0
                    ? SettlementInfo.text("footer.scrap_source", source.finish(), source.weight())
                    : SettlementInfo.text("footer.source", source.finish(), source.ruleSource() == null
                        ? SettlementInfo.text("footer.graph_rule") : source.ruleSource(), source.ruleNumber(), source.weight());
                lines.add(SettlementInfo.text("footer.quantity_source", quantitySource.quantities(), provenance));
                for (var groupContext : source.groups()) {
                    lines.add(SettlementInfo.text("tree." + groupContext.mode().name().toLowerCase(Locale.ROOT),
                        groupContext.path(), groupContext.weight()));
                    if (groupContext.chance() != 1) lines.add(SettlementInfo.text("chance.group",
                        dev.graphassembly.client.view.SettlementDescription.percent(groupContext.chance())));
                }
                if (source.chance() != 1) lines.add(SettlementInfo.text("footer.chance_item",
                    dev.graphassembly.client.view.SettlementDescription.percent(source.chance())));
            }
            lines.add(SettlementInfo.text("footer.info_hint"));
            var stack = NativeSteps.output(Minecraft.getInstance().level, group.variants().getFirst().output());
            // A representative icon is not a predicted count. The complete quantity-specific JEI index
            // remains in GraphCategory; show quantity text separately without overlapping native counts.
            stack.setCount(1);
            return create(stack, RecipeIngredientRole.OUTPUT, lines, group.quantityLabel().equals("1") ? "" : group.quantityLabel());
        }).toList();
    }
    private Entry create(ItemStack stack, RecipeIngredientRole role, List<Component> lines) {
        return create(stack, role, lines, "");
    }
    private Entry create(ItemStack stack, RecipeIngredientRole role, List<Component> lines, String quantityLabel) {
        var runtime = GraphJeiPlugin.runtime();
        Optional<mezz.jei.api.ingredients.ITypedIngredient<?>> typed = runtime.getIngredientManager()
            .createTypedIngredient(VanillaTypes.ITEM_STACK, stack).map(ingredient -> ingredient);
        var slot = runtime.getRecipeManager().createRecipeSlotDrawable(role, List.of(typed), Set.of(), 0);
        slot.addTooltipCallback((view, tooltip) -> tooltip.addAll(lines));
        all.add(slot);
        return new Entry(slot, stack, lines, quantityLabel);
    }
}
