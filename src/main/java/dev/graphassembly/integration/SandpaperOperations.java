package dev.graphassembly.integration;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import java.util.List;

/** Sandpaper has one result stack; both the player's query and completion use this gate. */
public final class SandpaperOperations {
    private SandpaperOperations() {}
    public static GraphRuntime.Lookup<SandPaperPolishingRecipe> lookup(Level level,ItemStack input) {
        var found=GraphRuntime.lookup(level,input,AllRecipeTypes.SANDPAPER_POLISHING.getType(),SandPaperPolishingRecipe.class,r->true);
        var accepted=found.recipes().stream().filter(holder->{
            var operation=GraphOperation.get(holder.value());
            var completion=OperationCompletion.prepare(level,operation);
            return fits(level,completion);
        }).toList();
        return new GraphRuntime.Lookup<>(found.handled(),accepted);
    }
    private static boolean fits(Level level,OperationCompletion completion) {
        return completion.maxStacks()<=1&&completion.outputsEnabled(level)
            &&completion.allMaximalOutcomesFit(outputs->dev.graphassembly.integration.vanilla.furnace.CookingLookup.merge(ItemStack.EMPTY,outputs,Integer.MAX_VALUE)!=null);
    }
    public static ItemStack complete(Level level,ItemStack input,RecipeHolder<SandPaperPolishingRecipe> holder) {
        var operation=GraphOperation.get(holder.value());
        var completion=OperationCompletion.prepare(level,operation);
        if(!fits(level,completion)||!operation.matchesPrimary(level,input))return input.copy();
        var results=completion.roll(level.random);
        return results.isEmpty()?ItemStack.EMPTY:results.getFirst();
    }
}
