package dev.graphassembly.integration.vanilla.smithing;

import dev.graphassembly.integration.*;
import dev.graphassembly.integration.vanilla.crafting.*;
import dev.graphassembly.integration.OperationCompletion;
import dev.graphassembly.integration.vanilla.menu.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Native menu transaction; preview is deliberately not an authorization or an extractable stack. */
public final class SmithingTransaction {
    private SmithingTransaction() {}
    public static final class Context {
        final SmithingMenu menu;final Player player;
        boolean busy,invalid,closed,handled;
        Context(SmithingMenu menu,Player player){this.menu=menu;this.player=player;}
    }
    private static SmithingAccess access(Context c){return (SmithingAccess)c.menu;}
    private static Context context(AbstractContainerMenu menu) {
        return menu.getClass()==SmithingMenu.class?((SmithingAccess)menu).graphAssembly$smithing():null;
    }
    public static void initialize(SmithingMenu menu,Player player) {
        if(menu.getClass()==SmithingMenu.class)((SmithingAccess)menu).graphAssembly$smithing(new Context(menu,player));
    }
    private static List<ItemStack> inputs(Context c) {
        var result=new ArrayList<ItemStack>();for(int i=0;i<3;i++)result.add(access(c).graphAssembly$inputs().getItem(i).copy());return result;
    }
    private record Snapshot(net.minecraft.world.level.Level level,List<RecipeHolder<?>> definitions,List<ItemStack> inputs,
                            ItemStack result,List<ItemStack> inventory,ItemStack carried,AbstractContainerMenu active) {
        static Snapshot capture(Context c) {
            return new Snapshot(c.player.level(),new ArrayList<>(c.player.level().getRecipeManager().getRecipes()),SmithingTransaction.inputs(c),
                c.menu.getSlot(3).getItem().copy(),MenuTransfer.inventory(c.player),c.menu.getCarried().copy(),c.player.containerMenu);
        }
        boolean current(Context c) {
            if(c.invalid||c.closed||c.player.level()!=level||c.player.containerMenu!=active
                ||!ItemStack.listMatches(inputs,SmithingTransaction.inputs(c))||!ItemStack.matches(result,c.menu.getSlot(3).getItem())
                ||!ItemStack.matches(carried,c.menu.getCarried())||!ItemStack.listMatches(inventory,MenuTransfer.inventory(c.player)))return false;
            var now=new ArrayList<>(level.getRecipeManager().getRecipes());if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
    }
    public static boolean update(SmithingMenu menu) {
        var c=context(menu);if(c==null)return false;if(c.busy||c.closed)return true;
        c.busy=true;c.invalid=false;
        try {
            var before=Snapshot.capture(c);var lookup=SmithingLookup.find(c.player.level(),access(c).graphAssembly$inputs());
            var preview=lookup.operation()==null?ItemStack.EMPTY:CraftingLookup.preview(c.player.level(),lookup.operation(),lookup.carrier());
            if(!before.current(c)){clear(c);return true;}
            boolean was=c.handled;c.handled=lookup.handled();
            ((CraftingPreview)access(c).graphAssembly$result()).graphAssembly$preview(c.handled);
            if(c.handled){access(c).graphAssembly$clearRecipe();access(c).graphAssembly$result().setRecipeUsed(null);menu.getSlot(3).set(preview);}
            else if(was){access(c).graphAssembly$clearRecipe();menu.getSlot(3).set(ItemStack.EMPTY);}
            return c.handled;
        } catch(RuntimeException invalid){clear(c);return true;}
        finally{c.busy=false;}
    }
    private static void clear(Context c) {
        c.invalid=true;c.handled=true;access(c).graphAssembly$clearRecipe();access(c).graphAssembly$result().setRecipeUsed(null);
        ((CraftingPreview)access(c).graphAssembly$result()).graphAssembly$preview(true);c.menu.getSlot(3).set(ItemStack.EMPTY);
    }
    public static boolean guarded(SmithingMenu menu) {
        var c=context(menu);return c!=null&&(c.busy||c.closed||update(menu));
    }
    public static boolean admits(SmithingMenu menu,int physical,ItemStack stack,java.util.function.Predicate<ItemStack> ordinary) {
        // Slot predicates are created during the superclass constructor, but invoked only later.
        var c=context(menu);if(c==null)return ordinary.test(stack);
        if(c.closed)return false;
        return SmithingLookup.admits(c.player.level(),physical,stack)||!Workpiece.has(stack)&&ordinary.test(stack);
    }
    public static Integer route(SmithingMenu menu,ItemStack stack) {
        var c=context(menu);if(c==null)return null;
        for(int physical=0;physical<3;physical++)if(!menu.getSlot(physical).hasItem()&&SmithingLookup.admits(c.player.level(),physical,stack))return physical;
        return Workpiece.has(stack)?-1:null;
    }
    public static boolean click(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player) {
        var c=context(menu);if(c==null)return false;
        if(c.busy){c.invalid=true;return true;}if(c.closed)return true;if(slot!=3)return false;
        boolean was=c.handled;if(!update(c.menu)&&!was)return false;
        // A failed preview refresh is a failed extraction, not permission to retry a new plan
        // in this same click after the callback has changed inputs, definitions, or reentered.
        if(c.invalid)return true;
        if(c.player!=player||player.level().isClientSide())return true;
        try {
            if(type==ClickType.PICKUP&&(button==0||button==1)||type==ClickType.SWAP)commit(c,type,button);
            else if(type==ClickType.QUICK_MOVE)while(commit(c,type,button)){if(!update(c.menu)||c.invalid)break;}
        } finally{update(c.menu);menu.broadcastChanges();}
        return true;
    }
    private static MenuTransfer.Plan transfer(Context c,List<ItemStack> outputs,ClickType type,int button,MenuTransfer.Limits limits) {
        return MenuTransfer.plan(MenuTransfer.inventory(c.player),c.menu.getCarried(),outputs,type,button,limits);
    }
    private static boolean commit(Context c,ClickType type,int button) {
        if(c.closed||c.busy||c.player.containerMenu!=c.menu)return false;
        c.busy=true;c.invalid=false;
        try {
            var before=Snapshot.capture(c);var level=c.player.level();
            if(!c.menu.stillValid(c.player)||!before.current(c))return false;
            var index=GraphIndex.get(level);var found=SmithingLookup.find(level,access(c).graphAssembly$inputs());
            var operation=found.operation();if(operation==null||!before.current(c))return false;
            var prepared=OperationCompletion.prepare(level,operation,found.carrier());
            var limits=new MenuTransfer.Limits(c.player.getInventory().getMaxStackSize());limits.prepare(before.carried());
            if(!prepared.outputsEnabled(level)||!prepared.allOutcomesFit(limits::prepare,limits::limit,
                outputs->transfer(c,outputs,type,button,limits)!=null))return false;
            var freshLookup=SmithingLookup.find(level,access(c).graphAssembly$inputs());var fresh=freshLookup.operation();
            if(fresh==null||fresh.graph()!=operation.graph()||fresh.node()!=operation.node()||!c.menu.stillValid(c.player)
                ||!ItemStack.matches(found.carrier(),freshLookup.carrier())
                ||GraphIndex.get(level)!=index||!before.current(c))return false;
            // All external codecs, ingredient predicates, feature and capacity queries precede RNG.
            var outputs=prepared.roll(level.random);var delivery=transfer(c,outputs,type,button,limits);
            if(delivery==null)throw new IllegalStateException("Smithing capacity changed after preflight");
            c.menu.getSlot(3).set(ItemStack.EMPTY);
            var storage=((SimpleContainer)access(c).graphAssembly$inputs()).getItems();
            for(int i=0;i<3;i++){var input=storage.get(i).copy();input.shrink(1);storage.set(i,input);}
            for(int i=0;i<delivery.inventory().size();i++)c.player.getInventory().setItem(i,delivery.inventory().get(i));
            c.menu.setCarried(delivery.carried());access(c).graphAssembly$inputs().setChanged();
            for(var output:outputs)if(!output.isEmpty())output.copy().onCraftedBy(level,c.player,output.getCount());
            level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(operation.graph().id()))
                .ifPresent(holder->c.player.triggerRecipeCrafted(holder,before.inputs()));
            access(c).graphAssembly$sound();return true;
        } catch(RuntimeException invalid){return false;}
        finally{c.busy=false;}
    }
    public static void close(AbstractContainerMenu menu) {
        var c=context(menu);if(c==null)return;c.closed=true;if(c.busy)c.invalid=true;c.menu.getSlot(3).set(ItemStack.EMPTY);
    }
}
