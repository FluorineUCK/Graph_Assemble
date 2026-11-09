package dev.graphassembly.integration.vanilla.crafting;

import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Synchronous menu-local lookup and transfer. Preview is never a consumable recipe output. */
public final class CraftingTransaction {
    private CraftingTransaction() {}
    public static final class Context {
        final AbstractContainerMenu menu; final Player player; final CraftingContainer grid; final ResultContainer result;
        boolean handled,busy;
        Context(AbstractContainerMenu menu,Player player,CraftingContainer grid,ResultContainer result){this.menu=menu;this.player=player;this.grid=grid;this.result=result;}
    }
    private record Transfer(List<ItemStack> grid,List<ItemStack> inventory,ItemStack carried) {}
    private record Snapshot(net.minecraft.world.level.Level level,GraphIndex index,AbstractContainerMenu activeMenu,
                            List<ItemStack> grid,List<ItemStack> inventory,ItemStack carried,ItemStack result) {
        static Snapshot capture(Context context) {
            return new Snapshot(context.player.level(),GraphIndex.get(context.player.level()),context.player.containerMenu,
                context.grid.getItems().stream().map(ItemStack::copy).toList(),
                java.util.stream.IntStream.range(0,context.player.getInventory().getContainerSize())
                    .mapToObj(i->context.player.getInventory().getItem(i).copy()).toList(),
                context.menu.getCarried().copy(),context.result.getItem(0).copy());
        }
        boolean current(Context context) {
            if(context.player.level()!=level || context.player.containerMenu!=activeMenu || activeMenu!=context.menu
                || ((CraftingMenuState)context.menu).graphAssembly$crafting()!=context
                || GraphIndex.get(level)!=index || !ItemStack.listMatches(grid,context.grid.getItems())
                || !ItemStack.matches(carried,context.menu.getCarried()) || !ItemStack.matches(result,context.result.getItem(0))
                || inventory.size()!=context.player.getInventory().getContainerSize())return false;
            for(int i=0;i<inventory.size();i++)if(!ItemStack.matches(inventory.get(i),context.player.getInventory().getItem(i)))return false;
            return true;
        }
    }

    public static boolean update(AbstractContainerMenu menu,Player player,CraftingContainer grid,ResultContainer result) {
        if(menu.getClass()!=InventoryMenu.class && menu.getClass()!=CraftingMenu.class)return false;
        var state=(CraftingMenuState)menu; var context=state.graphAssembly$crafting();
        if(context==null){context=new Context(menu,player,grid,result);state.graphAssembly$crafting(context);}
        if(context.busy)return true;
        if(player.level().isClientSide())return context.handled;
        boolean wasHandled=context.handled;
        var lookup=find(context); context.handled=lookup.handled();
        ((CraftingPreview)result).graphAssembly$preview(lookup.handled());
        if(!lookup.handled()) {
            if(wasHandled)result.setItem(0,ItemStack.EMPTY);
            return false;
        }
        ItemStack preview=CraftingLookup.preview(player.level(),lookup);
        result.setRecipeUsed(null); result.setItem(0,preview); menu.broadcastChanges();
        return true;
    }

    private static CraftingLookup.Lookup find(Context context) {
        return CraftingLookup.find(context.player.level(),context.grid);
    }

    public static boolean protectedPreview(net.minecraft.world.Container container){
        return container instanceof CraftingPreview preview && preview.graphAssembly$preview();
    }
    public static boolean click(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player) {
        var context=((CraftingMenuState)menu).graphAssembly$crafting();
        if(context==null)return false;
        if(context.busy)return true; // Never fall through to native result extraction during callbacks.
        if(slot!=0)return false;
        var lookup=find(context);
        // A stale graph preview must not fall into the native result-slot consumption path.
        if(!context.handled && !lookup.handled())return false;
        context.handled|=lookup.handled();
        if(player.level().isClientSide())return true;
        try {
            if(type==ClickType.PICKUP || type==ClickType.QUICK_MOVE || type==ClickType.SWAP) {
                if(type==ClickType.QUICK_MOVE) {
                    boolean again=true;
                    while(again) {
                        var before=context.grid.getItems().stream().map(ItemStack::copy).toList();
                        if(!commit(context,type,button))break;
                        again=!ItemStack.listMatches(before,context.grid.getItems());
                    }
                } else commit(context,type,button);
            }
            // CLONE, THROW, PICKUP_ALL and unsupported direct extraction are explicitly blocked.
        } finally {
            update(menu,player,context.grid,context.result); menu.broadcastChanges();
        }
        return true;
    }

    private static boolean commit(Context context,ClickType type,int button) {
        if(context.busy || context.player.containerMenu!=context.menu)return false;
        context.busy=true;
        try {
            return commitLocked(context,type,button);
        } finally {context.busy=false;}
    }

