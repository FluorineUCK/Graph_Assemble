package dev.graphassembly.integration;

import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.core.Node;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A declared role is a graph traversal predicate, never just an item ingredient. */
public final class WorkpieceInputs {
    private WorkpieceInputs() {}

    public static boolean matches(Level level, GraphDefinition graph, Node node, int index, ItemStack stack) {
        var role = node.workpieceInput(index);
        // Optional native auxiliary slots may be empty; the native ingredient/planner still
        // decides whether that slot is required. A declared workpiece can never be absent.
        if (stack.isEmpty()) return index != 0 && role == null;
        if (!Workpiece.has(stack)) return role == null && (index != 0 || node.start());
        if (index != 0 && role == null) return false;
        try {
            var progress = Workpiece.read(level, stack);
            return GraphEngine.canEnter(graph, progress, node.id())
                && (role == null || role.matches(progress))
                && (index != 0 || GraphEngine.allowed(graph, progress).contains(node.id()));
        } catch (RuntimeException malformed) { return false; }
    }
}
