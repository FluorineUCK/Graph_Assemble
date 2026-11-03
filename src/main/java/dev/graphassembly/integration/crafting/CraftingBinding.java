package dev.graphassembly.integration.crafting;

import dev.graphassembly.integration.*;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Geometry-aware physical assignment. One nonempty cell is exactly one available unit. */
public final class CraftingBinding {
    private CraftingBinding() {}
    public static InputAssignment.Result bind(Level level,CraftingContainer grid,CraftingRecipe recipe,GraphOperation operation) {
        if(SpecialCraftingDefinition.standard(recipe)) {
            var positioned=CraftingInput.ofPositioned(grid.getWidth(),grid.getHeight(),grid.getItems().stream().map(ItemStack::copy).toList());
            var input=positioned.input();
            if(!recipe.matches(input,level))return none();
            var slots=SpecialCraftingDefinition.roles(recipe,input);
            if(slots.length==0)return none();
            for(var role:operation.node().workpieceInputs())if(role.itemInput()>=slots.length)return none();
            for(int role=0;role<slots.length;role++) {
                int logical=slots[role];
                if(!matches(level,operation,role,input.getItem(logical)))return none();
                slots[role]=logical%input.width()+positioned.left()+(logical/input.width()+positioned.top())*grid.getWidth();
            }
            return new InputAssignment.Result(InputAssignment.Status.UNIQUE,slots);
        }
        var ingredients=recipe.getIngredients().stream().filter(i->!i.isEmpty()).toList();
        int count=0; for(int s=0;s<grid.getContainerSize();s++)if(!grid.getItem(s).isEmpty())count++;
        if(count!=ingredients.size() || !recipe.matches(grid.asCraftInput(),level))return none();
        if(recipe instanceof ShapedRecipe shaped) {
            int[] witness=null;
            for(int y=0;y<=grid.getHeight()-shaped.getHeight();y++)for(int x=0;x<=grid.getWidth()-shaped.getWidth();x++)for(boolean mirror:new boolean[]{false,true}) {
                var slots=new ArrayList<Integer>(); boolean ok=true;
                for(int row=0;row<shaped.getHeight();row++)for(int col=0;col<shaped.getWidth();col++) {
                    var ingredient=shaped.getIngredients().get(col+row*shaped.getWidth());
                    int slot=x+(mirror?shaped.getWidth()-1-col:col)+(y+row)*grid.getWidth();
                    var stack=grid.getItem(slot);
                    if(ingredient.isEmpty()) {if(!stack.isEmpty())ok=false;continue;}
                    int role=slots.size(); slots.add(slot);
                    if(!ingredient.test(stack) || !matches(level,operation,role,stack))ok=false;
                }
                if(!ok)continue;
                var assignment=slots.stream().mapToInt(i->i).toArray();
                if(witness!=null)for(int role=0;role<assignment.length;role++)
                    if((role==0 || operation.node().workpieceInput(role)!=null)
                        && !ItemStack.isSameItemSameComponents(grid.getItem(witness[role]),grid.getItem(assignment[role])))
                        return new InputAssignment.Result(InputAssignment.Status.AMBIGUOUS,witness);
                witness=assignment;
            }
            return witness==null?none():new InputAssignment.Result(InputAssignment.Status.UNIQUE,witness);
        }
        int slots=grid.getContainerSize(); boolean[][] matrix=new boolean[ingredients.size()][slots];
        int[] capacity=new int[slots],identity=new int[slots]; boolean[] significant=new boolean[ingredients.size()];
        for(int slot=0;slot<slots;slot++) {
            var stack=grid.getItem(slot); capacity[slot]=stack.isEmpty()?0:1; identity[slot]=slot;
            for(int p=0;p<slot;p++)if(ItemStack.isSameItemSameComponents(stack,grid.getItem(p))){identity[slot]=identity[p];break;}
            for(int role=0;role<ingredients.size();role++)matrix[role][slot]=!stack.isEmpty()
                && ingredients.get(role).test(stack) && matches(level,operation,role,stack);
        }
        for(int role=0;role<significant.length;role++)significant[role]=role==0 || operation.node().workpieceInput(role)!=null;
        return InputAssignment.solve(matrix,capacity,identity,significant);
    }
    private static boolean matches(Level level,GraphOperation operation,int role,ItemStack stack) {
        return (role!=0 || ItemStack.isSameItemSameComponents(operation.primary(),stack))
            && WorkpieceInputs.matches(level,operation.graph(),operation.node(),role,stack);
    }
    private static InputAssignment.Result none(){return new InputAssignment.Result(InputAssignment.Status.NONE,new int[0]);}
}
