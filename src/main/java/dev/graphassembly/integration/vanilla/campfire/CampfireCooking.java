package dev.graphassembly.integration.vanilla.campfire;

import dev.graphassembly.integration.OperationCompletion;
import dev.graphassembly.integration.MachineScopes;
import dev.graphassembly.integration.RecipeSnapshot;
import dev.graphassembly.integration.vanilla.furnace.*;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import java.util.*;

/** Native per-position boundary only: selection/definition/output algorithms remain shared. */
public final class CampfireCooking {
    private CampfireCooking() {}
    private static final MachineScopes<Scope> ACTIVE=new MachineScopes<>(scope->scope.invalid=true);
    public static final class Scope implements AutoCloseable {
        private final MachineScopes<Scope>.Token token;
        public boolean invalid;
        private Scope(MachineScopes<Scope>.Token token){this.token=token;}
        @Override public void close(){token.close();}
    }
    public static Scope enter(Level level,BlockPos pos) {
        return ACTIVE.enter(level,pos,Scope::new);
    }
    public static boolean machine(CampfireBlockEntity fire) {
        var block=fire.getBlockState().getBlock();
        return fire.getClass()==CampfireBlockEntity.class&&(block==Blocks.CAMPFIRE||block==Blocks.SOUL_CAMPFIRE);
    }
    public static final class State {
        public final String[] jobs={"","","",""};
        private final GraphIndex[] indices=new GraphIndex[4];
        public void loaded(){Arrays.fill(indices,null);}
    }
    private record Snapshot(Level level,BlockPos pos,CampfireBlockEntity fire,BlockState block,
                            List<ItemStack> items,RecipeSnapshot definitions) {
        static Snapshot capture(CampfireBlockEntity fire) {
            var level=fire.getLevel();var pos=fire.getBlockPos();
            return new Snapshot(level,pos,fire,level.getBlockState(pos),fire.getItems().stream().map(ItemStack::copy).toList(),
                RecipeSnapshot.capture(level));
        }
        boolean current(Scope scope) {
            if(scope.invalid||fire.getLevel()!=level||fire.isRemoved()||level.getBlockEntity(pos)!=fire
                ||level.getBlockState(pos)!=block||!ItemStack.listMatches(items,fire.getItems()))return false;
            return definitions.current(level);
        }
    }
    private static CookingLookup.Selection select(Level level,ItemStack input,BlockState block) {
        return CookingLookup.select(level,input,RecipeType.CAMPFIRE_COOKING,(operation,recipe)->switch(operation.node().operation().campfire()) {
            case ANY -> block.is(Blocks.CAMPFIRE)||block.is(Blocks.SOUL_CAMPFIRE);
            case NORMAL -> block.is(Blocks.CAMPFIRE);
            case SOUL -> block.is(Blocks.SOUL_CAMPFIRE);
        });
    }
    /** null means vanilla selection; empty means protected rejection. Never caches an authorization. */
    public static Optional<RecipeHolder<CampfireCookingRecipe>> cookable(CampfireBlockEntity fire,ItemStack held) {
        if(!machine(fire))return dev.graphassembly.integration.sequence.NativeSequences.tracked(held)?Optional.empty():null;
        var level=fire.getLevel();if(level==null)return null;
        try(var scope=enter(level,fire.getBlockPos())) {
            if(scope==null)return Optional.empty();
            var snapshot=Snapshot.capture(fire);var input=held.copy();
            var selection=select(level,input,snapshot.block);
            if(!snapshot.current(scope)||!ItemStack.matches(input,held))return Optional.empty();
            if(!selection.handled())return null;
            if(fire.getItems().stream().noneMatch(ItemStack::isEmpty)||selection.recipe()==null)return Optional.empty();
            return Optional.of(new RecipeHolder<>(selection.recipe().id(),(CampfireCookingRecipe)selection.recipe().value()));
        } catch(RuntimeException invalidPreparation){return Optional.empty();}
    }
    /** true hides ONLY this graph slot from the native loop, without changing the actual input list. */
    public static boolean tickSlot(CampfireBlockEntity fire,State state,int slot,int[] progress,int[] times,Scope scope) {
        if(scope.invalid)return true;
        var input=fire.getItems().get(slot);
        if(input.isEmpty()){state.jobs[slot]="";state.indices[slot]=null;return false;}
        if(!machine(fire))return dev.graphassembly.integration.sequence.NativeSequences.tracked(input);
        var level=fire.getLevel();boolean committed=false;
        try {
            // Baseline must precede external ingredient/component codecs, even index construction.
            var snapshot=Snapshot.capture(fire);
            var selection=select(level,input,snapshot.block);
            if(!snapshot.current(scope)){scope.invalid=true;return true;}
            if(!selection.handled()) {
                if(!state.jobs[slot].isEmpty()) {
                    progress[slot]=0;times[slot]=CookingLookup.ordinaryTime(level,RecipeType.CAMPFIRE_COOKING,input,times[slot]);
                    state.jobs[slot]="";state.indices[slot]=null;fire.setChanged();
                    if(!snapshot.current(scope)){scope.invalid=true;return true;}
                }
                return false;
            }
            // Native placeFood always creates a count-one position; never turn an externally
            // corrupted stack into an entire paid round by clearing the whole position.
            if(input.getCount()!=1||selection.operation()==null){progress[slot]=0;fire.setChanged();return true;}
            var index=GraphIndex.get(level);
            var identity=CookingLookup.identity(level,selection,input);
            if(!snapshot.current(scope)){scope.invalid=true;return true;}
            if(!identity.equals(state.jobs[slot])||state.indices[slot]!=null&&state.indices[slot]!=index)progress[slot]=0;
            state.jobs[slot]=identity;state.indices[slot]=index;
            times[slot]=selection.recipe().value().getCookingTime();
            progress[slot]=Math.min(progress[slot],times[slot]-1)+1;
            fire.setChanged();
            if(progress[slot]<times[slot])return true;
            var prepared=OperationCompletion.prepare(level,selection.operation());
            if(!prepared.outputsEnabled(level)||!snapshot.current(scope)){scope.invalid|=!snapshot.current(scope);return true;}
            var outputs=prepared.roll(level.random);
            // Pay the entire one-item round before the first drop/update/world callback.
            committed=true;fire.getItems().set(slot,ItemStack.EMPTY);state.jobs[slot]="";state.indices[slot]=null;
            fire.setChanged();
            for(var output:outputs)if(!output.isEmpty())Containers.dropItemStack(level,snapshot.pos.getX(),snapshot.pos.getY(),snapshot.pos.getZ(),output);
            level.sendBlockUpdated(snapshot.pos,snapshot.block,snapshot.block,3);
            level.gameEvent(GameEvent.BLOCK_CHANGE,snapshot.pos,GameEvent.Context.of(snapshot.block));
            return true;
        } catch(RuntimeException invalidPreparation) {
            if(committed)throw invalidPreparation;
            progress[slot]=0;fire.setChanged();return true;
        }
    }
}
