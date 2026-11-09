package dev.graphassembly.integration;


import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Read-only capacity alternatives; never use or advance the game's random source. */
public final class GraphOutputs {
    private GraphOutputs() {}
    /** Shared prepared primary settlement plus already-decoded native byproducts. */
    public record Prepared(OperationCompletion primary,
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
        var primary=OperationCompletion.prepare(level,operation);
        var byproducts=new ArrayList<com.simibubi.create.content.processing.recipe.ProcessingOutput>();
        for(int i=1;i<recipe.getRollableResults().size();i++) {
            var output=recipe.getRollableResults().get(i);
            byproducts.add(new com.simibubi.create.content.processing.recipe.ProcessingOutput(output.getStack().copy(),output.getChance()));
        }
        return new Prepared(primary,byproducts);
    }
    public static int maxStacks(Level level, GraphOperation operation, ProcessingRecipe<?, ?> recipe) {
        return Math.addExact(OperationCompletion.prepare(level,operation).maxStacks(),maxByproducts(recipe).size());
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
        return prepare(level,operation,recipe).roll(random);
    }
}
