package dev.graphassembly.integration.vanilla.anvil;

import dev.graphassembly.integration.vanilla.crafting.*;
import dev.graphassembly.integration.OperationCompletion;
import dev.graphassembly.integration.vanilla.menu.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Exact native menu scope. Host calculation and onTake retain their complete event/mixin chains. */
public final class AnvilTransaction {
    private AnvilTransaction() {}
    public static final class Context {
        final AnvilMenu menu;final Player player;
        boolean busy,invalid,closed,handled,requestedNative,paying,pickupPermit,takePermit;
        Context(AnvilMenu menu,Player player){this.menu=menu;this.player=player;}
    }
    private static AnvilAccess access(Context c){return (AnvilAccess)c.menu;}
    private static Context context(AbstractContainerMenu menu) {
        return menu.getClass()==AnvilMenu.class?((AnvilAccess)menu).graphAssembly$anvil():null;
    }
    public static void initialize(AnvilMenu menu,Player player) {
        if(menu.getClass()==AnvilMenu.class)((AnvilAccess)menu).graphAssembly$anvil(new Context(menu,player));
    }
    private static List<ItemStack> inputs(Context c) {
        return java.util.stream.IntStream.range(0,2).mapToObj(i->access(c).graphAssembly$anvilInputs().getItem(i).copy()).toList();
    }
    private static void previewGuard(Context c,boolean value){((CraftingPreview)access(c).graphAssembly$anvilResult()).graphAssembly$preview(value);}
    private record Snapshot(net.minecraft.world.level.Level level,List<RecipeHolder<?>> definitions,List<ItemStack> inputs,
                            String name,int xp,boolean creative,boolean valid,AbstractContainerMenu active,List<ItemStack> inventory,
                            ItemStack carried,ItemStack result,int cost,int materials) {
        static Snapshot capture(Context c) {
            return new Snapshot(c.player.level(),new ArrayList<>(c.player.level().getRecipeManager().getRecipes()),AnvilTransaction.inputs(c),
                access(c).graphAssembly$anvilName(),c.player.experienceLevel,c.player.getAbilities().instabuild,c.menu.stillValid(c.player),
                c.player.containerMenu,MenuTransfer.inventory(c.player),c.menu.getCarried().copy(),c.menu.getSlot(2).getItem().copy(),
                c.menu.getCost(),access(c).graphAssembly$anvilMaterials());
        }
        boolean current(Context c) {
            if(c.invalid||c.closed||c.player.level()!=level||c.player.containerMenu!=active
                ||!Objects.equals(name,access(c).graphAssembly$anvilName())||xp!=c.player.experienceLevel
                ||creative!=c.player.getAbilities().instabuild||valid!=c.menu.stillValid(c.player)
                ||!ItemStack.listMatches(inputs,AnvilTransaction.inputs(c))||!ItemStack.matches(carried,c.menu.getCarried())
                ||!ItemStack.matches(result,c.menu.getSlot(2).getItem())||cost!=c.menu.getCost()||materials!=access(c).graphAssembly$anvilMaterials()
                ||!ItemStack.listMatches(inventory,MenuTransfer.inventory(c.player)))return false;
            var now=new ArrayList<>(level.getRecipeManager().getRecipes());if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
    }
    private record NativePlan(AnvilLookup.Native result,int materials) {}
    private static NativePlan calculate(Context c,Runnable original) {
        var result=c.menu.getSlot(2).getItem().copy();int cost=c.menu.getCost(),materials=access(c).graphAssembly$anvilMaterials();
        boolean guard=((CraftingPreview)access(c).graphAssembly$anvilResult()).graphAssembly$preview();
        boolean protect=guard||c.handled;
        if(!protect)protect=AnvilLookup.find(c.player.level(),inputs(c),new AnvilLookup.Native(ItemStack.EMPTY,0,0)).handled();
        // Native calculation writes directly; it does not need permission to remove old previews.
        previewGuard(c,protect);
        if(protect)c.menu.getSlot(2).set(ItemStack.EMPTY);
        try {
            original.run();
            int nativeMaterials=access(c).graphAssembly$anvilMaterials();int right=access(c).graphAssembly$anvilInputs().getItem(1).getCount();
            return new NativePlan(new AnvilLookup.Native(c.menu.getSlot(2).getItem(),c.menu.getCost(),nativeMaterials>0?Math.min(right,nativeMaterials):right),nativeMaterials);
        } finally {
            c.menu.getSlot(2).set(result);access(c).graphAssembly$anvilCost(cost,materials);previewGuard(c,guard||protect);
        }
    }
    private static NativePlan calculate(Context c) {
        return calculate(c,()->{c.requestedNative=true;try{c.menu.createResult();}finally{c.requestedNative=false;}});
    }
    /** WrapMethod boundary includes upstream HEAD-cancelled native calculations. */
    public static void result(AnvilMenu menu,Runnable original) {
        var c=context(menu);if(c==null){original.run();return;}
        if(c.requestedNative){c.requestedNative=false;original.run();return;}
        if(c.paying){original.run();return;} // Legitimate native onTake input notifications.
        if(c.busy){c.invalid=true;return;}
        if(c.closed){clear(c);return;}
        c.busy=true;c.invalid=false;
        try {
            var before=Snapshot.capture(c);var host=calculate(c,original);
            var lookup=AnvilLookup.find(c.player.level(),before.inputs(),host.result());
            if(!before.current(c)){clear(c);return;}
            c.handled=lookup.handled();previewGuard(c,c.handled);
            access(c).graphAssembly$anvilCost(host.result().cost(),host.materials());
            if(!c.handled){c.menu.getSlot(2).set(host.result().result());return;}
            access(c).graphAssembly$anvilResult().setRecipeUsed(null);
            var preview=lookup.operation()==null?ItemStack.EMPTY:CraftingLookup.preview(c.player.level(),lookup.operation(),host.result().result());
            // Preview output codecs are callbacks too; compare before installing their result.
            access(c).graphAssembly$anvilCost(before.cost(),before.materials());
            if(!before.current(c)){clear(c);return;}
            access(c).graphAssembly$anvilCost(host.result().cost(),host.materials());c.menu.getSlot(2).set(preview);
            // A rejected/cancelled graph preparation is this click's terminal failure.
            // Do not recalculate it into a newly valid commit during the same extraction attempt.
            if(lookup.operation()==null)c.invalid=true;
        } catch(RuntimeException invalid){clear(c);}
        finally{c.busy=false;}
    }
    private static void clear(Context c) {
        c.invalid=true;c.handled=true;previewGuard(c,true);access(c).graphAssembly$anvilResult().setRecipeUsed(null);c.menu.getSlot(2).set(ItemStack.EMPTY);
    }
    /** Consume distinct entry permits before native code can invoke external callbacks. */
    public static boolean guardedPickup(AnvilMenu menu,Player player,boolean hasItem) {
        var c=context(menu);if(c==null)return false;
        if(c.pickupPermit){c.pickupPermit=false;return false;}
        // AnvilScreen queries native mayPickup to color the synced cost. Do not arm
        // a take permit or change the ResultContainer guard for this client query.
        if(c.player==player&&player.level().isClientSide()&&!c.busy&&!c.closed
            &&hasItem&&!menu.getSlot(2).getItem().isEmpty())return false;
        return c.busy||c.closed||c.handled;
    }
    public static boolean guardedTake(AnvilMenu menu) {
        var c=context(menu);if(c==null)return false;
        if(c.takePermit){c.takePermit=false;return false;}
        return c.busy||c.closed||c.handled;
    }
    private static boolean nativePickup(Context c,NativePlan host) {
        int cost=c.menu.getCost(),materials=access(c).graphAssembly$anvilMaterials();
        c.pickupPermit=true;access(c).graphAssembly$anvilCost(host.result().cost(),host.materials());
        try{return access(c).graphAssembly$anvilPickup(c.player);}
        finally{c.pickupPermit=false;access(c).graphAssembly$anvilCost(cost,materials);}
    }
    public static boolean click(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player) {
        var c=context(menu);if(c==null)return false;
        if(c.busy){c.invalid=true;return true;}if(c.closed)return true;if(slot!=2)return false;
        boolean was=c.handled;
        if(!was) {
            // A late graph reload can make an ordinary preview claimable. Ingredient
            // predicates are callbacks: guard the probe before asking if it is ours.
            c.busy=true;c.invalid=false;
            var result=(CraftingPreview)access(c).graphAssembly$anvilResult();
            boolean previousGuard=result.graphAssembly$preview();
            previewGuard(c,true);
            try {
                var before=Snapshot.capture(c);
                var claim=AnvilLookup.find(before.level(),before.inputs(),new AnvilLookup.Native(ItemStack.EMPTY,0,0));
                if(!before.current(c)){clear(c);return true;}
                if(!claim.handled()){previewGuard(c,previousGuard);return false;}
            } catch(RuntimeException invalid){clear(c);return true;}
            finally{c.busy=false;}
        }
        c.menu.createResult();
        if(!was&&!c.handled)return false;
        if(c.invalid||c.player!=player||player.level().isClientSide())return true;
        try {
            if(type==ClickType.PICKUP&&(button==0||button==1)||type==ClickType.SWAP)commit(c,type,button);
            else if(type==ClickType.QUICK_MOVE)while(commit(c,type,button)){c.menu.createResult();if(!c.handled||c.invalid)break;}
        } finally{if(!c.closed)c.menu.createResult();menu.broadcastChanges();}
        return true;
    }
    private static MenuTransfer.Plan transfer(Context c,List<ItemStack> outputs,ClickType type,int button,MenuTransfer.Limits limits) {
        return MenuTransfer.plan(MenuTransfer.inventory(c.player),c.menu.getCarried(),outputs,type,button,limits);
    }
    private static boolean same(NativePlan a,NativePlan b) {
        return a.result().cost()==b.result().cost()&&a.result().consumed()==b.result().consumed()&&a.materials()==b.materials()
            &&ItemStack.matches(a.result().result(),b.result().result());
    }
    private static boolean commit(Context c,ClickType type,int button) {
        if(c.busy||c.closed||c.player.containerMenu!=c.menu)return false;
        c.busy=true;c.invalid=false;boolean paymentStarted=false;
        try {
            var before=Snapshot.capture(c);if(!before.valid()||!before.current(c))return false;
            var level=c.player.level();var index=GraphIndex.get(level);var host=calculate(c);
            var lookup=AnvilLookup.find(level,before.inputs(),host.result());var operation=lookup.operation();
            if(operation==null||!before.current(c)||!nativePickup(c,host))return false;
            var prepared=OperationCompletion.prepare(level,operation,host.result().result());
            var limits=new MenuTransfer.Limits(c.player.getInventory().getMaxStackSize());limits.prepare(before.carried());
            if(!prepared.outputsEnabled(level)||!prepared.allOutcomesFit(limits::prepare,limits::limit,
                outputs->transfer(c,outputs,type,button,limits)!=null))return false;
            var freshHost=calculate(c);var fresh=AnvilLookup.find(level,before.inputs(),freshHost.result()).operation();
            if(fresh==null||fresh.graph()!=operation.graph()||fresh.node()!=operation.node()||!same(host,freshHost)
                ||!nativePickup(c,freshHost)||GraphIndex.get(level)!=index||!before.current(c))return false;
            // All native update callbacks, codecs and capacity queries precede graph RNG/payment.
            var outputs=prepared.roll(level.random);var delivery=transfer(c,outputs,type,button,limits);
            if(delivery==null)throw new IllegalStateException("Anvil capacity changed after preflight");
            // Match normal native pickup: result slot is already empty when onTake begins.
            // Pass the independently prepared native stack only as the onTake argument.
            paymentStarted=true;previewGuard(c,true);
            c.menu.getSlot(2).set(ItemStack.EMPTY);access(c).graphAssembly$anvilCost(host.result().cost(),host.materials());
            try {
                // Native pickup has already transferred its result before onTake fires the success
                // event. Do likewise, then never overwrite independent inventory changes made there.
                for(int i=0;i<delivery.inventory().size();i++)c.player.getInventory().setItem(i,delivery.inventory().get(i));
                c.menu.setCarried(delivery.carried());
                c.paying=true;c.takePermit=true;
                access(c).graphAssembly$anvilTake(c.player,host.result().result());
            } finally {c.paying=false;c.takePermit=false;previewGuard(c,true);c.menu.getSlot(2).set(ItemStack.EMPTY);}
            level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(operation.graph().id()))
                .ifPresent(holder->c.player.triggerRecipeCrafted(holder,before.inputs()));
            return true;
        } catch(RuntimeException invalid) {
            if(paymentStarted)throw invalid; // A paid native callback must never become a retryable failure.
            return false;
        } finally{c.busy=false;}
    }
    public static void close(AbstractContainerMenu menu) {
        var c=context(menu);if(c==null)return;c.closed=true;if(c.busy)c.invalid=true;c.menu.getSlot(2).set(ItemStack.EMPTY);
    }
}
