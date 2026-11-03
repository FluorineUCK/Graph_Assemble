package dev.graphassembly.integration.projection;

import com.simibubi.create.content.processing.recipe.*;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.*;

/** Query-local native clone; no mutable singleton template and no dynamically leaked operation scope. */
public final class CandidateBinding {
    public interface Attached { CandidateBinding graphAssembly$binding();void graphAssembly$binding(CandidateBinding b);int graphAssembly$outputLimit(); }
    final ProcessingRecipe<?,?> recipe,source;
    final GraphGateIngredient gate;
    final Object epoch;
    final InventoryProof.Origin[] origins;
    final ItemStack[] inputs;
    final List<ProcessingOutput> nativeOutputs;
    private CandidateBinding(ProcessingRecipe<?,?> recipe,ProcessingRecipe<?,?> source,GraphGateIngredient gate){
        this.recipe=recipe;this.source=source;this.gate=gate;epoch=InventoryProof.epoch();
        origins=new InventoryProof.Origin[recipe.getIngredients().size()];inputs=new ItemStack[origins.length];nativeOutputs=List.copyOf(recipe.getRollableResults());
    }
    private boolean capture(int role,ItemStack stack){
        var origin=((InventoryProof.StackMeta)(Object)stack).graphAssembly$origin();
        if(epoch!=InventoryProof.epoch()||origin==null||!origin.valid()||stack.isEmpty()||(Workpiece.has(stack)&&stack.getCount()!=1)
            ||!ItemStack.isSameItemSameComponents(stack,origin.live()))return false;
        // All logical roles must originate in the same machine, not just any inventory
        // observed on the server thread. Use its real dimension for progress migration.
        for(var captured:origins)if(captured!=null&&captured.machine.get()!=origin.machine.get())return false;
        if(!WorkpieceInputs.matches(origin.level(),gate.graph,gate.graph.nodes().get(gate.nodeId),role,stack))return false;
        // Do not silently bind a previously tested candidate to another machine/slot.
        if(origins[role]!=null&&origins[role]!=origin)return false;
        origins[role]=origin;inputs[role]=stack.copyWithCount(1);return true;
    }
    private boolean valid(){
        if(epoch!=InventoryProof.epoch())return false;
        for(int i=0;i<origins.length;i++)if(origins[i]==null ? !recipe.getIngredients().get(i).isEmpty() : !origins[i].valid())return false;
        return true;
    }
    public static Object fresh(Object value){
        if(!(value instanceof ProcessingRecipe<?,?> source)||InventoryProof.epoch()==null)return value;
        var first=source.getIngredients().stream().map(i->i.getCustomIngredient()).filter(i->i instanceof GraphGateIngredient g&&g.graph!=null).findFirst();
        if(first.isEmpty())return value;
        var gate=(GraphGateIngredient)first.get();var server=ServerLifecycleHooks.getCurrentServer();var level=server.overworld();
        // Refresh an existing graph/node identity even if a native cache retained its old template.
        var current=NativeRecipeCatalog.projection(level,gate.graphId,gate.nodeId);
        if(current==null||current.getClass()!=source.getClass())return value;
        source=current;
        gate=(GraphGateIngredient)source.getIngredients().getFirst().getCustomIngredient();
        var copy=(ProcessingRecipe<?,?>)dev.graphassembly.integration.NativeRecipeTemplates.copy(level,source);var binding=new CandidateBinding(copy,source,gate);
        for(int i=0;i<copy.getIngredients().size();i++){
            if(copy.getIngredients().get(i).isEmpty()){binding.inputs[i]=ItemStack.EMPTY;continue;}
            if(!(copy.getIngredients().get(i).getCustomIngredient() instanceof GraphGateIngredient own)||own.role!=i)throw new IllegalStateException("Incomplete projected ingredient definition");
            int role=i;own.proof=s->binding.capture(role,s);
        }
        ((Attached)copy).graphAssembly$binding(binding);return copy;
    }
    /** Raw identities are sorted before lazily materializing graph-owned candidates. */
    public static <T> List<T> candidates(List<T> raw){
        if(InventoryProof.epoch()==null||raw.stream().noneMatch(x->x instanceof ProcessingRecipe<?,?> p&&p.getIngredients().stream().anyMatch(i->i.getCustomIngredient() instanceof GraphGateIngredient g&&g.graph!=null)))return raw;
        return new AbstractList<>(){
            final IdentityHashMap<T,T> clones=new IdentityHashMap<>();
            public int size(){return raw.size();}
            @SuppressWarnings("unchecked") public T get(int i){var x=raw.get(i);return clones.computeIfAbsent(x,v->(T)fresh(v));}
            @Override public void sort(Comparator<? super T> c){raw.sort(c);}
        };
    }
    public List<ProcessingOutput> outputs(){
        if(!valid())return List.of();
        var level=origins[0].level();
        var op=new GraphOperation(level,gate.graph,gate.graph.nodes().get(gate.nodeId),inputs[0],Arrays.asList(inputs).subList(1,inputs.length));
        var envelope=new OutputEnvelope();GraphOutputs.allPrimaryOutcomesFit(level,op,batch->{envelope.include(batch);for(int first=0;first<batch.size();first++){var suffix=new ArrayList<>(batch.subList(first,batch.size()));var item=ResultReceipt.protect(suffix.getFirst(),false);item.applyComponents(op.primary().getComponentsPatch());suffix.set(0,item);envelope.include(suffix);}return true;});
        var previews=new ArrayList<>(envelope.stacks());int primaryPreviews=previews.size();
        for(int i=1;i<nativeOutputs.size();i++)previews.add(nativeOutputs.get(i).getStack().copy());
        int maximum=Math.max(previews.size(),GraphOutputs.maxStacks(level,op,source));
        if(maximum>((Attached)recipe).graphAssembly$outputLimit())return List.of();
        for(var origin:origins)if(origin!=null)origin.graphClaimed=true;
        class ReadSession {
            List<ItemStack> results;int primaryCount;
            List<ItemStack> roll(RandomSource random){
                if(!valid())return List.of();
                if(results==null){var all=new ArrayList<>(GraphRuntime.completeAll(level,gate.graph,gate.nodeId,Workpiece.read(inputs[0]),inputs[0],random));primaryCount=all.size();for(int i=1;i<nativeOutputs.size();i++){var out=nativeOutputs.get(i).rollOutput(random);if(!out.isEmpty())all.add(out);}results=List.copyOf(all);}return results;
            }
        }
        var session=new ReadSession();var slots=new ArrayList<ProcessingOutput>();
        for(int i=0;i<maximum;i++){final int index=i;slots.add(new ProcessingOutput(ItemStack.EMPTY,1){
            @Override public ItemStack getStack(){return valid()&&index<previews.size()?ResultReceipt.protect(previews.get(index),index>=primaryPreviews):ItemStack.EMPTY;}
            @Override public ItemStack rollOutput(RandomSource random){var all=session.roll(random);return index<all.size()?ResultReceipt.protect(all.get(index),index>=session.primaryCount):ItemStack.EMPTY;}
        });}return slots;
    }
}
