package dev.graphassembly.integration.brewing;

import dev.graphassembly.integration.furnace.CookingCompletion;
import dev.graphassembly.recipe.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.Containers;
import net.neoforged.neoforge.event.EventHooks;
import java.util.*;
import java.util.function.*;

/** Position-scoped authorization; proposals never grant permission to call doBrew directly. */
public final class BrewingTransaction {
    private BrewingTransaction() {}
    private record Location(Level level, BlockPos pos) {}
    private static final Map<Location,Scope> ACTIVE=new HashMap<>();
    private static final String JVM=UUID.randomUUID().toString();
    private static final Map<Object,String> IDENTITIES=new WeakHashMap<>();
    private static String sessionIdentity(Level level){return JVM+":"+IDENTITIES.computeIfAbsent(level.getServer(),k->UUID.randomUUID().toString());}
    private static String runtimeIdentity(Level level,Object index,Object brewing){
        return sessionIdentity(level)+":"+IDENTITIES.computeIfAbsent(index,k->UUID.randomUUID().toString())
            +":"+IDENTITIES.computeIfAbsent(brewing,k->UUID.randomUUID().toString());
    }
    public static final class State {
        public String job="";
        public String runtime="";
        private long generation;
        private Object index,brewing;
        public void loaded(){index=null;brewing=null;generation++;}
    }
    public static final class Scope implements AutoCloseable {
        private final Location location;
        public final BrewingStandBlockEntity stand;
        public boolean invalid,authorized;
        public BrewingLookup.Lookup lookup;
        private Snapshot ordinaryQuery;
        private GraphIndex ordinaryIndex;
        private boolean staging;
        private final List<Runnable> effects=new ArrayList<>();
        private Scope(Location location,BrewingStandBlockEntity stand){this.location=location;this.stand=stand;}
        @Override public void close(){ACTIVE.remove(location);}
    }
    public static Scope enter(Level level,BlockPos pos,BrewingStandBlockEntity stand){
        var location=new Location(level,pos.immutable());var previous=ACTIVE.get(location);
        if(previous!=null){previous.invalid=true;return null;}
        var scope=new Scope(location,stand);ACTIVE.put(location,scope);return scope;
    }
    public static boolean machine(BrewingStandBlockEntity stand){return stand.getClass()==BrewingStandBlockEntity.class&&stand.getBlockState().is(Blocks.BREWING_STAND);}
    private static BrewingAccess access(BrewingStandBlockEntity stand){return (BrewingAccess)stand;}
    private record Snapshot(Scope scope,List<ItemStack> items,Object brewing,List<?> recipes,Object block,int time,int fuel,String job,long generation){
        static Snapshot capture(Scope scope){
            var stand=scope.stand;var a=access(stand);var level=scope.location.level;
            return new Snapshot(scope,a.graphAssembly$items().stream().map(ItemStack::copy).toList(),level.potionBrewing(),
                List.copyOf(level.getRecipeManager().getRecipes()),level.getBlockState(scope.location.pos),a.graphAssembly$time(),a.graphAssembly$fuel(),a.graphAssembly$brewingState().job,a.graphAssembly$brewingState().generation);
        }
        boolean current(){return currentAt(time);}
        boolean currentAt(int expectedTime){
            var stand=scope.stand;var a=access(stand);var level=scope.location.level;
            if(scope.invalid||stand.isRemoved()||stand.getLevel()!=level||level.getBlockEntity(scope.location.pos)!=stand
                ||level.getBlockState(scope.location.pos)!=block||level.potionBrewing()!=brewing
                ||a.graphAssembly$time()!=expectedTime||a.graphAssembly$fuel()!=fuel||!a.graphAssembly$brewingState().job.equals(job)
                ||a.graphAssembly$brewingState().generation!=generation
                ||!ItemStack.listMatches(items,a.graphAssembly$items()))return false;
            var now=List.copyOf(level.getRecipeManager().getRecipes());
            if(now.size()!=recipes.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=recipes.get(i))return false;
            return true;
        }
    }
    /** Called after native fuel loading, before native countdown/start. null means native passthrough. */
    public static Boolean brewable(Scope scope){
        if(scope==null||scope.invalid)return false;
        var a=access(scope.stand);var state=a.graphAssembly$brewingState();var level=scope.location.level;
        var before=Snapshot.capture(scope);
        try {
            var found=BrewingLookup.find(level,before.items);scope.lookup=found;
            if(!before.current()){scope.invalid=true;return false;}
            if(!found.handled()){
                if(!state.job.isEmpty())a.graphAssembly$time(0);
                state.job="";state.loaded();return null;
            }
            if(!machine(scope.stand)||found.bottles().isEmpty()){a.graphAssembly$time(0);state.job="";return false;}
            for(var bottle:found.bottles())if(!fits(level,bottle,bottle.nativeResult()))return false;
            var index=GraphIndex.get(level);var identity=identity(level,found,before.items);
            if(found.bottles().stream().anyMatch(b->b.operation()!=null&&!b.operation().current(level))
                ||GraphIndex.get(level)!=index||!before.current()){scope.invalid=true;return false;}
            var runtime=runtimeIdentity(level,index,level.potionBrewing());
            if(!identity.equals(state.job)||state.index!=null&&state.index!=index||state.brewing!=null&&state.brewing!=level.potionBrewing()
                ||state.runtime.startsWith(sessionIdentity(level)+":")&&!state.runtime.equals(runtime))a.graphAssembly$time(0);
            state.job=identity;state.index=index;state.brewing=level.potionBrewing();state.runtime=runtime;return true;
        } catch(RuntimeException failure){a.graphAssembly$time(0);state.job="";return false;}
    }
    private static String identity(Level level,BrewingLookup.Lookup found,List<ItemStack> items){
        var content=new StringBuilder(found.primary().name());
        // Ordinary fuel may refill during a running native job. It is guarded in each five-slot
        // transaction snapshot, but it is not a logical workpiece in the persistent job identity.
        for(var stack:items.subList(0,4))content.append(':').append(stack.saveOptional(level.registryAccess()));
        for(var bottle:found.bottles()){
            content.append('/').append(bottle.slot()).append(':').append(bottle.nativeResult().saveOptional(level.registryAccess()));
            var op=bottle.operation();if(op!=null){
                content.append(':').append(op.node().id()).append(':').append(op.graph().id());
                content.append(':').append(((GraphRecipe)level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(op.graph().id())).orElseThrow().value()).json());
            }
        }
        return com.google.common.hash.Hashing.sha256().hashString(content,java.nio.charset.StandardCharsets.UTF_8).toString();
    }
    private static boolean single(Level level,ItemStack stack){return stack.isEmpty()||stack.getCount()==1&&stack.getMaxStackSize()>=1&&stack.isItemEnabled(level.enabledFeatures());}
    private static boolean fits(Level level,BrewingLookup.Bottle bottle,ItemStack nativeResult){
        if(nativeResult.isEmpty()||!single(level,nativeResult))return false;
        return bottle.operation()==null||CookingCompletion.prepare(level,bottle.operation(),nativeResult).fitsSingleSlot(level);
    }
    public static void complete(Scope scope){
        if(scope==null||!scope.authorized||scope.invalid)return;
        scope.authorized=false;var a=access(scope.stand);var level=scope.location.level;var before=Snapshot.capture(scope);
        if(a.graphAssembly$time()!=0||a.graphAssembly$brewingState().job.isEmpty())return;
        try {
            var found=BrewingLookup.find(level,before.items);
            if(!found.handled()||found.bottles().isEmpty()||!identity(level,found,before.items).equals(a.graphAssembly$brewingState().job)||!before.current())return;
            var staged=NonNullList.withSize(5,ItemStack.EMPTY);for(int i=0;i<5;i++)staged.set(i,before.items.get(i).copy());
            if(EventHooks.onPotionAttemptBrew(staged)){
                // EventHooks itself emits Post on cancellation. Never emit a second Post.
                boolean capacity=true;for(int i=0;i<5;i++)capacity&=staged.get(i).isEmpty()||staged.get(i).getCount()<=(i<3?1:staged.get(i).getMaxStackSize());
                if(capacity&&GraphIndex.cached(level,(GraphIndex)a.graphAssembly$brewingState().index)&&before.current())for(int i=0;i<5;i++)a.graphAssembly$items().set(i,staged.get(i).copy());
                return;
            }
            if(!before.current())return;
            for(var bottle:found.bottles())staged.set(bottle.slot(),level.potionBrewing().mix(staged.get(3).copy(),staged.get(bottle.slot()).copy()));
            EventHooks.onPotionBrewed(staged);
            // Material and fuel are not output carriers; Post cannot silently replace their payment.
            if(!ItemStack.matches(staged.get(3),before.items.get(3))||!ItemStack.matches(staged.get(4),before.items.get(4)))return;
            for(int i=0;i<3;i++){
                final int slot=i;
                if(found.bottles().stream().noneMatch(b->b.slot()==slot)&&!ItemStack.matches(staged.get(i),before.items.get(i)))return;
            }
            var prepared=new HashMap<Integer,CookingCompletion>();
            for(var bottle:found.bottles()){
                var result=staged.get(bottle.slot());if(result.isEmpty()||!single(level,result))return;
                if(bottle.operation()!=null){
                    var completion=CookingCompletion.prepare(level,bottle.operation(),result);
                    if(!completion.fitsSingleSlot(level))return;
                    prepared.put(bottle.slot(),completion);
                }
            }
            for(int i=0;i<3;i++)if(!single(level,staged.get(i)))return;
            var material=staged.get(3).copy();var remainder=material.hasCraftingRemainingItem()?material.getCraftingRemainingItem():ItemStack.EMPTY;
            material.shrink(1);var drop=ItemStack.EMPTY;if(material.isEmpty())material=remainder;else drop=remainder;
            if(found.bottles().stream().anyMatch(b->b.operation()!=null&&!b.operation().current(level))
                ||a.graphAssembly$brewingState().index!=GraphIndex.get(level)||!before.current())return;
            // No external callbacks between draws and payment. Each bottle gets its own plan/roll.
            for(var bottle:found.bottles())if(prepared.containsKey(bottle.slot())){
                var outputs=prepared.get(bottle.slot()).roll(level.random);staged.set(bottle.slot(),outputs.isEmpty()?ItemStack.EMPTY:outputs.getFirst());
            }
            for(int i=0;i<3;i++)a.graphAssembly$items().set(i,staged.get(i));
            a.graphAssembly$items().set(3,material);a.graphAssembly$brewingState().job="";
            scope.stand.setChanged();
            if(!drop.isEmpty())Containers.dropItemStack(level,scope.location.pos.getX(),scope.location.pos.getY(),scope.location.pos.getZ(),drop);
            level.levelEvent(1035,scope.location.pos,0);
        } catch(RuntimeException invalidPreparation){/* No fallback to unguarded native processing. */}
    }
    private static NonNullList<ItemStack> copyItems(List<ItemStack> source){
        var copy=NonNullList.withSize(source.size(),ItemStack.EMPTY);
        for(int i=0;i<source.size();i++)copy.set(i,source.get(i).copy());return copy;
    }
    /** Native ordinary query stays native, but no callback receives live stack references. */
    public static boolean ordinaryBrewable(Scope scope,Predicate<NonNullList<ItemStack>> nativeQuery){
        if(scope==null||scope.invalid)return false;
        var before=Snapshot.capture(scope);var level=scope.location.level;
        var index=GraphIndex.get(level);
        if(!before.current())return false;
        boolean result=nativeQuery.test(copyItems(before.items));
        if(!GraphIndex.cached(level,index)||!before.current()){
            scope.invalid=true;access(scope.stand).graphAssembly$time(0);return false;
        }
        scope.ordinaryQuery=before;scope.ordinaryIndex=index;return result;
    }
    /** Run the unmodified ordinary Pre/mix/Post/remainder chain on isolated inventory. Only
     * its two native world-effect call sites are deferred until the complete chain validates. */
    public static void ordinaryComplete(Scope scope,Consumer<NonNullList<ItemStack>> nativeCompletion){
        if(scope==null||scope.invalid)return;
        var a=access(scope.stand);var level=scope.location.level;
        if(scope.ordinaryQuery!=null&&(!scope.ordinaryQuery.currentAt(0)||!GraphIndex.cached(level,scope.ordinaryIndex)))return;
        var before=Snapshot.capture(scope);var index=GraphIndex.get(level);
        if(!before.current()||before.items.stream().anyMatch(Workpiece::has))return;
        var staged=copyItems(before.items);scope.effects.clear();scope.staging=true;
        try {
            nativeCompletion.accept(staged);
            if(!GraphIndex.cached(level,index)||!before.current()){
                scope.invalid=true;a.graphAssembly$time(0);return;
            }
            // Copy before exposure: event listeners may retain references to their temporary list.
            var paid=copyItems(staged);
            if(!GraphIndex.cached(level,index)||!before.current())return;
            for(int i=0;i<5;i++)a.graphAssembly$items().set(i,paid.get(i));
            scope.staging=false;
            for(var effect:scope.effects)effect.run();
        } finally {scope.staging=false;scope.effects.clear();}
    }
    public static boolean deferDrop(Level level,double x,double y,double z,ItemStack stack){
        var scope=ACTIVE.get(new Location(level,BlockPos.containing(x,y,z)));
        if(scope==null||!scope.staging)return false;
        var copy=stack.copy();scope.effects.add(()->Containers.dropItemStack(level,x,y,z,copy));return true;
    }
    public static boolean deferEvent(Level level,int event,BlockPos pos,int data){
        var scope=ACTIVE.get(new Location(level,pos));
        if(scope==null||!scope.staging)return false;
        scope.effects.add(()->level.levelEvent(event,pos,data));return true;
    }
    public static void direct(Level level,BlockPos pos,NonNullList<ItemStack> items,Consumer<NonNullList<ItemStack>> nativeCompletion){
        var active=ACTIVE.get(new Location(level,pos));
        if(active!=null){active.invalid=true;return;}
        var entity=level.getBlockEntity(pos);
        if(!(entity instanceof BrewingStandBlockEntity stand)){
            if(items.stream().noneMatch(Workpiece::has))nativeCompletion.accept(items);return;
        }
        try(var scope=enter(level,pos,stand)){
            var snapshot=Snapshot.capture(scope);var found=BrewingLookup.find(level,items);
            if(!found.handled()&&snapshot.current()&&items==access(stand).graphAssembly$items())ordinaryComplete(scope,nativeCompletion);
        }
    }
}
