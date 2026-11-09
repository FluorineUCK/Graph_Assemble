package dev.graphassembly.integration.bridge;

import com.simibubi.create.content.processing.recipe.*;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.*;

/** Host-owned candidate: explicit slot assignment, one prepared result session, scoped handoff. */
public final class CandidateBinding implements AutoCloseable {
    public interface Attached { CandidateBinding graphAssembly$binding();void graphAssembly$binding(CandidateBinding b);int graphAssembly$outputLimit(); }
    private final ProcessingRecipe<?,?> recipe,source;
    private final GraphGateIngredient gate;
    private final Level level;
    private final RecipeSnapshot definitions;
    private final Object epoch;
    private final InventoryProof.Origin[] origins;
    private final ItemStack[] inputs;
    private final ResultReceipt.Scope handoff=new ResultReceipt.Scope();
    private boolean bound,closed;
    private List<ProcessingOutput> outputs;
    private List<ItemStack> results;
    private int primaryCount;

    private CandidateBinding(Level level,ProcessingRecipe<?,?> recipe,ProcessingRecipe<?,?> source,GraphGateIngredient gate) {
        this.level=level;this.recipe=recipe;this.source=source;this.gate=gate;
        definitions=RecipeSnapshot.capture(level);epoch=InventoryProof.epoch();
        origins=new InventoryProof.Origin[recipe.getIngredients().size()];inputs=new ItemStack[origins.length];
        Arrays.fill(inputs,ItemStack.EMPTY);
    }
    /** A verified host supplies its actual physical inputs once, before any native matching probes. */
    public boolean bind(List<ItemStack> physical) {
        if(bound||closed||epoch==null||epoch!=InventoryProof.epoch()||!definitions.current(level))return false;
        var proofs=new ArrayList<InventoryProof.Origin>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<InventoryProof.Origin,Boolean>());
        for(var stack:physical) {
            var origin=((InventoryProof.StackMeta)(Object)stack).graphAssembly$origin();
            if(!stack.isEmpty()&&(origin==null||origin.level()!=level||!origin.valid()
                ||!ItemStack.isSameItemSameComponents(stack,origin.live())||stack.getCount()!=origin.count
                ||(Workpiece.has(stack)&&stack.getCount()!=1)||!seen.add(origin)))return false;
            proofs.add(origin);
        }
        var roles=java.util.stream.IntStream.range(0,inputs.length).filter(i->!recipe.getIngredients().get(i).isEmpty()).toArray();
        var bases=Arrays.stream(roles).mapToObj(i->((GraphGateIngredient)recipe.getIngredients().get(i).getCustomIngredient()).base).toList();
        var assigned=ItemInputBinding.bind(bases,physical,
            (i,stack)->WorkpieceInputs.matches(level,gate.graph,gate.graph.nodes().get(gate.nodeId),roles[i],stack),
            i->roles[i]==0||gate.graph.nodes().get(gate.nodeId).inputs().workpieceInput(roles[i])!=null);
        if(assigned.status()!=InputAssignment.Status.UNIQUE)return false;
        var slots=assigned.slots();net.minecraft.world.level.block.entity.BlockEntity machine=null;
        for(int i=0;i<roles.length;i++) {
            var origin=proofs.get(slots[i]);
            if(origin==null||(machine!=null&&machine!=origin.machine.get()))return false;
            machine=origin.machine.get();
        }
        for(int i=0;i<roles.length;i++){origins[roles[i]]=proofs.get(slots[i]);inputs[roles[i]]=physical.get(slots[i]).copyWithCount(1);}
        bound=true;return valid();
    }
    /** Ingredient.test observes an existing assignment; it cannot capture or lock a candidate slot. */
    private boolean matches(int role,ItemStack stack) {
        return valid()&&origins[role]!=null
            &&((InventoryProof.StackMeta)(Object)stack).graphAssembly$origin()==origins[role]
            &&ItemStack.isSameItemSameComponents(inputs[role],stack);
    }
    private boolean valid() {
        if(!bound||closed||epoch!=InventoryProof.epoch()||!definitions.current(level))return false;
        for(int i=0;i<origins.length;i++)if(origins[i]==null?!recipe.getIngredients().get(i).isEmpty():!origins[i].valid())return false;
        return origins.length>0&&origins[0]!=null;
    }
    /** Explicit adapter entry point, not registration or automatic machine admission. */
    public static ProcessingRecipe<?,?> prepare(Level level,ProcessingRecipe<?,?> source) {
        if(level.isClientSide()||InventoryProof.epoch()==null)throw new IllegalStateException("Candidate needs a server execution context");
        if(source.getIngredients().isEmpty()||!(source.getIngredients().getFirst().getCustomIngredient() instanceof GraphGateIngredient gate)||gate.graph==null)
            throw new IllegalArgumentException("Candidate needs a graph definition");
        var holder=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(gate.graphId)).orElse(null);
        if(holder==null||!(holder.value() instanceof GraphRecipe graph)||!graph.json().equals(com.google.gson.JsonParser.parseString(gate.definition))
            ||!GraphIndex.get(level).graphs().containsKey(gate.graphId))throw new IllegalArgumentException("Stale candidate definition");
        var copy=(ProcessingRecipe<?,?>)NativeRecipeTemplates.copy(level,source);
        var binding=new CandidateBinding(level,copy,source,gate);
        for(int i=0;i<copy.getIngredients().size();i++) {
            if(copy.getIngredients().get(i).isEmpty())continue;
            if(!(copy.getIngredients().get(i).getCustomIngredient() instanceof GraphGateIngredient own)||own.role!=i
                ||!own.graphId.equals(gate.graphId)||!own.nodeId.equals(gate.nodeId)||!own.definition.equals(gate.definition))
                throw new IllegalArgumentException("Incomplete candidate ingredient definition");
            int role=i;own.proof=stack->binding.matches(role,stack);
        }
        ((Attached)copy).graphAssembly$binding(binding);return copy;
    }
    public static Object fresh(Object value) {
        if(!(value instanceof ProcessingRecipe<?,?> source)||InventoryProof.epoch()==null)return value;
        var first=source.getIngredients().stream().map(i->i.getCustomIngredient()).filter(i->i instanceof GraphGateIngredient g&&g.graph!=null).findFirst();
        if(first.isEmpty())return value;
        var gate=(GraphGateIngredient)first.get();var level=ServerLifecycleHooks.getCurrentServer().overworld();
        var current=NativeRecipeCatalog.projection(level,gate.graphId,gate.nodeId);
        return current==null||current.getClass()!=source.getClass()?value:prepare(level,current);
    }
    /** Raw identities are sorted before lazily materializing graph-owned candidates. */
    public static <T> List<T> candidates(List<T> raw) {
        if(InventoryProof.epoch()==null||raw.stream().noneMatch(x->x instanceof ProcessingRecipe<?,?> p&&p.getIngredients().stream().anyMatch(i->i.getCustomIngredient() instanceof GraphGateIngredient g&&g.graph!=null)))return raw;
        return new AbstractList<>() {
            final IdentityHashMap<T,T> clones=new IdentityHashMap<>();
            public int size(){return raw.size();}
            @SuppressWarnings("unchecked") public T get(int i){var x=raw.get(i);return clones.computeIfAbsent(x,v->(T)fresh(v));}
            @Override public void sort(Comparator<? super T> c){raw.sort(c);}
        };
    }
    public List<ProcessingOutput> outputs() {
        if(!valid())return List.of();
        if(outputs!=null)return outputs;
        var op=new GraphOperation(level,gate.graph,gate.graph.nodes().get(gate.nodeId),inputs[0],Arrays.asList(inputs).subList(1,inputs.length));
        var prepared=GraphOutputs.prepare(level,op,source);
        var primaryKind=prepared.primary().terminal()?ResultReceipt.Kind.TERMINAL:ResultReceipt.Kind.CARRIER;
        var envelope=new OutputEnvelope();
        prepared.primary().allMaximalOutcomesFit(batch->{
            envelope.include(batch);
            for(int first=0;first<batch.size();first++) {
                var suffix=new ArrayList<>(batch.subList(first,batch.size()));
                var item=ResultReceipt.protect(suffix.getFirst(),primaryKind,handoff);
                item.applyComponents(op.primary().getComponentsPatch());suffix.set(0,item);envelope.include(suffix);
            }
            return true;
        });
        var previews=new ArrayList<>(envelope.stacks());int primaryPreviews=previews.size();
        for(var byproduct:prepared.byproducts())previews.add(byproduct.getStack().copy());
        int maximum=Math.max(previews.size(),Math.addExact(prepared.primary().maxStacks(),prepared.byproducts().size()));
        if(maximum>((Attached)recipe).graphAssembly$outputLimit()||!prepared.outputsEnabled(level)||!valid())return outputs=List.of();
        for(var origin:origins)if(origin!=null)origin.graphClaimed=true;
        var slots=new ArrayList<ProcessingOutput>();
        for(int i=0;i<maximum;i++) {
            final int index=i;
            slots.add(new ProcessingOutput(ItemStack.EMPTY,1) {
                @Override public ItemStack getStack(){return valid()&&index<previews.size()?ResultReceipt.protect(previews.get(index),index>=primaryPreviews?ResultReceipt.Kind.BYPRODUCT:primaryKind,handoff):ItemStack.EMPTY;}
                @Override public ItemStack rollOutput(RandomSource random) {
                    if(!valid())return ItemStack.EMPTY;
                    if(results==null) {
                        var all=new ArrayList<>(prepared.primary().roll(random));primaryCount=all.size();
                        for(var byproduct:prepared.byproducts()){var out=byproduct.rollOutput(random);if(!out.isEmpty())all.add(out);}
                        results=List.copyOf(all);
                    }
                    return index<results.size()?ResultReceipt.protect(results.get(index),index>=primaryCount?ResultReceipt.Kind.BYPRODUCT:primaryKind,handoff):ItemStack.EMPTY;
                }
            });
        }
        return outputs=List.copyOf(slots);
    }
    @Override public void close(){closed=true;handoff.close();}
}
