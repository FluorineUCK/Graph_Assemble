package dev.graphassembly.integration.menu;

import dev.graphassembly.core.*;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/** Lazy possible-destination preflight: only actual chance gates may remove a leading result. */
public final class MenuOutputPreflight {
    private MenuOutputPreflight() {}
    private record Prepared(OutputNode source,List<ItemStack> quantities,List<Prepared> children) {}
    public static boolean fits(Level level,GraphOperation operation,Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits) {
        return fits(level,operation,ItemStack.EMPTY,prepare,limit,fits);
    }
    public static boolean fits(Level level,GraphOperation operation,ItemStack nativeCarrier,Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits) {
        var input=operation.primary();
        var plan=GraphEngine.plan(operation.graph(),Workpiece.read(level,input),operation.node().id(),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
        if(!plan.settled())return GraphOutputs.allPrimaryOutcomesFit(level,operation,nativeCarrier,outputs->{outputs.forEach(prepare);return fits.test(outputs);});
        if(plan.pool().isEmpty())return fits.test(List.of());
        // Materialize a tree once, not once per preceding sibling quantity combination.
        var roots=plan.pool().stream().map(node->prepare(level,node,prepare,limit)).toList();
        var items=new ArrayList<ItemStack>();
        for(var root:roots)if(!visit(root,items,()->fits.test(List.copyOf(items))))return false;
        return true;
    }
    private static Prepared prepare(Level level,OutputNode node,Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit) {
        if(node.chance()==0)return new Prepared(node,List.of(),List.of());
        if(node instanceof WeightedOutput output) {
            var maximum=NativeSteps.output(level,output);prepare.accept(maximum);
            var quantities=new ArrayList<ItemStack>();
            boolean constant=true;int expected=limit.applyAsInt(maximum);
            // Capacity queries for EVERY exact count have already run under the transaction's
            // baseline. Only proven constant limits let a maximum quantity dominate smaller ones.
            if(output.chance()<1)for(int count=1;count<output.count();count++)
                if(limit.applyAsInt(maximum.copyWithCount(count))!=expected){constant=false;break;}
            if(constant)quantities.add(maximum);
            else for(int count=1;count<=output.count();count++)quantities.add(maximum.copyWithCount(count));
            return new Prepared(node,List.copyOf(quantities),List.of());
        }
        return new Prepared(node,List.of(),((OutputGroup)node).results().stream().map(child->prepare(level,child,prepare,limit)).toList());
    }
    private static boolean visit(Prepared prepared,List<ItemStack> items,BooleanSupplier next) {
        var node=prepared.source();
        if(node.chance()==0)return next.getAsBoolean();
        if(node.chance()<1&&!next.getAsBoolean())return false;
        if(node instanceof WeightedOutput) {
            for(var quantity:prepared.quantities()) {
                items.add(quantity);
                try {if(!next.getAsBoolean())return false;}finally {items.removeLast();}
            }
            return true;
        }
        var group=(OutputGroup)node;
        if(group.mode()==OutputGroup.Mode.ALL)return all(prepared.children(),0,items,next);
        for(var child:prepared.children())if(!visit(child,items,next))return false;
        return true;
    }
    private static boolean all(List<Prepared> nodes,int index,List<ItemStack> items,BooleanSupplier next) {
        return index==nodes.size()?next.getAsBoolean():visit(nodes.get(index),items,()->all(nodes,index+1,items,next));
    }
}
