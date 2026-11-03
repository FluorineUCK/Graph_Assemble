package dev.graphassembly.client;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.client.view.TooltipSummary;
import dev.graphassembly.client.view.NextStepPresentation;
import dev.graphassembly.client.view.WorkpieceTooltipRoles;
import dev.graphassembly.core.*;
import dev.graphassembly.filter.GraphFilterItem;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.*;

/** Human-readable shared step descriptions; technical IDs/diagnostics require advanced tooltips. */
public final class PlayerTooltips {
    private PlayerTooltips() {}
    private static final Map<GraphIndex,Map<String,net.minecraft.world.item.crafting.Recipe<?>>> CACHE = new WeakHashMap<>();
    private static net.minecraft.world.item.crafting.Recipe<?> recipe(Level level, GraphDefinition graph, String id) {
        var steps = CACHE.computeIfAbsent(GraphIndex.get(level), index -> new HashMap<>());
        return steps.computeIfAbsent(graph.id() + "\u0000" + id, key ->
            GraphPreviewRecipes.resolve(level, graph, graph.nodes().get(id)).value());
    }
    private static Component node(Level level, GraphDefinition graph, String id) {
        if (graph == null || !graph.nodes().containsKey(id)) return Component.translatable("graph_assembly.operation.unavailable");
        try {
            var step = graph.nodes().get(id);
            if (step.isCampfire()) return StepDescription.describeCampfire(step);
            return StepDescription.player(recipe(level, graph, id));
        } catch (RuntimeException e) { return Component.translatable("graph_assembly.operation.unavailable"); }
    }
    private static void nodes(List<Component> lines, Level level, GraphDefinition graph, Collection<String> ids, boolean advanced) {
        nodes(lines, level, graph, ids, advanced, Set.of());
    }
    private static void nodes(List<Component> lines, Level level, GraphDefinition graph, Collection<String> ids, boolean advanced, Set<String> inverted) {
        var summary = TooltipSummary.of(ids);
        for (String id : summary.shown()) {
            var label = node(level,graph,id);
            if (inverted.contains(id)) label = Component.translatable("graph_assembly.filter.inverted", label);
            lines.add(Component.literal("  ").append(label).withStyle(ChatFormatting.DARK_AQUA));
            if (advanced) lines.add(Component.literal("    " + id).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (summary.remaining()>0) lines.add(Component.translatable("graph_assembly.summary.more", summary.remaining()).withStyle(ChatFormatting.GRAY));
    }
    public static List<Component> filter(ItemStack stack) {
        var mc = Minecraft.getInstance(); var selection = GraphFilterItem.selection(mc.level, stack);
        List<Component> lines = new ArrayList<>();
        // Like Create's Attribute Filter, an unconfigured item has no saved-condition summary.
        if (selection.targets().isEmpty()) return lines;
        var heading = selection.targets().size() == 1 && selection.mode() != dev.graphassembly.filter.FilterConfiguration.Mode.NONE
            ? Component.translatable("graph_assembly.filter.summary.single")
            : Component.translatable("graph_assembly.filter.summary." + selection.mode().id(), selection.targets().size());
        lines.addAll(com.simibubi.create.foundation.item.TooltipHelper.cutTextComponent(heading,
            com.simibubi.create.foundation.item.TooltipHelper.styleFromColor(ChatFormatting.GOLD),
            com.simibubi.create.foundation.item.TooltipHelper.styleFromColor(ChatFormatting.YELLOW)));
        if (mc.level == null) return lines;
        var graph = GraphIndex.get(mc.level).graphs().get(selection.graph());
        boolean advanced = mc.options.advancedItemTooltips;
        if (advanced && !selection.graph().isEmpty()) lines.add(Component.literal(selection.graph()).withStyle(ChatFormatting.GRAY));
        if (graph == null && !selection.targets().isEmpty()) lines.add(Component.translatable("graph_assembly.filter.stale").withStyle(ChatFormatting.RED));
        for (String id : TooltipSummary.of(selection.targets()).shown()) {
            Component label = node(mc.level, graph, id);
            if (selection.inverted().contains(id)) label = Component.translatable("graph_assembly.filter.inverted", label);
            lines.addAll(com.simibubi.create.foundation.item.TooltipHelper.cutTextComponent(Component.literal("- ").append(label),
                com.simibubi.create.foundation.item.TooltipHelper.styleFromColor(ChatFormatting.GRAY),
                com.simibubi.create.foundation.item.TooltipHelper.styleFromColor(ChatFormatting.WHITE)));
            if (advanced) lines.add(Component.literal("  " + id).withStyle(ChatFormatting.DARK_GRAY));
        }
        int remaining = TooltipSummary.of(selection.targets()).remaining();
        if (remaining > 0) lines.add(Component.translatable("graph_assembly.summary.more", remaining).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
    public static void workpiece(ItemTooltipEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !Workpiece.has(event.getItemStack())) return;
        var lines = event.getToolTip(); boolean advanced = event.getFlags().isAdvanced();
        Progress state;
        try { state = Workpiece.read(level,event.getItemStack()); }
        catch (RuntimeException e) {
            lines.add(Component.translatable("graph_assembly.stopped", Component.translatable("graph_assembly.stop.invalid")).withStyle(ChatFormatting.RED));
            if (advanced) lines.add(Component.literal("Invalid saved progress: " + e.getMessage()).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        var problem = GraphRuntime.diagnostic(level,state);
        if (problem != null) {
            lines.add(Component.translatable("graph_assembly.stopped",Component.translatable(problem.key())).withStyle(ChatFormatting.RED));
            if (advanced) lines.add(Component.literal(problem.detail()).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        var graph = GraphIndex.get(level).graphs().get(state.graphId());
        if (advanced) lines.add(Component.literal(state.graphId()).withStyle(ChatFormatting.GRAY));
        var preflight = GraphEngine.preflight(graph,state);
        var roles = WorkpieceTooltipRoles.select(graph, state, preflight, level.potionBrewing(),
            event.getItemStack(), id -> recipe(level, graph, id));
        var auxiliary = roles.auxiliary();
        var allowed = roles.allowed();
        lines.add(Component.translatable(state.pendingRoute()!=null && !allowed.isEmpty() ? "graph_assembly.next.routed"
            : NextStepPresentation.workpieceKey(allowed.size(), state.lockedNext()!=null)).withStyle(ChatFormatting.AQUA));
        for (String id : TooltipSummary.of(allowed).shown()) {
            nodes(lines, level, graph, List.of(id), advanced);
            if (roles.primary().contains(id)) join(lines, level, graph, id, 0, advanced);
            for (var role : auxiliary.getOrDefault(id, List.of())) join(lines, level, graph, id, role.itemInput(), advanced);
        }
        if (allowed.size() > TooltipSummary.MAX_ROWS)
            lines.add(Component.translatable("graph_assembly.summary.more", allowed.size() - TooltipSummary.MAX_ROWS));
        var blocked = new LinkedHashMap<>(preflight.blocked());
        auxiliary.keySet().forEach(blocked::remove);
        for (String id : TooltipSummary.of(blocked.keySet()).shown()) {
            String key = preflight.blockedReasons().get(id) == GraphEngine.BlockedReason.PRIMARY_CONDITIONS
                ? "graph_assembly.operation.blocked.conditions" : "graph_assembly.operation.blocked";
            lines.add(Component.translatable(key,node(level,graph,id)).withStyle(ChatFormatting.RED));
            if (advanced) lines.add(Component.literal(blocked.get(id)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (blocked.size()>TooltipSummary.MAX_ROWS) lines.add(Component.translatable("graph_assembly.summary.more",blocked.size()-TooltipSummary.MAX_ROWS));
        if (!state.history().isEmpty()) {
            lines.add(Component.translatable("graph_assembly.recent.title").withStyle(ChatFormatting.GRAY));
            nodes(lines,level,graph,state.history(),advanced);
        }
        if (!state.marks().isEmpty()) {
            var marks = TooltipSummary.of(state.marks().entrySet());
            lines.add(Component.translatable("graph_assembly.marks",marks.shown().toString()).withStyle(ChatFormatting.GRAY));
            if(marks.remaining()>0) lines.add(Component.translatable("graph_assembly.summary.more",marks.remaining()));
        }
    }

    private static void join(List<Component> lines, Level level, GraphDefinition graph, String id, int ownInput, boolean advanced) {
        var target = graph.nodes().get(id);
        if (target.workpieceInputs().stream().noneMatch(role -> role.itemInput() > 0)) return;
        var ownRole = Component.translatable("graph_assembly.join." + (ownInput == 0 ? "primary" : "auxiliary"));
        lines.add(ownRole.withStyle(ChatFormatting.GOLD));
        var partners = new TreeSet<Integer>();
        partners.add(0);
        target.workpieceInputs().forEach(role -> partners.add(role.itemInput()));
        partners.remove(ownInput);
        net.minecraft.world.item.crafting.Recipe<?> nativeRecipe;
        try { nativeRecipe = recipe(level, graph, id); }
        catch (RuntimeException unavailable) { return; }
        var brewing = nativeRecipe instanceof dev.graphassembly.integration.brewing.BrewingRecipe descriptor ? descriptor : null;
        if (brewing != null) ownRole.append(" · ").append(StepDescription.brewingInput(brewing, ownInput));
        var logicalIngredients = brewing != null ? List.<net.minecraft.world.item.crafting.Ingredient>of()
            : nativeRecipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING
                ? nativeRecipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList()
                : nativeRecipe.getIngredients();
        var shown = TooltipSummary.of(partners);
        for (int index : shown.shown()) {
            Component names;
            if (brewing != null) {
                names = StepDescription.brewingInput(brewing, index);
            } else {
                var genericNames = Component.empty();
                var items = index < logicalIngredients.size() ? logicalIngredients.get(index).getItems() : new ItemStack[0];
                var candidates = TooltipSummary.of(Arrays.asList(items));
                for (var item : candidates.shown()) {
                    if (!genericNames.getSiblings().isEmpty()) genericNames.append(Component.translatable("graph_assembly.join.or"));
                    genericNames.append(item.getHoverName());
                }
                if (items.length == 0) genericNames.append(Component.translatable("graph_assembly.operation.unavailable"));
                if (candidates.remaining() > 0) genericNames.append(Component.translatable("graph_assembly.summary.more", candidates.remaining()));
                names = genericNames;
            }
            lines.add(Component.translatable("graph_assembly.join.partner." + (index == 0 ? "primary" : "auxiliary"), names).withStyle(ChatFormatting.GRAY));
            var role = target.workpieceInput(index);
            if (role != null && !role.currentNodes().isEmpty()) {
                var current = Component.empty();
                for (String previous : TooltipSummary.of(role.currentNodes()).shown()) {
                    if (!current.getSiblings().isEmpty()) current.append(Component.translatable("graph_assembly.join.or"));
                    try {
                        var step = graph.nodes().get(previous);
                        current.append(step.isCampfire() ? StepDescription.describeCampfire(step)
                            : StepDescription.describe(recipe(level, graph, previous)));
                    }
                    catch (RuntimeException unavailable) { current.append(node(level, graph, previous)); }
                }
                int more = TooltipSummary.of(role.currentNodes()).remaining();
                if (more > 0) current.append(Component.translatable("graph_assembly.summary.more", more));
                lines.add(Component.translatable("graph_assembly.join.after", current).withStyle(ChatFormatting.GRAY));
                if (advanced) lines.add(Component.literal("    " + String.join(", ", role.currentNodes())).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (shown.remaining() > 0) lines.add(Component.translatable("graph_assembly.summary.more", shown.remaining()).withStyle(ChatFormatting.GRAY));
        if (ownInput > 0) lines.add(Component.translatable("graph_assembly.join.inherit").withStyle(ChatFormatting.GRAY));
    }
}
