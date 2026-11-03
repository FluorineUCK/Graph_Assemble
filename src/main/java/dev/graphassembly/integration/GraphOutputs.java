package dev.graphassembly.integration;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.recipe.Workpiece;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.core.Progress;
import dev.graphassembly.core.OutputTrees;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Read-only capacity alternatives; never use or advance the game's random source. */
public final class GraphOutputs {
    private GraphOutputs() {}
    /** Shared prepared primary settlement plus already-decoded native byproducts. */
    public record Prepared(dev.graphassembly.integration.furnace.CookingCompletion primary,
        List<com.simibubi.create.content.processing.recipe.ProcessingOutput> byproducts) {
        public Prepared {byproducts=List.copyOf(byproducts);}
        public boolean outputsEnabled(Level level) {
            return primary.outputsEnabled(level)&&byproducts.stream().allMatch(output->output.getStack().isItemEnabled(level.enabledFeatures()));
        }
        public List<ItemStack> roll(net.minecraft.util.RandomSource random) {
            var outputs=new ArrayList<>(primary.roll(random));
            for(var output:byproducts){var stack=output.rollOutput(random);if(!stack.isEmpty())outputs.add(stack);}
            return outputs;
        }
    }
    public static Prepared prepare(Level level,GraphOperation operation,ProcessingRecipe<?,?> recipe) {
        var primary=dev.graphassembly.integration.furnace.CookingCompletion.prepare(level,operation);
        var byproducts=new ArrayList<com.simibubi.create.content.processing.recipe.ProcessingOutput>();
        for(int i=1;i<recipe.getRollableResults().size();i++) {
            var output=recipe.getRollableResults().get(i);
            byproducts.add(new com.simibubi.create.content.processing.recipe.ProcessingOutput(output.getStack().copy(),output.getChance()));
        }
        return new Prepared(primary,byproducts);
    }
    public static boolean allPrimaryOutcomesFit(Level level, GraphOperation operation, java.util.function.Predicate<List<ItemStack>> fits) {
        return allPrimaryOutcomesFit(level,operation,ItemStack.EMPTY,fits);
    }
    public static boolean allPrimaryOutcomesFit(Level level,GraphOperation operation,ItemStack nativeCarrier,java.util.function.Predicate<List<ItemStack>> fits) {
        var node = operation.node(); var graph = operation.graph(); var primary = operation.primary();
        String inputItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.getItem()).toString();
        var preview=GraphEngine.plan(graph,Workpiece.read(level,primary),node.id(),inputItem);
        List<ItemStack> result = new ArrayList<>();
        if (!preview.settled()) {
            var carrier=!nativeCarrier.isEmpty()?nativeCarrier.copy():preview.item().equals(inputItem) ? primary : NativeSteps.stack(preview.item());
            var state=preview.state();
            if(!preview.branches().isEmpty()) {
                preview.branches().stream().map(edge->edge.to()).distinct().forEach(target->
                    result.add(Workpiece.write(carrier,new Progress(state.graphId(),state.entry(),state.current(),
                        state.marks(),state.checkpoints(),state.rewardCounts(),target,state.history(),state.pendingRoute()))));
            } else result.add(Workpiece.write(carrier,state));
        } else return OutputTrees.allMaximalOutcomesFit(preview.pool(), outputs -> fits.test(outputs.stream().map(out -> NativeSteps.output(level, out)).toList()));
        for (var alternative : result) if (!fits.test(List.of(alternative))) return false;
        return true;
    }
    public static int maxStacks(Level level, GraphOperation operation, ProcessingRecipe<?, ?> recipe) {
        var primary = operation.primary();
        var plan = GraphEngine.plan(operation.graph(), Workpiece.read(level, primary), operation.node().id(),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.getItem()).toString());
        return Math.addExact(plan.settled() ? OutputTrees.maxStacks(plan.pool()) : 1, maxByproducts(recipe).size());
    }
    public static List<ItemStack> maxByproducts(ProcessingRecipe<?, ?> recipe) {
        List<ItemStack> result = new ArrayList<>();
        for (int i=1;i<recipe.getRollableResults().size();i++) {
            var stack = recipe.getRollableResults().get(i).getStack();
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }
    public static List<ItemStack> roll(Level level, GraphOperation operation, ProcessingRecipe<?, ?> recipe) {
        return roll(level, operation, recipe, level.random);
    }
    public static List<ItemStack> roll(Level level, GraphOperation operation, ProcessingRecipe<?, ?> recipe, net.minecraft.util.RandomSource random) {
        var primary = operation.primary();
        List<ItemStack> result = new ArrayList<>(GraphRuntime.completeAll(level, operation.graph(), operation.node().id(), Workpiece.read(level, primary), primary, random));
        for (int i=1;i<recipe.getRollableResults().size();i++) {
            var stack = recipe.getRollableResults().get(i).rollOutput(random);
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }
}
