package dev.graphassembly.integration.vanilla.anvil;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.integration.sequence.NativeSequences;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Fixed left/right logical roles; the host alone supplies native result, cost and consumption. */
public final class AnvilLookup {
    private AnvilLookup() {}
    public record Native(ItemStack result,int cost,int consumed) {
        public Native {result=result.copy();}
        @Override public ItemStack result(){return result.copy();}
    }
    public record Lookup(boolean handled,GraphOperation operation) {}
    public static Lookup find(Level level,List<ItemStack> inputs,Native nativeResult) {
        var left=inputs.get(0);var right=inputs.get(1);
        boolean claimed=dev.graphassembly.integration.sequence.NativeSequences.tracked(left)||dev.graphassembly.integration.sequence.NativeSequences.tracked(right);
        if(left.isEmpty())return new Lookup(claimed,null);
        var index=GraphIndex.get(level);var entries=new ArrayList<GraphIndex.Entry>();
        var progress=Workpiece.read(level,left);
        if(progress==null)entries.addAll(NativeSequences.entries(level,index,GraphAssembly.ANVIL_TYPE.get()));
        else {
            var graph=index.graphs().get(progress.graphId());
            if(graph!=null)for(String id:GraphPipeline.allowed(graph,progress))entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));
        }
        var candidates=new ArrayList<GraphOperation>();
        for(var entry:entries) {
            if(NativeSequences.type(level,index,entry)!=GraphAssembly.ANVIL_TYPE.get())continue;
            if(progress==null&&!entry.ingredient().test(left))continue;
            claimed=true; // Raw entrance failures cannot unlock an ordinary anvil result.
            var descriptor=AnvilDefinition.create(level,entry.node());
            if(left.getCount()!=1||nativeResult.result().getCount()!=1||nativeResult.cost()<=0
                ||!nativeResult.result().isItemEnabled(level.enabledFeatures()))continue;
            if(descriptor.action()==AnvilRecipe.Action.RENAME) {
                if(!right.isEmpty()||nativeResult.consumed()!=0)continue;
            } else if(right.isEmpty()||nativeResult.consumed()<=0)continue;
            if(descriptor.json().has("addition")&&!NativeSteps.ingredient(level,descriptor.json().get("addition")).test(right))continue;
            if(!WorkpieceInputs.matches(level,entry.graph(),entry.node(),0,left)||!WorkpieceInputs.matches(level,entry.graph(),entry.node(),1,right))continue;
            if(dev.graphassembly.integration.sequence.NativeSequences.tracked(right)&&nativeResult.consumed()!=1)continue;
            var result=nativeResult.result();
            boolean sameItem=ItemStack.isSameItem(left,result);
            boolean repaired=sameItem&&left.isDamageableItem()&&result.getDamageValue()<left.getDamageValue();
            boolean enchanted=sameItem&&enhanced(left,result);
            boolean effect=switch(descriptor.action()) {
                case REPAIR -> repaired;
                case ENCHANT -> enchanted;
                case WORK -> repaired||enchanted;
                case RENAME -> renamed(left,result,descriptor);
                case COMBINE -> combined(left,result);
            };
            if(!effect)continue;
            candidates.add(new GraphOperation(level,entry.graph(),entry.node(),left.copy(),List.of(right.copy())));
        }
        return new Lookup(claimed,candidates.size()==1?candidates.getFirst():null);
    }
    private static boolean combined(ItemStack source,ItemStack result) {
        var before=source.copy();var after=result.copy();
        // Host output is authoritative. Ignore only effects explicitly insufficient for combine.
        before.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        after.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        before.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        after.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        before.remove(GraphAssembly.PROGRESS.get());after.remove(GraphAssembly.PROGRESS.get());
        return !ItemStack.isSameItemSameComponents(before,after);
    }
    private static boolean renamed(ItemStack source,ItemStack result,AnvilRecipe descriptor) {
        var before=source.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        var after=result.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if(Objects.equals(before,after))return false;
        if(!descriptor.json().has("name"))return true;
        String exact=descriptor.json().get("name").getAsString();
        return Objects.equals(after,exact.isEmpty()?null:net.minecraft.network.chat.Component.literal(exact));
    }
    private static boolean enhanced(ItemStack source,ItemStack result) {
        for(var type:List.of(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) {
            var before=source.getOrDefault(type,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            var after=result.getOrDefault(type,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for(var enchantment:after.keySet())if(after.getLevel(enchantment)>before.getLevel(enchantment))return true;
        }
        return false;
    }
}
