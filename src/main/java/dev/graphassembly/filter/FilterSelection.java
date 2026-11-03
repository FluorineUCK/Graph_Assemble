package dev.graphassembly.filter;

import com.google.gson.*;
import dev.graphassembly.core.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

public record FilterSelection(String graph, List<String> targets, Set<String> inverted, FilterConfiguration.Mode mode) {
    public static final FilterSelection EMPTY = new FilterSelection("", List.of());
    public FilterSelection(String graph, List<String> targets) { this(graph, targets, Set.of(), FilterConfiguration.Mode.ANY); }
    public FilterSelection {
        var config = new FilterConfiguration(graph, targets, inverted, mode);
        targets = config.targets(); inverted = config.inverted();
    }
    public FilterConfiguration configuration() { return new FilterConfiguration(graph, targets, inverted, mode); }
    public String toJson() {
        return configuration().toJson();
    }
    public static FilterSelection parse(String json) {
        try {
            var config = FilterConfiguration.parse(json);
            return fromConfiguration(config);
        } catch (RuntimeException e) { return EMPTY; }
    }
    public static FilterSelection fromConfiguration(FilterConfiguration config) {
        return new FilterSelection(config.graph(), config.targets(), config.inverted(), config.mode());
    }
    public boolean matches(Level level, ItemStack input) {
        if (input.isEmpty()) return false;
        var index = GraphIndex.get(level);
        var definition = index.graphs().get(graph);
        if (definition == null || targets.isEmpty() || !definition.nodes().keySet().containsAll(targets)) return false;
        try {
            var state = Workpiece.read(level, input);
            Set<String> available;
            if (state != null) {
                if (!state.graphId().equals(graph) || GraphEngine.incompatibility(definition, state) != null) return false;
                available = Set.copyOf(GraphEngine.allowed(definition, state));
            } else {
                if (input.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY)) return false;
                available = new HashSet<>();
                for (String id : GraphEngine.allowed(definition, null))
                    if (index.entrance(graph, id).test(input)) available.add(id);
            }
            // Establish the selected graph's actionable input domain before applying NOT/NONE.
            return !available.isEmpty() && FilterConfiguration.matches(targets, inverted, mode, available);
        } catch (RuntimeException e) { return false; }
    }
}
