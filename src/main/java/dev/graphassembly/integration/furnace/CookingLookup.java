package dev.graphassembly.integration.furnace;

import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import java.util.*;

/** Read-only selection and conservative one-slot output planning. Nothing here burns fuel or rolls RNG. */
public final class CookingLookup {
    private CookingLookup() {}
    public record Selection(boolean handled,RecipeHolder<AbstractCookingRecipe> recipe,GraphOperation operation) {}
    public static boolean machine(AbstractFurnaceBlockEntity furnace) {
        var c=furnace.getClass(); return c==FurnaceBlockEntity.class||c==SmokerBlockEntity.class||c==BlastFurnaceBlockEntity.class;
    }
    public static Selection select(Level level,AbstractFurnaceBlockEntity furnace,RecipeType<?> type) {
        var input=furnace.getItem(0);
        if(input.isEmpty())return new Selection(false,null,null);
        if(!machine(furnace))return new Selection(Workpiece.has(input),null,null);
        return select(level,input,type);
    }
    /** One primary workpiece, shared by native cooking devices; no fuel/output-slot policy. */
    public static Selection select(Level level,ItemStack input,RecipeType<?> type) {
        return select(level,input,type,(operation,recipe)->true);
    }
    public static Selection select(Level level,ItemStack input,RecipeType<?> type,
        java.util.function.BiPredicate<GraphOperation,AbstractCookingRecipe> admitted) {
        if(input.isEmpty())return new Selection(false,null,null);
        var index=GraphIndex.get(level);
        dev.graphassembly.core.Progress progress;
        try {progress=Workpiece.read(level,input);}catch(RuntimeException e){return new Selection(true,null,null);}
        var entries=new ArrayList<GraphIndex.Entry>();
        if(progress==null)entries.addAll(index.entries(type));
        else {
            var graph=index.graphs().get(progress.graphId());
            if(graph==null)return new Selection(true,null,null);
            try {for(var id:GraphEngine.allowed(graph,progress))entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));}
            catch(RuntimeException e){return new Selection(true,null,null);}
        }
        Selection found=null;
        for(var entry:entries) {
            if(progress==null&&!entry.ingredient().test(input))continue;
            if(index.nativeType(entry.graph().id(),entry.node().id())!=type)continue;
            if(!WorkpieceInputs.matches(level,entry.graph(),entry.node(),0,input))continue;
            var recipe=CookingDefinition.create(level,entry.node(),input);
            var operation=new GraphOperation(level,entry.graph(),entry.node(),input,List.of());
            if(!admitted.test(operation,recipe))continue;
            if(found!=null)return new Selection(true,null,null);
            found=new Selection(true,new RecipeHolder<>(ResourceLocation.parse(entry.graph().id()),recipe),operation);
        }
        return found!=null?found:new Selection(progress!=null,null,null);
    }
    public static boolean fits(Level level,GraphOperation operation,AbstractFurnaceBlockEntity furnace) {
        return GraphOutputs.allPrimaryOutcomesFit(level,operation,outputs->merge(furnace.getItem(2),outputs,furnace.getMaxStackSize())!=null);
    }
    /** Empty bundles fit, multi-kind bundles never fit. Returns a new complete output stack. */
    public static ItemStack merge(ItemStack existing,List<ItemStack> outputs,int limit) {
        var merged=existing.copy();
        for(var output:outputs) {
            if(output.isEmpty())continue;
            if(merged.isEmpty())merged=output.copy();
            else {if(!ItemStack.isSameItemSameComponents(merged,output))return null;merged.grow(output.getCount());}
            if(merged.getCount()>Math.min(limit,merged.getMaxStackSize()))return null;
        }
        return merged;
    }
    public static int ordinaryTime(Level level,RecipeType<?> type,ItemStack input) {
        return ordinaryTime(level,type,input,200);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static int ordinaryTime(Level level,RecipeType<?> type,ItemStack input,int fallback) {
        return ((Optional<RecipeHolder<AbstractCookingRecipe>>)(Optional)level.getRecipeManager().getRecipeFor((RecipeType)type,new SingleRecipeInput(input),level)).map(holder->holder.value().getCookingTime()).orElse(fallback);
    }
    public static String identity(Level level,Selection selection,ItemStack input) {
        if(selection.operation()==null)return "ordinary:"+input.saveOptional(level.registryAccess());
        var op=selection.operation();
        var holder=level.getRecipeManager().byKey(ResourceLocation.parse(op.graph().id())).orElseThrow();
        String nativeIdentity="";
        if(op.node().recipe().has("recipe_id")) {
            var recipe=level.getRecipeManager().byKey(ResourceLocation.parse(op.node().recipe().get("recipe_id").getAsString())).orElseThrow().value();
            nativeIdentity=Recipe.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),recipe).getOrThrow().toString();
        }
        return com.google.common.hash.Hashing.sha256().hashString(op.graph().id()+"/"+op.node().id()+":"+((GraphRecipe)holder.value()).json()+":"+nativeIdentity+":"+input.saveOptional(level.registryAccess()),java.nio.charset.StandardCharsets.UTF_8).toString();
    }
}
