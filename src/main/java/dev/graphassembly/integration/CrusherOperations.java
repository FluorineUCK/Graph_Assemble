package dev.graphassembly.integration;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import java.util.*;

/** Crusher admits the two upstream recipe families without manufacturing a type conversion. */
public final class CrusherOperations {
    private CrusherOperations() {}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static GraphRuntime.Lookup<StandardProcessingRecipe<RecipeWrapper>> lookup(Level level,ItemStack input) {
        var crushing=GraphRuntime.lookup(level,input,AllRecipeTypes.CRUSHING.getType(),CrushingRecipe.class,r->true);
        if(!crushing.recipes().isEmpty())return new GraphRuntime.Lookup<>(true,(List)crushing.recipes());
        boolean tracked=dev.graphassembly.recipe.Workpiece.has(input)||input.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
        if(crushing.handled()&&!tracked)return new GraphRuntime.Lookup<>(true,List.of());
        // A tracked workpiece may simply be on the other admitted family; ambiguity in the
        // crushing family still takes precedence over milling, just as the native host does.
        if(crushing.handled()&&tracked&&GraphRuntime.lookup(level,input,AllRecipeTypes.CRUSHING.getType(),CrushingRecipe.class,r->true,true).recipes().size()>1)
            return new GraphRuntime.Lookup<>(true,List.of());
        var milling=GraphRuntime.lookup(level,input,AllRecipeTypes.MILLING.getType(),MillingRecipe.class,r->true);
        return new GraphRuntime.Lookup<>(crushing.handled()||milling.handled(),(List)milling.recipes());
    }
    /** Empty slots are reserved conservatively before any RNG; large batches retain raw remainder. */
    public static boolean apply(Level level,com.simibubi.create.content.processing.recipe.ProcessingInventory inventory,
                                ProcessingRecipe<?,?> recipe) {
        var input=inventory.getStackInSlot(0);
        var operation=GraphOperation.get(recipe);
        if(operation==null||!operation.matchesPrimary(level,input))return false;
        var prepared=GraphOutputs.prepare(level,operation,recipe);
        if(!prepared.outputsEnabled(level))return false;
        var remainder=input.getCraftingRemainingItem();
        int perItem=GraphOutputs.maxStacks(level,operation,recipe)+(remainder.isEmpty()?0:1);
        int free=0;
        for(int i=1;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).isEmpty())free++;
        int rolls=perItem==0?input.getCount():Math.min(input.getCount(),free/perItem);
        if(rolls<=0)return false;
        var outputs=new ArrayList<ItemStack>();
        for(int i=0;i<rolls;i++) {
            outputs.addAll(prepared.roll(level.random));
            if(!remainder.isEmpty())outputs.add(remainder.copy());
        }
        input.shrink(rolls);
        int slot=1;
        for(var output:outputs) {
            if(output.isEmpty())continue;
            while(!inventory.getStackInSlot(slot).isEmpty())slot++;
            inventory.setStackInSlot(slot++,output);
        }
        return true;
    }
}
