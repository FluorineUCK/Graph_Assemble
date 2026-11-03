package dev.graphassembly.integration.stonecutting;

import dev.graphassembly.core.*;
import dev.graphassembly.integration.*;
import dev.graphassembly.integration.menu.MenuTransfer;
import dev.graphassembly.integration.crafting.CraftingPreview;
import dev.graphassembly.integration.furnace.CookingCompletion;
import dev.graphassembly.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Menu-local explicit selection. A representative stack is never an extraction capability. */
public final class StonecuttingTransaction {
    private StonecuttingTransaction() {}
    public static final class Context {
        final StonecutterMenu menu; final Player player;
        boolean busy,handled,closed; ItemStack identity=ItemStack.EMPTY; GraphIndex index;
        List<Choice> choices=List.of(); String selected;
        List<RecipeHolder<?>> definitions; String displayedSelection;
        Context(StonecutterMenu menu,Player player){this.menu=menu;this.player=player;}
    }
    private record Choice(String key,GraphOperation operation,RecipeHolder<StonecutterRecipe> recipe) {}

    private record Snapshot(net.minecraft.world.level.Level level,List<RecipeHolder<?>> definitions,ItemStack input,ItemStack result,List<ItemStack> inventory,
                            ItemStack carried,AbstractContainerMenu active,String selected,int selectedIndex) {
        static Snapshot capture(Context c) {
            return new Snapshot(c.player.level(),new ArrayList<>(c.player.level().getRecipeManager().getRecipes()),c.menu.container.getItem(0).copy(),
                c.menu.getSlot(1).getItem().copy(),StonecuttingTransaction.inventory(c),c.menu.getCarried().copy(),c.player.containerMenu,c.selected,access(c).graphAssembly$selected());
        }
        boolean current(Context c) {
            if(c.closed || c.player.level()!=level || c.player.containerMenu!=active || !Objects.equals(selected,c.selected)
                || selectedIndex!=access(c).graphAssembly$selected() || !ItemStack.matches(input,c.menu.container.getItem(0))
                || !ItemStack.matches(result,c.menu.getSlot(1).getItem()) || !ItemStack.matches(carried,c.menu.getCarried())
                || !ItemStack.listMatches(inventory,StonecuttingTransaction.inventory(c)))return false;
            var now=new ArrayList<>(c.player.level().getRecipeManager().getRecipes());
            if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
    }
    private static StonecuttingAccess access(Context c){return (StonecuttingAccess)c.menu;}
    public static void initialize(StonecutterMenu menu,Player player) {
        if(menu.getClass()==StonecutterMenu.class)((StonecuttingAccess)menu).graphAssembly$stonecutting(new Context(menu,player));
    }
    private static List<ItemStack> inventory(Context c) {
        var result=new ArrayList<ItemStack>();
        for(int i=0;i<c.player.getInventory().getContainerSize();i++)result.add(c.player.getInventory().getItem(i).copy());
        return result;
    }
    /** Also runs on the client, where the native screen reads these same deterministic projections. */
    public static boolean update(StonecutterMenu menu) {
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting(); if(c==null)return false;
        if(c.busy)return true;
        if(c.closed)return c.handled;
        // Rendering uses a stable menu-local projection. Extraction below always rebinds afresh.
        var definitions=new ArrayList<RecipeHolder<?>>(c.player.level().getRecipeManager().getRecipes());
        if(sameDefinitions(c.definitions,definitions)&&ItemStack.matches(c.identity,menu.container.getItem(0))
            && Objects.equals(c.selected,c.displayedSelection)&&GraphIndex.cached(c.player.level(),c.index))return c.handled;
        c.busy=true;
        try {
            var snapshot=Snapshot.capture(c); var input=menu.container.getItem(0);
            var index=GraphIndex.get(c.player.level());
            var found=find(c,index,input);
            if(!snapshot.current(c)){invalidate(c);return true;}
            boolean was=c.handled;
            // A shared cache invalidation (e.g. integrated-client sync) is not a definition
            // replacement. Rebind fresh operations while retaining the explicit graph/node key.
            boolean same=sameDefinitions(c.definitions,snapshot.definitions()) && ItemStack.isSameItemSameComponents(input,c.identity);
            c.index=index;c.identity=input.copy();c.choices=found;
            c.handled=Workpiece.has(input)||!found.isEmpty();
            if(!same)c.selected=null;
            c.definitions=snapshot.definitions();c.displayedSelection=c.selected;
            if(!c.handled) {
                ((CraftingPreview)access(c).graphAssembly$result()).graphAssembly$preview(false);
                if(was){
                    access(c).graphAssembly$selected(-1);menu.getSlot(1).set(ItemStack.EMPTY);access(c).graphAssembly$resetNativeInput();
                    // A getter may be the first observer of a reload, before native slotsChanged runs.
                    // Discard old graph projections immediately rather than exposing a stale list.
                    access(c).graphAssembly$list(c.player.level().getRecipeManager().getRecipesFor(RecipeType.STONECUTTING,new SingleRecipeInput(input),c.player.level()));
                }
                if(was&&!same)access(c).graphAssembly$notifyChoices();
                return false;
            }
            access(c).graphAssembly$list(found.stream().map(Choice::recipe).toList());
            int selected=-1;
            for(int i=0;i<found.size();i++)if(found.get(i).key().equals(c.selected))selected=i;
            access(c).graphAssembly$selected(selected);
            ((CraftingPreview)access(c).graphAssembly$result()).graphAssembly$preview(true);
            access(c).graphAssembly$result().setRecipeUsed(null);
            menu.getSlot(1).set(selected<0?ItemStack.EMPTY:found.get(selected).recipe().value().getResultItem(c.player.level().registryAccess()).copy());
            // Publish list and selection first. Native listener may reenter hasInputItem while busy.
            if(!same)access(c).graphAssembly$notifyChoices();
            return true;
        } catch(RuntimeException invalid){invalidate(c);return true;}
        finally {c.busy=false;}
    }
    private static void invalidate(Context c) {
        c.handled=true;c.selected=null;c.choices=List.of();c.index=null;c.definitions=null;
        access(c).graphAssembly$list(List.of());access(c).graphAssembly$selected(-1);
        ((CraftingPreview)access(c).graphAssembly$result()).graphAssembly$preview(true);
        c.menu.getSlot(1).set(ItemStack.EMPTY);
    }
    private static boolean sameDefinitions(List<RecipeHolder<?>> before,List<RecipeHolder<?>> after) {
        if(before==null||before.size()!=after.size())return false;
        for(int i=0;i<before.size();i++)if(before.get(i)!=after.get(i))return false;
        return true;
    }
    private static List<Choice> find(Context c,GraphIndex index,ItemStack input) {
        if(input.isEmpty())return List.of();
        var level=c.player.level();var progress=Workpiece.read(level,input);var entries=new ArrayList<GraphIndex.Entry>();
        if(progress==null)entries.addAll(index.entries(RecipeType.STONECUTTING));
        else {
            var graph=index.graphs().get(progress.graphId());
            if(graph!=null)for(var id:GraphEngine.allowed(graph,progress))entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));
        }
        entries.sort(Comparator.comparing((GraphIndex.Entry e)->e.graph().id()).thenComparing(e->e.node().id()));
        var result=new ArrayList<Choice>();
        for(var e:entries) {
            if(index.nativeType(e.graph().id(),e.node().id())!=RecipeType.STONECUTTING
                || progress==null&&!e.ingredient().test(input) || !WorkpieceInputs.matches(level,e.graph(),e.node(),0,input))continue;
            var nativeRecipe=StonecuttingDefinition.create(level,e.node(),input);
            var operation=new GraphOperation(level,e.graph(),e.node(),input.copy(),List.of());
            var plan=GraphEngine.plan(e.graph(),progress,e.node().id(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
            ItemStack preview=input.copyWithCount(1);
            if(!plan.settled()) {
                var state=plan.state();
                if(!plan.branches().isEmpty())state=new Progress(state.graphId(),state.entry(),state.current(),state.marks(),state.checkpoints(),state.rewardCounts(),plan.branches().getFirst().to(),state.history(),state.pendingRoute());
                preview=Workpiece.write(plan.item().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem()).toString())?input:NativeSteps.stack(plan.item()),state);
            } else {
                var leaves=OutputTrees.leaves(plan.pool());if(!leaves.isEmpty())preview=NativeSteps.output(level,leaves.getFirst());
            }
            String key=e.graph().id()+"\u0000"+e.node().id();
            result.add(new Choice(key,operation,new RecipeHolder<>(ResourceLocation.parse(e.graph().id()),new StonecutterRecipe(nativeRecipe.getGroup(),Ingredient.of(input.getItem()),preview))));
        }
        return List.copyOf(result);
    }
    public static Boolean select(StonecutterMenu menu,int index) {
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting();if(c==null)return null;
        if(c.busy||c.closed)return false;
        if(!update(menu))return index<0||index>=menu.getRecipes().size()?false:null;
        if(index<0||index>=c.choices.size())return false;
        c.selected=c.choices.get(index).key();update(menu);menu.broadcastChanges();return true;
    }
    public static boolean click(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player) {
        if(menu.getClass()!=StonecutterMenu.class)return false;
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting();if(c==null)return false;
        if(c.busy||c.closed)return true;
        if(slot!=1)return false;
        boolean was=c.handled;
        if(!update(c.menu)&&!was)return false;
        if(player!=c.player||player.level().isClientSide())return true;
        try {
            if(type==ClickType.PICKUP&&(button==0||button==1)||type==ClickType.SWAP)commit(c,type,button);
            else if(type==ClickType.QUICK_MOVE)while(commit(c,type,button)){if(!update(c.menu))break;}
        } finally {update(c.menu);menu.broadcastChanges();}
        return true;
    }
    private static boolean commit(Context c,ClickType type,int button) {
        if(c.closed||c.busy||c.player.containerMenu!=c.menu||c.selected==null)return false;
        c.busy=true;
        try {
            // Baseline and busy scope precede even GraphIndex's external codecs/predicates.
            var snapshot=Snapshot.capture(c);var index=GraphIndex.get(c.player.level());
            if(index!=c.index)return false;
            var choices=find(c,index,c.menu.container.getItem(0));
            var choice=choices.stream().filter(p->p.key().equals(c.selected)).findFirst().orElse(null);
            if(choice==null||!snapshot.current(c))return false;
            var level=c.player.level();var operation=choice.operation();
            var prepared=CookingCompletion.prepare(level,operation);
            var limits=new MenuTransfer.Limits(c.player.getInventory().getMaxStackSize());
            limits.prepare(snapshot.carried());
            if(!StonecuttingOutputs.fits(level,operation,limits::prepare,limits::limit,
                outputs->transfer(c,outputs,type,button,limits)!=null))return false;
            if(!snapshot.current(c)||GraphIndex.get(level)!=index)return false;
            // After this boundary, settlement/placement uses only prepared templates and limits.
            // In particular no Item.getMaxStackSize or SimpleContainer.setItem is invoked.
            var outputs=prepared.roll(level.random);var transfer=transfer(c,outputs,type,button,limits);
            if(transfer==null)throw new IllegalStateException("Stonecutting capacity changed after preflight");
            c.menu.getSlot(1).set(ItemStack.EMPTY);
            c.menu.container.getItem(0).shrink(1);
            for(int i=0;i<transfer.inventory().size();i++)c.player.getInventory().setItem(i,transfer.inventory().get(i));
            c.menu.setCarried(transfer.carried());
            // All authoritative mutation is finished before callbacks; nested actions remain busy.
            c.menu.container.setChanged();
            for(var output:outputs)if(!output.isEmpty())output.copy().onCraftedBy(level,c.player,output.getCount());
            level.getRecipeManager().byKey(ResourceLocation.parse(operation.graph().id())).ifPresent(holder->c.player.triggerRecipeCrafted(holder,List.of(snapshot.input())));
            access(c).graphAssembly$sound();return true;
        } catch(RuntimeException invalid){return false;}
        finally {c.busy=false;}
    }
    private static MenuTransfer.Plan transfer(Context c,List<ItemStack> outputs,ClickType type,int button,MenuTransfer.Limits limits) {
        return MenuTransfer.plan(inventory(c),c.menu.getCarried(),outputs,type,button,limits);
    }
    public static void close(StonecutterMenu menu) {
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting();if(c==null)return;
        c.closed=true;c.selected=null;c.choices=List.of();menu.getSlot(1).set(ItemStack.EMPTY);
    }
    /** Native inventory routing is ordinary-recipe-only, so graph-only inputs need this local seam. */
    public static boolean quickInventory(StonecutterMenu menu,int slot,Player player) {
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting();
        if(c==null||slot<2||slot>=menu.slots.size())return false;
        if(c.busy||c.closed)return true;
        if(player!=c.player||player.level().isClientSide())return false;
        c.busy=true;
        try {
            var snapshot=Snapshot.capture(c);var incoming=menu.getSlot(slot).getItem();
            if(incoming.isEmpty()||find(c,GraphIndex.get(player.level()),incoming).isEmpty())return false;
            if(!snapshot.current(c))return true;
            var current=menu.container.getItem(0);
            if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,incoming))return true;
            int max=Math.min(menu.getSlot(0).getMaxStackSize(incoming),incoming.getMaxStackSize());
            int amount=Math.min(incoming.getCount(),max-current.getCount());if(amount<=0)return true;
            if(!snapshot.current(c))return true;
            ((net.minecraft.world.SimpleContainer)menu.container).getItems().set(0,incoming.copyWithCount(current.getCount()+amount));
            menu.getSlot(slot).set(incoming.copyWithCount(incoming.getCount()-amount));menu.container.setChanged();return true;
        } catch(RuntimeException invalid){return Workpiece.has(menu.getSlot(slot).getItem());}
        finally {c.busy=false;update(menu);menu.broadcastChanges();}
    }
    /** Native DataSlot synchronization binds the client index to its deterministic identity list. */
    public static void synchronizedSelection(AbstractContainerMenu menu,int data,int selected) {
        if(menu.getClass()!=StonecutterMenu.class||data!=0)return;
        var c=((StonecuttingAccess)menu).graphAssembly$stonecutting();
        if(c==null||!c.player.level().isClientSide()||c.busy||c.closed)return;
        c.selected=selected>=0&&selected<c.choices.size()?c.choices.get(selected).key():null;
    }
}
