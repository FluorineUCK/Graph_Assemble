package dev.graphassembly.integration;

import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.integration.crafting.CraftingLookup;
import dev.graphassembly.integration.furnace.CookingCompletion;
import dev.graphassembly.mixin.create.GroupedItemsAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

/** One native assembling call, with existing graph binding/settlement and original delivery phases. */
public final class MechanicalCrafterTransaction {
    private MechanicalCrafterTransaction() {}
    public interface View {
        RecipeGridHandler.GroupedItems graphAssembly$groupedItems();
        int graphAssembly$countDown();
    }
    private record Place(Level level,BlockPos pos) {}
    private static final Map<Place,Scope> ACTIVE=new HashMap<>();
    private static final ThreadLocal<Deque<Scope>> CALLS=ThreadLocal.withInitial(ArrayDeque::new);
    public static Map<Pair<Integer,Integer>,ItemStack> grid(RecipeGridHandler.GroupedItems items) {
        return ((GroupedItemsAccess)items).graphAssembly$grid();
    }
    private static final class Grid extends SimpleContainer implements CraftingContainer {
        private final int width,height;
        Grid(CraftingInput input){super(input.items().stream().map(ItemStack::copy).toArray(ItemStack[]::new));width=input.width();height=input.height();}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
    }
    private record Cell(MechanicalCrafterBlockEntity machine,BlockState block,RecipeGridHandler.GroupedItems grouped,
                        Map<Pair<Integer,Integer>,ItemStack> contents,ItemStack inventory,int countDown) {
        static Cell capture(MechanicalCrafterBlockEntity machine) {
            var view=(View)machine;var grouped=view.graphAssembly$groupedItems();var copy=new HashMap<Pair<Integer,Integer>,ItemStack>();
            grid(grouped).forEach((key,stack)->copy.put(key,stack.copy()));
            return new Cell(machine,machine.getLevel().getBlockState(machine.getBlockPos()),grouped,copy,
                machine.getInventory().getStackInSlot(0).copy(),view.graphAssembly$countDown());
        }
        boolean current(Level level) {
            var view=(View)machine;
            if(machine.getLevel()!=level||machine.isRemoved()||level.getBlockEntity(machine.getBlockPos())!=machine
                ||level.getBlockState(machine.getBlockPos())!=block||view.graphAssembly$groupedItems()!=grouped
                ||view.graphAssembly$countDown()!=countDown||!ItemStack.matches(inventory,machine.getInventory().getStackInSlot(0)))return false;
            var now=grid(grouped);if(!now.keySet().equals(contents.keySet()))return false;
            for(var entry:contents.entrySet())if(!ItemStack.matches(entry.getValue(),now.get(entry.getKey())))return false;
            return true;
        }
    }
    public static final class Scope implements AutoCloseable {
        private final MechanicalCrafterBlockEntity machine;private final Place place;
        private boolean invalid,handled,ready,committed;
        private List<ItemStack> outputs=List.of();
        private Scope(MechanicalCrafterBlockEntity machine){this.machine=machine;place=new Place(machine.getLevel(),machine.getBlockPos().immutable());}
        public boolean prepare() {
            if(invalid){handled=true;return false;}
            try {
                var level=place.level();var chain=RecipeGridHandler.getAllCraftersOfChain(machine);
                if(chain==null){handled=true;return false;}
                var cells=chain.stream().map(Cell::capture).toList();
                var definitions=new ArrayList<>(level.getRecipeManager().getRecipes());boolean config=AllConfigs.server().recipes.allowRegularCraftingInCrafter.get();
                var grouped=((View)machine).graphAssembly$groupedItems();grouped.calcStats();
                var input=MechanicalCraftingInput.of(grouped);var container=new Grid(input);
                var lookup=CraftingLookup.find(level,container,(operation,recipe)->CreateNativeRecipes.crafterAdmitted(level,operation,input));
                if(!current(cells,definitions,config)){handled=true;return false;}
                handled=lookup.handled();if(!handled)return true;
                var plan=lookup.plan();if(plan==null||input.items().stream().anyMatch(stack->!stack.isEmpty()&&stack.getCount()!=1))return false;
                var prepared=CookingCompletion.prepare(level,plan.operation());
                var remainders=new ArrayList<ItemStack>();
                // Preserve native mechanical-crafter per-cell container semantics, exactly once,
                // but invoke its extension callbacks before the final validation/RNG boundary.
                for(var stack:input.items())if(!stack.isEmpty()&&stack.hasCraftingRemainingItem())remainders.add(stack.getCraftingRemainingItem().copy());
                if(!CreateNativeRecipes.crafterAdmitted(level,plan.operation(),input)||!plan.operation().current(level)
                    ||!prepared.outputsEnabled(level)||!current(cells,definitions,config))return false;
                var result=new ArrayList<>(prepared.roll(level.random));result.addAll(remainders);outputs=List.copyOf(result);ready=true;
                return true;
            } catch(RuntimeException invalidPreparation){handled=true;return false;}
        }
        private boolean current(List<Cell> cells,List<RecipeHolder<?>> definitions,boolean config) {
            if(invalid||config!=AllConfigs.server().recipes.allowRegularCraftingInCrafter.get())return false;
            for(var cell:cells)if(!cell.current(place.level()))return false;
            var now=new ArrayList<>(place.level().getRecipeManager().getRecipes());if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
        @Override public void close(){ACTIVE.remove(place);var calls=CALLS.get();calls.pop();if(calls.isEmpty())CALLS.remove();}
    }
    public static Scope open(MechanicalCrafterBlockEntity machine) {
        var place=new Place(machine.getLevel(),machine.getBlockPos().immutable());var active=ACTIVE.get(place);
        if(active!=null){active.invalid=true;return null;}
        var scope=new Scope(machine);ACTIVE.put(place,scope);CALLS.get().push(scope);return scope;
    }
    public static Scope current(){return CALLS.get().peek();}
    public static boolean substituting(){var scope=current();return scope!=null&&scope.ready;}
    public static ItemStack representative(){var scope=current();return scope.outputs.isEmpty()?ItemStack.EMPTY:scope.outputs.getFirst();}
    public static RecipeGridHandler.GroupedItems commit(RecipeGridHandler.GroupedItems ordinary) {
        var scope=current();if(scope==null||!scope.ready||scope.committed)return ordinary;
        var result=new RecipeGridHandler.GroupedItems();int index=0;
        for(var output:scope.outputs)if(!output.isEmpty())grid(result).put(Pair.of(index++,0),output);
        scope.committed=true;return result;
    }
}