    private static boolean commitLocked(Context context,ClickType type,int button) {
        var snapshot=Snapshot.capture(context);
        var lookup=find(context); var plan=lookup.plan();
        if(plan==null || !snapshot.current(context))return false;
        var level=context.player.level();
        var prepared=dev.graphassembly.integration.OperationCompletion.prepare(level,plan.operation(),plan.nativeCarrier());
        if(!prepared.outputsEnabled(level))return false;
        var positioned=CraftingInput.ofPositioned(context.grid.getWidth(),context.grid.getHeight(),
            snapshot.grid().stream().map(ItemStack::copy).toList());
        net.minecraft.core.NonNullList<ItemStack> remaining;
        var parent=net.neoforged.neoforge.common.CommonHooks.getCraftingPlayer();
        net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(context.player);
        try {
            remaining=plan.recipe().getRemainingItems(positioned.input());
        } finally {net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(parent);}
        if(!snapshot.current(context))return false;
        var returns=new ArrayList<ItemStack>(Collections.nCopies(context.grid.getContainerSize(),ItemStack.EMPTY));
        for(int y=0;y<positioned.input().height();y++)for(int x=0;x<positioned.input().width();x++)
            returns.set(x+positioned.left()+(y+positioned.top())*context.grid.getWidth(),remaining.get(x+y*positioned.input().width()).copy());
        if(!prepared.allMaximalOutcomesFit(outputs->{
            // Chance may remove leading entries; reserve every possible first destination identity.
            for(int from=0;from<=outputs.size();from++)if(transfer(context,plan,outputs.subList(from,outputs.size()),returns,type,button)==null)return false;
            return true;
        }))return false;
        // Capacity/data codecs may also call external code. Recheck again immediately before RNG.
        if(!snapshot.current(context))return false;
        var fresh=find(context).plan();
        if(!snapshot.current(context)||fresh==null||fresh.operation().graph()!=plan.operation().graph()
            ||fresh.operation().node()!=plan.operation().node()||!Arrays.equals(fresh.slots(),plan.slots())
            ||!ItemStack.matches(fresh.nativeCarrier(),plan.nativeCarrier()))return false;
        var operation=plan.operation();
        var outputs=prepared.roll(level.random);
        var transfer=transfer(context,plan,outputs,returns,type,button);
        if(transfer==null)throw new IllegalStateException("Crafting capacity changed during synchronous commit");
        context.result.setItem(0,ItemStack.EMPTY);
        for(int i=0;i<transfer.grid().size();i++)context.grid.setItem(i,transfer.grid().get(i));
        for(int i=0;i<transfer.inventory().size();i++)context.player.getInventory().setItem(i,transfer.inventory().get(i));
        context.menu.setCarried(transfer.carried());
        // Success-only notifications: no event callback holds an uncommitted plan.
        for(var output:outputs)if(!output.isEmpty())output.copy().onCraftedBy(level,context.player,output.getCount());
        if(!outputs.isEmpty())net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(context.player,outputs.getFirst().copy(),context.grid);
        var holder=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(operation.graph().id()));
        holder.ifPresent(recipe->context.player.triggerRecipeCrafted(recipe,positioned.input().items()));
        return true;
    }

    private static Transfer transfer(Context context,CraftingLookup.Plan plan,List<ItemStack> outputs,List<ItemStack> returns,ClickType type,int button) {
        var grid=new ArrayList<ItemStack>();var inventory=new ArrayList<ItemStack>();
        for(int i=0;i<context.grid.getContainerSize();i++)grid.add(context.grid.getItem(i).copy());
        for(int i=0;i<context.player.getInventory().getContainerSize();i++)inventory.add(context.player.getInventory().getItem(i).copy());
        ItemStack carried=context.menu.getCarried().copy();
        for(int slot:plan.slots())grid.get(slot).shrink(1);
        for(int i=0;i<returns.size();i++) {
            var remainder=returns.get(i).copy(); if(remainder.isEmpty())continue;
            remainder=merge(grid,i,remainder);
            if(!store(inventory,remainder))return null;
        }
        int first=0;
        if(type==ClickType.PICKUP && !outputs.isEmpty()) {
            var output=outputs.getFirst();
            if(carried.isEmpty())carried=output.copy();
            else if(ItemStack.isSameItemSameComponents(carried,output) && carried.getCount()+output.getCount()<=carried.getMaxStackSize())carried.grow(output.getCount());
            else return null;
            first=1;
        } else if(type==ClickType.SWAP) {
            int target=button==40?40:button>=0 && button<9?button:-1;
            if(target<0 || !inventory.get(target).isEmpty())return null;
            if(!outputs.isEmpty()){inventory.set(target,outputs.getFirst().copy());first=1;}
        }
        for(int i=first;i<outputs.size();i++)if(!store(inventory,outputs.get(i).copy()))return null;
        return new Transfer(grid,inventory,carried);
    }
    private static boolean store(List<ItemStack> inventory,ItemStack stack) {
        for(int i=0;i<36 && !stack.isEmpty();i++)if(!inventory.get(i).isEmpty())stack=merge(inventory,i,stack);
        for(int i=0;i<36 && !stack.isEmpty();i++)if(inventory.get(i).isEmpty())stack=merge(inventory,i,stack);
        return stack.isEmpty();
    }
    private static ItemStack merge(List<ItemStack> stacks,int slot,ItemStack incoming) {
        var current=stacks.get(slot); if(!current.isEmpty() && !ItemStack.isSameItemSameComponents(current,incoming))return incoming;
        int room=(current.isEmpty()?incoming.getMaxStackSize():current.getMaxStackSize())-current.getCount();
        int amount=Math.min(room,incoming.getCount()); if(amount<=0)return incoming;
        if(current.isEmpty())stacks.set(slot,incoming.copyWithCount(amount));else current.grow(amount);
        incoming.shrink(amount);return incoming;
    }
}
