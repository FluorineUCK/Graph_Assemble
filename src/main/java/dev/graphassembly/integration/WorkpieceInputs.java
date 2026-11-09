package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.InputEligibility;

import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A declared role is a graph traversal predicate, never just an item ingredient. */
public final class WorkpieceInputs {
    private WorkpieceInputs() {}

    public static boolean matches(Level level, GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> node, int index, ItemStack stack) {
        var sequence=dev.graphassembly.integration.sequence.NativeSequences.stage(level,graph);
        if(sequence!=null)return index==0?sequence.matches(stack):!dev.graphassembly.integration.sequence.NativeSequences.tracked(stack);
        if(stack.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY))return false;
        var role = node.inputs().workpieceInput(index);
        // Optional native auxiliary slots may be empty; the native ingredient/planner still
        // decides whether that slot is required. A declared workpiece can never be absent.
        if (stack.isEmpty()) return index != 0 && role == null;
        if (!Workpiece.has(stack)) return InputEligibility.canParticipate(graph, node, index, null);
        if (index != 0 && role == null) return false;
        try {
            var progress = Workpiece.read(level, stack);
            return InputEligibility.canParticipate(graph, node, index, progress);
        } catch (RuntimeException malformed) { return false; }
    }
}
