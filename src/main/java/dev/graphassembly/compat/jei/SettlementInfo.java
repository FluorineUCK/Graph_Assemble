package dev.graphassembly.compat.jei;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.client.view.SettlementDescription;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.NativeSteps;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;

/** Registers descriptions in JEI's native Information category, which owns wrapping and scrolling. */
final class SettlementInfo {
    private SettlementInfo() {}
    static void register(IRecipeRegistration registration, Level level, GraphDefinition<OperationSpec> graph) {
        var document = SettlementDescription.from(graph);
        var ingredients = new ArrayList<ItemStack>();
        for (var output : document.outputs()) {
            var stack = NativeSteps.output(level, output);
            if (stack.isEmpty()) continue;
            stack.setCount(1); // Counts and weights belong to the conditions, not ingredient identity.
            if (ingredients.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) ingredients.add(stack);
        }
        // Strict finishes without outputs still need an accessible explanation.
        if (ingredients.isEmpty()) ingredients.add(GraphAssembly.FILTER.get().getDefaultInstance());
        var paragraphs = document.paragraphs().stream()
            .map(SettlementInfo::render).toArray(Component[]::new);
        registration.addItemStackInfo(ingredients, paragraphs);
    }
    private static Component render(SettlementDescription.Line line) {
        String key = line.key();
        // JEI skips empty components during newline expansion; a space preserves the section break.
        if (key.equals("info.gap")) return Component.literal(" ");
        var content = text(key, line.arguments().toArray()).copy();
        switch (key) {
            case "info.title", "finish", "finish.none", "info.inputs", "info.notes", "actions.title" ->
                content.withStyle(style -> style.withBold(true).withColor(0x234958));
            case "rule_pool", "rule_pool.weighted", "requirements.heading", "source.graph", "source.node", "input.role", "actions.node" ->
                content.withStyle(style -> style.withBold(true));
            case "output", "output.plain" -> content.withStyle(style -> style.withColor(0x245536));
            case "info.graph" -> content.withStyle(style -> style.withColor(0x555555));
        }
        // Keep native JEI wrapping/scrolling. Group paths remain explicit when very deep trees cap indentation.
        String prefix = " ".repeat(Math.min(line.depth(), 5));
        if (key.equals("output") || key.equals("output.plain")) prefix += "• ";
        return prefix.isEmpty() ? content : Component.literal(prefix).append(content);
    }
    static Component text(String key, Object... arguments) {
        if ((key.equals("output") || key.equals("output.plain")) && arguments.length > 0 && arguments[0] instanceof String item) {
            arguments = arguments.clone();
            arguments[0] = NativeSteps.stack(item).getHoverName();
        }
        return Component.translatable("graph_assembly.settlement." + key, arguments);
    }
}
