package dev.graphassembly.integration;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** Read-only drain eligibility. Simulations never call ProcessingRecipe.rollResults. */
public final class DrainOperations {
    private DrainOperations() {}
    public static GraphRuntime.Lookup<EmptyingRecipe> lookup(Level level,ItemStack input) {
        return GraphRuntime.lookup(level,input,AllRecipeTypes.EMPTYING.getType(),EmptyingRecipe.class,r->true);
    }
    public static boolean validFluid(Level level,FluidStack fluid) {
        return !fluid.isEmpty()&&fluid.getAmount()>0
            &&fluid.getFluid().defaultFluidState().createLegacyBlock().getBlock().isEnabled(level.enabledFeatures());
    }
    public static OperationCompletion completion(Level level,EmptyingRecipe recipe) {
        var operation=GraphOperation.get(recipe);
        if(operation==null||recipe.getFluidResults().size()!=1||!validFluid(level,recipe.getResultingFluid())
            ||recipe.getRollableResults().size()!=1||!recipe.getFluidIngredients().isEmpty())return null;
        var completion=OperationCompletion.prepare(level,operation);
        return completion.outputsEnabled(level)&&completion.maxStacks()<=1&&completion.allMaximalOutcomesFit(outputs ->
            dev.graphassembly.integration.vanilla.furnace.CookingLookup.merge(ItemStack.EMPTY,outputs,Integer.MAX_VALUE)!=null)?completion:null;
    }
}
