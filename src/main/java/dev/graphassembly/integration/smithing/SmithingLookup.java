package dev.graphassembly.integration.smithing;

import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.Workpiece;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Fixed native geometry; logical roles are base=0, template=1, addition=2. */
public final class SmithingLookup {
    private SmithingLookup() {}
    public record Lookup(boolean handled,GraphOperation operation,ItemStack carrier) {
        public Lookup {carrier=carrier.copy();}
        @Override public ItemStack carrier(){return carrier.copy();}
    }
    private record Candidate(GraphOperation operation,ItemStack carrier) {}
    private static List<GraphIndex.Entry> entries(Level level,ItemStack base) {
        var index=GraphIndex.get(level);var entries=new ArrayList<GraphIndex.Entry>();
        var progress=Workpiece.read(level,base);
        if(progress==null)entries.addAll(index.entries(RecipeType.SMITHING));
        else {
            var graph=index.graphs().get(progress.graphId());
            if(graph!=null)for(var id:GraphEngine.allowed(graph,progress))entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));
        }
        return entries;
    }
    public static Lookup find(Level level,Container inputs) {
        boolean protectedInput=false;for(int i=0;i<3;i++)protectedInput|=Workpiece.has(inputs.getItem(i));
        var base=inputs.getItem(1).copy();if(base.isEmpty())return new Lookup(protectedInput,null,ItemStack.EMPTY);
        var template=inputs.getItem(0).copy();var addition=inputs.getItem(2).copy();
        var found=new ArrayList<Candidate>();boolean preparedFailure=false;
        // Preparation exceptions belong to the caller's protected failure boundary, not to
        // the ordinary/unhandled result. In particular a raw matched entry has no Workpiece yet.
        for(var entry:entries(level,base)) {
                if(!primary(level,entry,base))continue;
                var recipe=SmithingDefinition.create(level,entry.node(),base);
                var nativeInput=new SmithingRecipeInput(template.copy(),base.copy(),addition.copy());
                if(template.isEmpty()||addition.isEmpty()||!recipe.isTemplateIngredient(nativeInput.template())||!recipe.isBaseIngredient(nativeInput.base())||!recipe.isAdditionIngredient(nativeInput.addition())
                    ||!WorkpieceInputs.matches(level,entry.graph(),entry.node(),1,template)||!WorkpieceInputs.matches(level,entry.graph(),entry.node(),2,addition))continue;
                ItemStack carrier=ItemStack.EMPTY;
                if(SmithingDefinition.trim(recipe)) {
                    if(!recipe.matches(nativeInput,level))continue;
                    preparedFailure=true;
                    carrier=recipe.assemble(new SmithingRecipeInput(template.copy(),base.copy(),addition.copy()),level.registryAccess());
                    if(carrier.isEmpty()||carrier.getCount()!=1||carrier.getMaxStackSize()<1||!carrier.isItemEnabled(level.enabledFeatures()))continue;
                }
                found.add(new Candidate(new GraphOperation(level,entry.graph(),entry.node(),base.copy(),List.of(template.copy(),addition.copy())),carrier.copy()));
        }
        var candidate=found.size()==1?found.getFirst():null;
        return new Lookup(protectedInput||preparedFailure||!found.isEmpty(),candidate==null?null:candidate.operation(),candidate==null?ItemStack.EMPTY:candidate.carrier());
    }
    private static boolean primary(Level level,GraphIndex.Entry entry,ItemStack base) {
        return GraphIndex.get(level).nativeType(entry.graph().id(),entry.node().id())==RecipeType.SMITHING
            &&(Workpiece.has(base)||entry.ingredient().test(base))&&WorkpieceInputs.matches(level,entry.graph(),entry.node(),0,base);
    }
    /** Admission is partial: the template/addition may be inserted before the primary exists. */
    public static boolean admits(Level level,int physical,ItemStack incoming) {
        if(incoming.isEmpty())return false;
        try {
            var index=GraphIndex.get(level);
            var candidates=physical==1?entries(level,incoming):index.graphs().values().stream()
                .flatMap(graph->graph.nodes().values().stream().map(node->new GraphIndex.Entry(graph,node,null))).toList();
            for(var entry:candidates) {
                if(index.nativeType(entry.graph().id(),entry.node().id())!=RecipeType.SMITHING)continue;
                if(physical==1) {if(primary(level,entry,incoming)){
                    var recipe=SmithingDefinition.create(level,entry.node(),incoming);
                    if(!SmithingDefinition.trim(recipe)||recipe.isBaseIngredient(incoming.copy()))return true;
                }}
                else {
                    int logical=physical==0?1:2;
                    if(!WorkpieceInputs.matches(level,entry.graph(),entry.node(),logical,incoming))continue;
                    var recipe=SmithingDefinition.create(level,entry.node(),NativeSteps.stack(entry.graph().transitionalItem()));
                    if(physical==0?recipe.isTemplateIngredient(incoming):recipe.isAdditionIngredient(incoming))return true;
                }
            }
        } catch(RuntimeException invalid){return false;}
        return false;
    }
}
