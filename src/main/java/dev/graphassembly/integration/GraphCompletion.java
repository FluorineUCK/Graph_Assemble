package dev.graphassembly.integration;

import dev.graphassembly.core.io.*;
import dev.graphassembly.core.route.RouteState;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonObject;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/** Shared prepared graph settlement for native machines/menus: external codecs run before RNG. */
public final class GraphCompletion implements OperationCompletion {
    private record Key(String item,JsonObject components) {}
    private final TransitionPlan plan;
    private final Map<Key,ItemStack> templates;
    private final ItemStack carrier;
    private final ItemStack fallback;
    private GraphCompletion(TransitionPlan plan,Map<Key,ItemStack> templates,ItemStack carrier,ItemStack fallback){this.plan=plan;this.templates=Map.copyOf(templates);this.carrier=carrier.copy();this.fallback=fallback.copyWithCount(1);}
    public static GraphCompletion prepare(Level level,GraphOperation operation) {
        return prepare(level,operation,ItemStack.EMPTY);
    }
    /** Prepared native result changes only the nonterminal carrier, never primary graph state. */
    public static GraphCompletion prepare(Level level,GraphOperation operation,ItemStack nativeCarrier) {
        var primary=operation.primary();String item=BuiltInRegistries.ITEM.getKey(primary.getItem()).toString();
        var plan=GraphPipeline.plan(operation.graph(),Workpiece.read(level,primary),operation.node().id(),item);
        var templates=new HashMap<Key,ItemStack>();
        if(plan instanceof TransitionPlan.SettlementPlan settlement)for(var output:OutputTrees.leaves(settlement.outputPool())) {
            var key=new Key(output.item(),output.components());
            // Decode each distinct complete component definition before the final validation/RNG boundary.
            templates.computeIfAbsent(key,ignored->NativeSteps.output(level,output));
        }
        var carrier = switch (plan) {
            case TransitionPlan.SettlementPlan ignored -> ItemStack.EMPTY;
            case TransitionPlan.ContinuationPlan continuation -> !nativeCarrier.isEmpty() ? nativeCarrier.copy()
                : continuation.carrierItem().equals(item) ? primary : NativeSteps.stack(continuation.carrierItem());
        };
        return new GraphCompletion(plan,templates,carrier,primary);
    }
    /** Every possible decoded outcome is checked before drawing any graph randomness. */
    public boolean outputsEnabled(Level level) {
        return (carrier.isEmpty()||carrier.isItemEnabled(level.enabledFeatures()))
            &&templates.values().stream().allMatch(stack->stack.isItemEnabled(level.enabledFeatures()));
    }
    /** Brewing bottle slots admit at most one item, not merely one stack. */
    public boolean fitsSingleItem(Level level) {
        if (!outputsEnabled(level)) return false;
        if (!(plan instanceof TransitionPlan.SettlementPlan settlement)) return !carrier.isEmpty() && carrier.getCount()==1 && carrier.getMaxStackSize()>=1;
        return OutputTrees.allMaximalOutcomesFit(settlement.outputPool(), outputs -> {
            long count=0;
            for(var output:outputs) {
                var template=templates.get(new Key(output.item(),output.components()));
                if(template==null || template.isEmpty() || output.count()>template.getMaxStackSize())return false;
                count+=output.count();
            }
            return count<=1;
        });
    }
    public boolean terminal() { return plan instanceof TransitionPlan.SettlementPlan; }
    public int maxStacks() {
        return plan instanceof TransitionPlan.SettlementPlan settlement ? OutputTrees.maxStacks(settlement.outputPool()) : 1;
    }
    private ItemStack materialize(OutputItem output) {
        var template=templates.get(new Key(output.item(),output.components()));
        if(template==null)throw new IllegalStateException("Unprepared graph outcome");
        return template.copyWithCount(output.count());
    }
    private ItemStack materialize(WeightedOutput output) {
        return materialize(new OutputItem(output.item(),output.count(),output.components()));
    }
    /** Representative only: never selects a random branch or changes the prepared state. */
    public ItemStack preview() {
        if(plan instanceof TransitionPlan.ContinuationPlan continuation) {
            var state=continuation.progress();
            if(!continuation.randomBranches().isEmpty())state=state.withRouting(new RouteState(
                continuation.randomBranches().getFirst().to(),state.routing().pendingRoute()));
            return Workpiece.write(carrier,state);
        }
        var leaves=OutputTrees.leaves(((TransitionPlan.SettlementPlan)plan).outputPool());
        return leaves.isEmpty()?fallback.copy():materialize(leaves.getFirst());
    }
    /** Monotone capacity checks can use maximal bundles, including every continuation route. */
    public boolean allMaximalOutcomesFit(Predicate<List<ItemStack>> fits) {
        if(plan instanceof TransitionPlan.SettlementPlan settlement)
            return OutputTrees.allMaximalOutcomesFit(settlement.outputPool(),outputs->fits.test(outputs.stream().map(this::materialize).toList()));
        var continuation=(TransitionPlan.ContinuationPlan)plan;
        var state=continuation.progress();
        if(continuation.randomBranches().isEmpty())return fits.test(List.of(Workpiece.write(carrier,state)));
        for(var target:continuation.randomBranches().stream().map(edge->edge.to()).distinct().toList())
            if(!fits.test(List.of(Workpiece.write(carrier,state.withRouting(new RouteState(target,state.routing().pendingRoute()))))))return false;
        return true;
    }
    private record Outcome(OutputNode source,List<ItemStack> quantities,List<Outcome> children) {}
    /** Non-monotone menu limits also check optional leading results and exact quantities. */
    public boolean allOutcomesFit(Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits) {
        if(!(plan instanceof TransitionPlan.SettlementPlan settlement))return allMaximalOutcomesFit(outputs->{outputs.forEach(prepare);return fits.test(outputs);});
        if(settlement.outputPool().isEmpty())return fits.test(List.of());
        var roots=settlement.outputPool().stream().map(node->prepareOutcome(node,prepare,limit)).toList();
        var items=new ArrayList<ItemStack>();
        for(var root:roots)if(!visit(root,items,()->fits.test(List.copyOf(items))))return false;
        return true;
    }
    private Outcome prepareOutcome(OutputNode node,Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit) {
        if(node.chance()==0)return new Outcome(node,List.of(),List.of());
        if(node instanceof WeightedOutput output) {
            var maximum=materialize(output);prepare.accept(maximum);
            var quantities=new ArrayList<ItemStack>();
            boolean constant=true;int expected=limit.applyAsInt(maximum);
            // Only proven constant limits let a maximal quantity dominate smaller ones.
            if(output.chance()<1)for(int count=1;count<output.count();count++)
                if(limit.applyAsInt(maximum.copyWithCount(count))!=expected){constant=false;break;}
            if(constant)quantities.add(maximum);
            else for(int count=1;count<=output.count();count++)quantities.add(maximum.copyWithCount(count));
            return new Outcome(node,List.copyOf(quantities),List.of());
        }
        return new Outcome(node,List.of(),((OutputGroup)node).children().stream().map(child->prepareOutcome(child,prepare,limit)).toList());
    }
    private static boolean visit(Outcome prepared,List<ItemStack> items,BooleanSupplier next) {
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
    private static boolean all(List<Outcome> nodes,int index,List<ItemStack> items,BooleanSupplier next) {
        return index==nodes.size()?next.getAsBoolean():visit(nodes.get(index),items,()->all(nodes,index+1,items,next));
    }
    public List<ItemStack> roll(RandomSource random) {
        var completion=GraphPipeline.resolve(plan, random::nextDouble);
        if(completion instanceof TransitionResult.Continued continued)return List.of(Workpiece.write(carrier,continued.progress()));
        return ((TransitionResult.Settled)completion).outputs().stream().map(this::materialize).toList();
    }
}
