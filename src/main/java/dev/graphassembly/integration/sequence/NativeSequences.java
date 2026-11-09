package dev.graphassembly.integration.sequence;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.integration.RecipeSnapshot;
import dev.graphassembly.mixin.create.sequence.SequenceAccess;
import dev.graphassembly.recipe.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import java.util.*;

/** Native sequence step views for existing device adapters. Never registers/converts a graph recipe. */
public final class NativeSequences {
    private static final Map<RecipeManager,NativeSequences> CACHE=new WeakHashMap<>();
    private static final Set<RecipeManager> BUILDING=Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<GraphDefinition<OperationSpec>,Stage> stages=new IdentityHashMap<>();
    private final List<GraphIndex.Entry> entries=new ArrayList<>();
    private NativeSequences() {}
    public record Stage(RecipeHolder<SequencedAssemblyRecipe> holder,int index,SequenceStepRecipe descriptor,
                        GraphDefinition<OperationSpec> view,RecipeType<?> type,RecipeSnapshot definitions) {
        public boolean current(Level level) {
            var recipe=holder.value();
            return definitions.current(level)&&level.getRecipeManager().byKey(holder.id()).orElse(null)==holder
                &&index<recipe.getSequence().size()&&recipe.getSequence().get(index).getRecipe()==descriptor;
        }
        public boolean matches(ItemStack input) {
            if(input.isEmpty()||Workpiece.has(input))return false;
            var parent=holder.value();var state=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
            int step=state==null?0:state.step();
            return step>=0&&(long)step<(long)parent.getSequence().size()*parent.getLoops()
                &&step%parent.getSequence().size()==index
                &&((SequenceAccess)parent).graphAssembly$appliesTo(holder.id(),input);
        }
    }
    private static synchronized NativeSequences get(Level level) {
        var manager=level.getRecipeManager();var cached=CACHE.get(manager);if(cached!=null)return cached;
        if(!BUILDING.add(manager))throw new IllegalStateException("Reentrant native sequence discovery");
        try {
            var definitions=RecipeSnapshot.capture(manager);
            var built=new NativeSequences();
            RecipeType<SequencedAssemblyRecipe> sequenceType=AllRecipeTypes.SEQUENCED_ASSEMBLY.getType();
            for(var holder:manager.getAllRecipesFor(sequenceType)) {
                var parent=holder.value();
                if(parent.getSequence().stream().noneMatch(step->step.getRecipe() instanceof SequenceStepRecipe))continue;
                try {
                    if(parent.getSequence().isEmpty()||parent.getLoops()<1||(long)parent.getSequence().size()*parent.getLoops()>Integer.MAX_VALUE)
                        throw new IllegalArgumentException("Sequence needs a nonempty finite positive loop count");
                    var transit=parent.getTransitionalItem();
                    if(transit.isEmpty()||Workpiece.has(transit)||transit.has(AllDataComponents.SEQUENCED_ASSEMBLY))
                        throw new IllegalArgumentException("Transitional definition cannot contain saved assembly progress");
                    float weight=0;
                    for(var output:parent.resultPool) {
                        var stack=output.getStack();
                        if(!Float.isFinite(output.getChance())||output.getChance()<0||stack.isEmpty()||stack.getCount()>stack.getMaxStackSize()
                            ||Workpiece.has(stack)||stack.has(AllDataComponents.SEQUENCED_ASSEMBLY))throw new IllegalArgumentException("Invalid native sequence result");
                        weight+=output.getChance();
                    }
                    if(!Float.isFinite(weight)||weight<=0)throw new IllegalArgumentException("Sequence result weights must have a finite positive total");
                    var pending=new ArrayList<Stage>();
                    for(int i=0;i<parent.getSequence().size();i++) {
                        if(!(parent.getSequence().get(i).getRecipe() instanceof SequenceStepRecipe descriptor))continue;
                        var view=view(level,holder,descriptor,i);
                        var template=NativeSteps.template(level,view,view.nodes().values().iterator().next(),transit.copyWithCount(1));
                        pending.add(new Stage(holder,i,descriptor,view,template.getType(),definitions));
                    }
                    for(var stage:pending) {
                        built.stages.put(stage.view(),stage);
                        var entrance=CompoundIngredient.of(parent.getIngredient(),Ingredient.of(transit.getItem()));
                        built.entries.add(new GraphIndex.Entry(stage.view(),stage.view().nodes().values().iterator().next(),entrance));
                    }
                } catch(RuntimeException invalid) {GraphAssembly.LOGGER.error("Disabled native sequence steps {}: {}",holder.id(),invalid.getMessage());}
            }
            if(!definitions.current(manager))throw new IllegalStateException("Recipes changed during native sequence discovery");
            CACHE.put(manager,built);return built;
        } finally {BUILDING.remove(manager);}
    }
    /** The view carries one device operation only: no sequence topology, loops, outputs or persisted progress. */
    private static GraphDefinition<OperationSpec> view(Level level,RecipeHolder<SequencedAssemblyRecipe> holder,SequenceStepRecipe descriptor,int index) {
        var parent=holder.value();var json=new JsonObject();
        json.add("ingredient",Ingredient.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),parent.getIngredient()).getOrThrow());
        json.addProperty("transitional_item",BuiltInRegistries.ITEM.getKey(parent.getTransitionalItem().getItem()).toString());
        var node=descriptor.step();node.addProperty("id","sequence_step_"+index);node.addProperty("start",true);node.addProperty("finish",true);
        var nodes=new JsonArray();nodes.add(node);json.add("nodes",nodes);json.add("edges",new JsonArray());
        return GraphCodec.parse(holder.id().toString(),json);
    }
    public static synchronized void release(RecipeManager manager){CACHE.remove(manager);}
    public static Stage stage(Level level,GraphDefinition<OperationSpec> view){return get(level).stages.get(view);}
    public static List<GraphIndex.Entry> entries(Level level,GraphIndex graphIndex,RecipeType<?> type) {
        var result=new ArrayList<>(graphIndex.entries(type));
        for(var entry:get(level).entries)if(stage(level,entry.graph()).type()==type)result.add(entry);
        return result;
    }
    public static List<GraphIndex.Entry> allEntries(Level level,GraphIndex index) {
        var result=new ArrayList<GraphIndex.Entry>();
        index.graphs().values().forEach(graph->graph.nodes().values().forEach(node->result.add(new GraphIndex.Entry(graph,node,null))));
        result.addAll(get(level).entries);return result;
    }
    public static RecipeType<?> type(Level level,GraphIndex index,GraphIndex.Entry entry) {
        var stage=stage(level,entry.graph());return stage==null?index.nativeType(entry.graph().id(),entry.node().id()):stage.type();
    }
    public static boolean currentStep(Level level,ItemStack input) {
        return get(level).stages.values().stream().anyMatch(stage->stage.matches(input));
    }
    /** Inspect routing only: calling Create getRecipe here would mutate its enforced result supplier. */
    public static boolean allowsUpstream(Level level,ItemStack input,RecipeType<?> type,Class<?> clazz) {
        if(Workpiece.has(input))return false;
        var progress=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if(progress==null)return false;
        var holder=level.getRecipeManager().byKey(progress.id()).orElse(null);
        if(holder==null||!(holder.value() instanceof SequencedAssemblyRecipe parent))return false;
        // Entirely upstream parents keep their original Create routing, without imposing
        // new host policy on ordinary sequences which do not contain our descriptors.
        if(parent.getSequence().stream().noneMatch(step->step.getRecipe() instanceof SequenceStepRecipe))return true;
        // A mixed parent disabled during template validation must not reopen ordinary
        // processing, even while this saved carrier is waiting for an upstream stage.
        if(get(level).stages.values().stream().noneMatch(stage->stage.holder()==holder&&stage.current(level)))return false;
        int step=progress.step(),size=parent.getSequence().size();
        if(size==0||step<0||(long)step>=(long)size*parent.getLoops()
            ||!((SequenceAccess)parent).graphAssembly$appliesTo(holder.id(),input))return false;
        var expected=parent.getSequence().get(step%size).getRecipe();
        return !(expected instanceof SequenceStepRecipe)&&expected.getType()==type&&clazz.isInstance(expected);
    }
    /** Mixed-parent delegation also requires the host's complete material/fluid admission. */
    public static <R extends Recipe<?>> boolean allowsUpstream(Level level,ItemStack input,RecipeType<R> type,Class<R> clazz,
                                                              java.util.function.Predicate<RecipeHolder<R>> matches) {
        var definitions=RecipeSnapshot.capture(level);
        var before=input.copy();
        if(!allowsUpstream(level,input,type,clazz))return false;
        var progress=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        var holder=level.getRecipeManager().byKey(progress.id()).orElse(null);
        if(holder==null||!(holder.value() instanceof SequencedAssemblyRecipe parent))return false;
        if(parent.getSequence().stream().noneMatch(step->step.getRecipe() instanceof SequenceStepRecipe))return true;
        var expected=parent.getSequence().get(progress.step()%parent.getSequence().size()).getRecipe();
        try {
            // A temporary holder exposes only the unmodified expected recipe. Do not use
            // Create getRecipe: it installs an enforced-result closure on the shared object.
            return matches.test(new RecipeHolder<>(holder.id(),clazz.cast(expected)))
                &&definitions.current(level)&&ItemStack.matches(before,input);
        } catch(RuntimeException invalidAdmission) {return false;}
    }
    /** Activation receives an already selected recipe: matching only its family is insufficient. */
    public static boolean allowsUpstreamActivation(Level level,ItemStack input,Recipe<?> recipe) {
        var progress=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if(progress==null)return true;
        if(!allowsUpstream(level,input,recipe.getType(),recipe.getClass()))return false;
        var holder=level.getRecipeManager().byKey(progress.id()).orElse(null);
        if(holder==null||!(holder.value() instanceof SequencedAssemblyRecipe parent))return false;
        if(parent.getSequence().stream().noneMatch(step->step.getRecipe() instanceof SequenceStepRecipe))return true;
        // Inspection alone must not install/replace Create's shared enforced-result supplier.
        return parent.getSequence().get(progress.step()%parent.getSequence().size()).getRecipe()==recipe;
    }
    public static boolean tracked(ItemStack stack){return Workpiece.has(stack)||stack.has(AllDataComponents.SEQUENCED_ASSEMBLY);}
}
