package dev.graphassembly.integration.crafter;

import dev.graphassembly.integration.crafting.*;
import dev.graphassembly.integration.furnace.CookingCompletion;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Pulse-local machine boundary only. Matching and settlement belong to shared crafting/graph code. */
public final class CrafterTransaction {
    private CrafterTransaction() {}
    private record Location(ServerLevel level,BlockPos pos) {}
    // Only active synchronous scopes, never a cached plan; covers replacement BE reentry too.
    private static final Set<Location> BUSY=new HashSet<>();
    public interface Delivery {
        void deliver(ServerLevel level,BlockPos pos,CrafterBlockEntity machine,ItemStack stack,BlockState state,RecipeHolder<CraftingRecipe> recipe);
    }
    private record Snapshot(ServerLevel level,BlockPos pos,CrafterBlockEntity machine,BlockState state,
                            List<ItemStack> inputs,int mask,List<RecipeHolder<?>> definitions) {
        static Snapshot capture(ServerLevel level,BlockPos pos,CrafterBlockEntity machine) {
            return new Snapshot(level,pos.immutable(),machine,level.getBlockState(pos),
                machine.getItems().stream().map(ItemStack::copy).toList(),CrafterTransaction.mask(machine),
                new ArrayList<>(level.getRecipeManager().getRecipes()));
        }
        boolean current() {
            if(machine.getLevel()!=level||machine.isRemoved()||level.getBlockEntity(pos)!=machine
                ||level.getBlockState(pos)!=state||!ItemStack.listMatches(inputs,machine.getItems())||mask!=CrafterTransaction.mask(machine))return false;
            var now=new ArrayList<>(level.getRecipeManager().getRecipes());
            if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
    }
    private static int mask(CrafterBlockEntity machine) {
        int mask=0;for(int i=0;i<9;i++)if(machine.isSlotDisabled(i))mask|=1<<i;return mask;
    }
    /** true suppresses native cached ordinary execution, including rejected protected inputs. */
    public static boolean pulse(CrafterBlock block,BlockState state,ServerLevel level,BlockPos pos,Delivery delivery) {
        if(block.getClass()!=CrafterBlock.class||state.getBlock()!=Blocks.CRAFTER)return false;
        var key=new Location(level,pos.immutable());
        if(!BUSY.add(key))return true;
        boolean committed=false;
        try {
            if(!(level.getBlockEntity(pos) instanceof CrafterBlockEntity machine)||machine.getClass()!=CrafterBlockEntity.class)return false;
            var snapshot=Snapshot.capture(level,pos,machine); // Pure baseline BEFORE index construction/codecs.
            var index=GraphIndex.get(level);
            var lookup=CraftingLookup.find(level,machine);
            if(!snapshot.current()||!GraphIndex.cached(level,index))return true;
            if(!lookup.handled())return false;
            var plan=lookup.plan();
            if(plan==null){level.levelEvent(1050,pos,0);return true;}
            var prepared=CookingCompletion.prepare(level,plan.operation(),plan.nativeCarrier());
            if(!prepared.outputsEnabled(level))return true;
            var input=CraftingInput.of(3,3,snapshot.inputs().stream().map(ItemStack::copy).toList());
            List<ItemStack> remaining;
            var parent=net.neoforged.neoforge.common.CommonHooks.getCraftingPlayer();
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);
            try {remaining=plan.recipe().getRemainingItems(input).stream().map(ItemStack::copy).toList();}
            finally {net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(parent);}
            if(!snapshot.current()||!GraphIndex.cached(level,index))return true;
            var fresh=CraftingLookup.find(level,machine).plan();
            if(!snapshot.current()||!GraphIndex.cached(level,index)||fresh==null
                ||fresh.operation().graph()!=plan.operation().graph()||fresh.operation().node()!=plan.operation().node()
                ||!Arrays.equals(fresh.slots(),plan.slots())||!ItemStack.matches(fresh.nativeCarrier(),plan.nativeCarrier()))return true;
            var outputs=prepared.roll(level.random);
            // No setter/target/event callback can observe only part of the paid input round.
            committed=true;
            for(int slot:plan.slots())machine.getItem(slot).shrink(1);
            machine.setChanged();
            machine.setCraftingTicksRemaining(6);
            level.setBlock(pos,state.setValue(CrafterBlock.CRAFTING,true),2);
            var holder=new RecipeHolder<CraftingRecipe>(net.minecraft.resources.ResourceLocation.parse(plan.operation().graph().id()),plan.recipe());
            for(var output:outputs)if(!output.isEmpty()) {
                output.onCraftedBySystem(level);
                delivery.deliver(level,pos,machine,output,state,holder);
            }
            for(var remainder:remaining)if(!remainder.isEmpty())delivery.deliver(level,pos,machine,remainder,state,holder);
            return true;
        } catch(RuntimeException invalidPreparation) {
            if(committed)throw invalidPreparation; // No rollback promise for delivery/event callbacks.
            return true;
        } finally {BUSY.remove(key);}
    }
}
