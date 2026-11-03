package dev.graphassembly.integration.crafting;

import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Shared native crafting lookup and non-authoritative preview; no device transfer policy. */
public final class CraftingLookup {
    private CraftingLookup() {}
    public record Plan(GraphOperation operation,CraftingRecipe recipe,int[] slots,ItemStack nativeCarrier) {
        public Plan {slots=slots.clone();nativeCarrier=nativeCarrier.copy();}
        @Override public int[] slots(){return slots.clone();}
        @Override public ItemStack nativeCarrier(){return nativeCarrier.copy();}
    }
    public record Lookup(boolean handled,Plan plan) {}
    public static Lookup find(net.minecraft.world.level.Level level,CraftingContainer grid) {
        return find(level,grid,(operation,recipe)->true);
    }
    public static Lookup find(Level level,CraftingContainer grid,java.util.function.BiPredicate<GraphOperation,CraftingRecipe> admitted) {
        var index=GraphIndex.get(level);
        var candidates=new ArrayList<Plan>(); boolean protectedInput=false,ambiguous=false;
        for(int slot=0;slot<grid.getContainerSize();slot++) {
            var primary=grid.getItem(slot); if(primary.isEmpty())continue;
            protectedInput|=Workpiece.has(primary);
            try {
                var progress=Workpiece.read(level,primary);
                var entries=new ArrayList<GraphIndex.Entry>();
                if(progress==null)entries.addAll(index.entries(RecipeType.CRAFTING));
                else {
                    var graph=index.graphs().get(progress.graphId()); if(graph==null)continue;
                    for(var id:GraphEngine.allowed(graph,progress))entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));
                }
                for(var entry:entries) {
                    if(index.nativeType(entry.graph().id(),entry.node().id())!=RecipeType.CRAFTING)continue;
                    if(progress==null && !entry.ingredient().test(primary))continue;
                    if(candidates.stream().anyMatch(p->p.operation().graph()==entry.graph() && p.operation().node()==entry.node()
                        && ItemStack.isSameItemSameComponents(primary,p.operation().primary())))continue;
                    var operation=new GraphOperation(level,entry.graph(),entry.node(),primary.copy(),List.of());
                    var recipe=(CraftingRecipe)NativeSteps.template(level,entry.graph(),entry.node(),primary);
                    if(!admitted.test(operation,recipe))continue;
                    if(SpecialCraftingDefinition.standard(recipe)) {
                        var input=CraftingInput.of(grid.getWidth(),grid.getHeight(),grid.getItems().stream().map(ItemStack::copy).toList());
                        if(recipe.matches(input,level)) {
                            var roles=SpecialCraftingDefinition.roles(recipe,input);
                            // Claim only a genuine native operation with this fixed physical primary.
                            // Role/progress or assembled-carrier failure must not unlock ordinary pickup.
                            if(roles.length>0&&ItemStack.isSameItemSameComponents(primary,input.getItem(roles[0])))protectedInput=true;
                        }
                    }
                    if(!WorkpieceInputs.matches(level,entry.graph(),entry.node(),0,primary))continue;
                    var bound=CraftingBinding.bind(level,grid,recipe,operation);
                    if(bound.status()==InputAssignment.Status.AMBIGUOUS){ambiguous=true;continue;}
                    if(bound.status()==InputAssignment.Status.UNIQUE) {
                        var carrier=SpecialCraftingDefinition.standard(recipe)?SpecialCraftingDefinition.carrier(level,recipe,
                            CraftingInput.of(grid.getWidth(),grid.getHeight(),grid.getItems().stream().map(ItemStack::copy).toList())):ItemStack.EMPTY;
                        if(SpecialCraftingDefinition.standard(recipe)&&carrier.isEmpty())continue;
                        candidates.add(new Plan(operation,recipe,bound.slots(),carrier));
                    }
                }
            } catch(RuntimeException invalid) { /* Protected invalid progress never reaches ordinary fallback. */ }
        }
        if(ambiguous || candidates.size()>1)return new Lookup(true,null);
        return new Lookup(protectedInput || !candidates.isEmpty(),candidates.isEmpty()?null:candidates.getFirst());
    }

    public static ItemStack preview(Level level,Lookup lookup) {
        return lookup.plan()==null?ItemStack.EMPTY:preview(level,lookup.plan().operation(),lookup.plan().nativeCarrier());
    }
    public static ItemStack preview(Level level,GraphOperation operation) {
        return preview(level,operation,ItemStack.EMPTY);
    }
    public static ItemStack preview(Level level,GraphOperation operation,ItemStack nativeCarrier) {
        ItemStack preview=ItemStack.EMPTY;
        {
            var primary=operation.primary();
            var outcome=GraphEngine.plan(operation.graph(),Workpiece.read(level,primary),operation.node().id(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.getItem()).toString());
            if(!outcome.settled()) {
                var carrier=!nativeCarrier.isEmpty()?nativeCarrier.copy():outcome.item().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.getItem()).toString())?primary:NativeSteps.stack(outcome.item());
                var statePreview=outcome.state();
                if(!outcome.branches().isEmpty())statePreview=new dev.graphassembly.core.Progress(statePreview.graphId(),statePreview.entry(),statePreview.current(),
                    statePreview.marks(),statePreview.checkpoints(),statePreview.rewardCounts(),outcome.branches().getFirst().to(),
                    statePreview.history(),statePreview.pendingRoute());
                preview=Workpiece.write(carrier,statePreview);
            } else if(!outcome.pool().isEmpty()) {
                // Representative only; transaction always checks every possible result bundle afresh.
                var first=dev.graphassembly.core.OutputTrees.leaves(outcome.pool());
                if(!first.isEmpty())preview=NativeSteps.output(level,first.getFirst());
            }
            if(preview.isEmpty())preview=primary.copyWithCount(1);
        }
        return preview;
    }
}
