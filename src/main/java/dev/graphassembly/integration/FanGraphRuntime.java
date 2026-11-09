package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import com.simibubi.create.content.kinetics.fan.processing.*;
import dev.graphassembly.integration.vanilla.furnace.*;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.recipe.Workpiece;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/** Call-local graph settlement; Create still owns exposure timers, -1 locks and delivery. */
public final class FanGraphRuntime {
    private FanGraphRuntime() {}
    private static final ThreadLocal<Deque<Scope>> CALLS=ThreadLocal.withInitial(ArrayDeque::new);
    private record Timer(String job,ItemStack input,GraphDefinition<OperationSpec> definition,boolean completed,java.lang.ref.WeakReference<Level> world) {}
    // Identity metadata only, never an authorization/settlement plan. Weak actor keys cannot be
    // retained by the values (including indirectly through a strong reference to the world).
    private static final Map<Object,Timer> TIMERS=Collections.synchronizedMap(new WeakHashMap<>());
    public static final class Scope implements AutoCloseable {
        private final Object key;
        private final Level level;
        private final Supplier<ItemStack> live;
        private final BooleanSupplier attached;
        private final ItemStack input;
        private final List<RecipeHolder<?>> definitions;
        private Selection selected;
        private FanProcessingType type;
        private String job="";
        private boolean invalid,restart,lockedOutput,completed,aborted;
        private Scope(Object key,Level level,Supplier<ItemStack> live,BooleanSupplier attached) {
            this.key=key;this.level=level;this.live=live;this.attached=attached;
            input=live.get().copy();definitions=new ArrayList<>(level.getRecipeManager().getRecipes());
        }
        public boolean allowed(){return current()&&(!selected.handled()||selected.operation()!=null);}
        public boolean freshEligibility() {
            return selected.operation()!=null||type.canProcess(input,level);
        }
        public boolean restarting(){return restart;}
        private boolean completedInput(Timer old) {
            // Native ItemEntity merging may grow a completed equal-component output. Only
            // completed identity ignores count; active snapshots/job hashes stay count-exact.
            return old!=null&&old.completed&&ItemStack.isSameItemSameComponents(old.input,input)
                &&(old.world.get()==level||old.definition==null&&old.world.get()==null);
        }
        private void bindTimer() {
            var old=TIMERS.get(key);
            if(selected.operation()==null) {
                lockedOutput=completedInput(old);
                restart=!selected.handled()&&old!=null&&!lockedOutput;
                return;
            }
            job=CookingLookup.identity(level,selected.operation(),input);
            var graph=selected.operation().graph();
            String saved=old==null?"":old.job;
            if(key instanceof ItemEntity entity) {
                var data=entity.getPersistentData().getCompound("CreateData");
                saved=data.getString("GraphAssemblyFanJob");
            }
            boolean sameOutput=completedInput(old)&&(old.definition==null||old.definition==graph);
            boolean unknownTransportLock=old==null&&key instanceof TransportedItemStack transported&&transported.processingTime==-1;
            lockedOutput=sameOutput||unknownTransportLock;
            restart=!lockedOutput&&(!job.equals(saved)||old!=null&&(old.definition!=graph||old.world.get()!=level));
        }
        public void beginEntity(ItemEntity entity) {
            if(restart)entityData(entity).remove("Processing");
            if(selected.operation()!=null) {
                var data=entityData(entity);data.putString("GraphAssemblyFanJob",job);data.putBoolean("GraphAssemblyFanCompleted",lockedOutput&&!restart);
            } else if(restart) {TIMERS.remove(key);entityData(entity).remove("GraphAssemblyFanJob");entityData(entity).remove("GraphAssemblyFanCompleted");}
        }
        public void finishEntity(ItemEntity entity) {
            if(aborted&&!entity.isRemoved()){entityData(entity).remove("Processing");entityData(entity).putBoolean("GraphAssemblyFanCompleted",false);}
            if(selected.operation()==null||entity.isRemoved())return;
            if(!completed&&!current())return;
            boolean done=completed||lockedOutput&&!restart;
            TIMERS.put(entity,timer(entity.getItem(),done));entityData(entity).putBoolean("GraphAssemblyFanCompleted",done);
        }
        public void beginTransported(TransportedItemStack transported) {
            if(restart)transported.clearFanProcessingData();
            if(selected.operation()==null&&restart)TIMERS.remove(key);
        }
        public void finishTransported(TransportedItemStack transported,TransportedResult result) {
            if(aborted)transported.clearFanProcessingData();
            if(selected.operation()==null)return;
            if(!completed&&!current())return;
            TIMERS.put(transported,timer(transported.stack,completed||lockedOutput&&!restart));
            if(completed)for(var output:result.getOutputs())TIMERS.put(output,timer(output.stack,true));
        }
        private Timer timer(ItemStack input,boolean complete) {
            return new Timer(job,input.copy(),selected.operation().graph(),complete,new java.lang.ref.WeakReference<>(level));
        }
        private boolean current() {
            if(invalid||!attached.getAsBoolean()||!ItemStack.matches(input,live.get()))return false;
            var now=new ArrayList<>(level.getRecipeManager().getRecipes());
            if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
        @Override public void close(){var calls=CALLS.get();calls.pop();if(calls.isEmpty())CALLS.remove();}
    }
    public static Scope open(Object key,Level level,Supplier<ItemStack> input,BooleanSupplier attached,FanProcessingType type) {
        var calls=CALLS.get();
        for(var prior:calls)if(prior.key==key){prior.invalid=true;return null;}
        var scope=new Scope(key,level,input,attached);calls.push(scope);
        try {
            scope.selected=select(level,scope.input,type);
            scope.type=type;
            if(scope.current())scope.bindTimer();
        } catch(RuntimeException preparationFailure){scope.invalid=true;}
        return scope;
    }
    private record Selection(boolean handled,GraphOperation operation,com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> processing) {}
    private static Selection select(Level level,ItemStack input,FanProcessingType type) {
        if(type==AllFanProcessingTypes.SPLASHING) {
            var lookup=GraphRuntime.lookup(level,input,com.simibubi.create.AllRecipeTypes.SPLASHING.getType(),SplashingRecipe.class,r->true);
            var recipe=lookup.first().map(RecipeHolder::value).orElse(null);
            return new Selection(lookup.handled(),recipe==null?null:GraphOperation.get(recipe),recipe);
        }
        if(type==AllFanProcessingTypes.HAUNTING) {
            var lookup=GraphRuntime.lookup(level,input,com.simibubi.create.AllRecipeTypes.HAUNTING.getType(),HauntingRecipe.class,r->true);
            var recipe=lookup.first().map(RecipeHolder::value).orElse(null);
            return new Selection(lookup.handled(),recipe==null?null:GraphOperation.get(recipe),recipe);
        }
        boolean protectedInput=dev.graphassembly.integration.sequence.NativeSequences.tracked(input);
        for(var source:CreateNativeRecipes.fanSources(type)) {
            var selected=CookingLookup.select(level,input,source,(operation,recipe)->CreateNativeRecipes.fanAdmitted(level,type,operation,recipe));
            if(selected.operation()!=null)return new Selection(selected.handled(),selected.operation(),null);
            // Ambiguity protects even raw input. A progressing piece may simply target the
            // second native source (blasting rather than smelting), so finish routing first.
            if(selected.handled()&&!protectedInput)return new Selection(selected.handled(),selected.operation(),null);
            if(!protectedInput&&level.getRecipeManager().getRecipeFor(source,new SingleRecipeInput(input),level)
                .filter(com.simibubi.create.AllRecipeTypes.CAN_BE_AUTOMATED).isPresent())
                return new Selection(false,null,null);
        }
        return new Selection(protectedInput,null,null);
    }
    /** null delegates to ordinary upstream eligibility. */
    public static Boolean eligible() {
        var scope=CALLS.get().peek();
        if(scope==null||!scope.selected.handled())return null;
        return scope.allowed();
    }
    /** null delegates to ordinary processing, or rejects an invalid preparation without payment. */
    public static List<ItemStack> process() {
        var scope=CALLS.get().peek();
        if(scope==null||!scope.selected.handled())return null;
        try {
            if(!scope.allowed())return null;
            var prepared=scope.selected.processing()==null
                ? new GraphOutputs.Prepared(OperationCompletion.prepare(scope.level,scope.selected.operation()),List.of())
                : GraphOutputs.prepare(scope.level,scope.selected.operation(),scope.selected.processing());
            if(!prepared.outputsEnabled(scope.level)||!scope.current()){scope.aborted=true;return null;}
            var outputs=new ArrayList<ItemStack>();
            for(int unit=0;unit<scope.input.getCount();unit++)outputs.addAll(prepared.roll(scope.level.random));
            scope.completed=true;
            return outputs;
        } catch(RuntimeException invalidPreparation){scope.aborted=true;return null;}
    }
    public static boolean handled(){var scope=CALLS.get().peek();return scope!=null&&scope.selected.handled();}
    private static CompoundTag entityData(ItemEntity entity) {
        var persistent=entity.getPersistentData();if(!persistent.contains("CreateData"))persistent.put("CreateData",new CompoundTag());
        return persistent.getCompound("CreateData");
    }
    /** Native load has already decoded Item. Bind completed metadata to THAT loaded identity,
     * not to whichever item is substituted before the first subsequent airflow query. */
    public static void loadedEntity(ItemEntity entity,CompoundTag saved) {
        TIMERS.remove(entity);
        var data=saved.getCompound("NeoForgeData").getCompound("CreateData");
        if(data.getString("GraphAssemblyFanJob").isEmpty()||!data.getBoolean("GraphAssemblyFanCompleted")
            ||data.getCompound("Processing").getInt("Time")>0)return;
        TIMERS.put(entity,new Timer(data.getString("GraphAssemblyFanJob"),entity.getItem().copy(),null,true,
            new java.lang.ref.WeakReference<>(entity.level())));
    }
    /** Only our known runtime jobs add metadata to native transported serialization. */
    public static void saveTransported(TransportedItemStack transported,CompoundTag saved) {
        var timer=TIMERS.get(transported);if(timer==null)return;
        saved.putString("GraphAssemblyFanJob",timer.job);saved.putBoolean("GraphAssemblyFanCompleted",timer.completed);
    }
    public static void loadedTransported(TransportedItemStack transported,CompoundTag saved) {
        TIMERS.remove(transported);
        if(saved.getString("GraphAssemblyFanJob").isEmpty()||!saved.getBoolean("GraphAssemblyFanCompleted")||transported.processingTime!=-1)return;
        // read() has registries, not a Level. The first matching call attaches this loaded
        // identity to its world; substituting input before that call cannot inherit the lock.
        TIMERS.put(transported,new Timer(saved.getString("GraphAssemblyFanJob"),transported.stack.copy(),null,true,new java.lang.ref.WeakReference<>(null)));
    }
    /** All graph results from one exposure inherit its native completed lock, including new entities. */
    public static void inheritExposure(ItemEntity entity) {
        var scope=CALLS.get().peek();if(scope==null||!scope.completed)return;
        var processing=new CompoundTag();processing.putString("Type",CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(scope.type).toString());processing.putInt("Time",-1);
        var data=entityData(entity);data.put("Processing",processing);data.putString("GraphAssemblyFanJob",scope.job);data.putBoolean("GraphAssemblyFanCompleted",true);
        TIMERS.put(entity,scope.timer(entity.getItem(),true));
    }
}
