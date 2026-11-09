package dev.graphassembly.integration;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.integration.sequence.NativeSequences;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.filter.GraphFilterItem;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Native basin lifecycle adapter. Plans are local to one synchronous match/commit. */
public final class BasinGraphRuntime {
    private BasinGraphRuntime() {}
    private static final java.util.Map<Recipe<?>,java.lang.ref.WeakReference<BasinOperatingBlockEntity>> MACHINES=
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public record Lookup(boolean handled,List<Recipe<?>> recipes) {}
    private record Plan(IItemHandler items,IFluidHandler fluids,int[] itemCounts,int[] fluidCounts,List<ItemStack> remainders,GraphOutputs.Prepared prepared) {}
    private record Place(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos) {}
    private static final MachineScopes<CommitScope> COMMITS=new MachineScopes<>(scope->scope.invalid=true);
    private static final class CommitScope implements AutoCloseable {
        private final Place place;private boolean invalid;
        private final MachineScopes<CommitScope>.Token token;
        private CommitScope(Place place,MachineScopes<CommitScope>.Token token){this.place=place;this.token=token;}
        static CommitScope enter(BasinBlockEntity basin) {
            return COMMITS.enter(basin.getLevel(),basin.getBlockPos(),token->
                new CommitScope(new Place(basin.getLevel(),basin.getBlockPos().immutable()),token));
        }
        @Override public void close(){token.close();}
    }
    private static List<Boolean> policy(BasinRecipe recipe,GraphOperation operation) {
        if(!dev.graphassembly.integration.vanilla.crafting.CraftingDefinition.SERIALIZERS.contains(operation.node().operation().recipe().get("type").getAsString()))return List.of();
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().recipes;
        return recipe.getType()==com.simibubi.create.AllRecipeTypes.MIXING.getType()
            ?List.of(config.allowShapelessInMixer.get(),config.allowShapedSquareInPress.get()):List.of(config.allowShapedSquareInPress.get());
    }
    private record Baseline(BasinBlockEntity basin,BasinOperatingBlockEntity machine,
        net.minecraft.world.level.block.state.BlockState block,net.minecraft.world.level.block.state.BlockState machineBlock,
        net.minecraft.world.level.block.state.BlockState heat,IItemHandler items,IFluidHandler fluids,
        List<ItemStack> stacks,List<net.neoforged.neoforge.fluids.FluidStack> liquids,ItemStack filter,
        List<net.minecraft.world.item.crafting.RecipeHolder<?>> definitions,List<Boolean> policy) {
        static Baseline capture(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation) {
            var level=basin.getLevel();var machine=MACHINES.get(recipe).get();
            var items=level.getCapability(Capabilities.ItemHandler.BLOCK,basin.getBlockPos(),null);
            var fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,basin.getBlockPos(),null);
            if(items==null||fluids==null||basin.getFilter()==null)return null;
            var stacks=new ArrayList<ItemStack>();for(int slot=0;slot<items.getSlots();slot++)stacks.add(items.getStackInSlot(slot).copy());
            var liquids=new ArrayList<net.neoforged.neoforge.fluids.FluidStack>();for(int tank=0;tank<fluids.getTanks();tank++)liquids.add(fluids.getFluidInTank(tank).copy());
            return new Baseline(basin,machine,level.getBlockState(basin.getBlockPos()),level.getBlockState(machine.getBlockPos()),
                level.getBlockState(basin.getBlockPos().below()),items,fluids,stacks,liquids,basin.getFilter().getFilter().copy(),
                new ArrayList<>(level.getRecipeManager().getRecipes()),BasinGraphRuntime.policy(recipe,operation));
        }
        boolean current(CommitScope scope,BasinRecipe recipe,GraphOperation operation) {
            var level=scope.place.level();
            if(scope.invalid||!currentMachine(basin,recipe)||machine!=MACHINES.get(recipe).get()
                ||level.getBlockState(basin.getBlockPos())!=block||level.getBlockState(machine.getBlockPos())!=machineBlock
                ||level.getBlockState(basin.getBlockPos().below())!=heat||!basin.canContinueProcessing()
                ||basin.getFilter()==null||!ItemStack.matches(filter,basin.getFilter().getFilter())
                ||!policy.equals(BasinGraphRuntime.policy(recipe,operation))||items.getSlots()!=stacks.size()||fluids.getTanks()!=liquids.size())return false;
            for(int slot=0;slot<stacks.size();slot++)if(!ItemStack.matches(stacks.get(slot),items.getStackInSlot(slot)))return false;
            for(int tank=0;tank<liquids.size();tank++)if(!net.neoforged.neoforge.fluids.FluidStack.matches(liquids.get(tank),fluids.getFluidInTank(tank)))return false;
            var now=new ArrayList<>(level.getRecipeManager().getRecipes());if(now.size()!=definitions.size())return false;
            for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
            return true;
        }
    }

    public static Lookup lookup(BasinOperatingBlockEntity machine, BasinBlockEntity basin) {
        RecipeType<?> type=machine instanceof MechanicalMixerBlockEntity ? com.simibubi.create.AllRecipeTypes.MIXING.getType()
            : machine instanceof MechanicalPressBlockEntity ? com.simibubi.create.AllRecipeTypes.COMPACTING.getType() : null;
        var result=lookup(type,basin);
        for(var recipe:result.recipes())MACHINES.put(recipe,new java.lang.ref.WeakReference<>(machine));
        return result;
    }
    private static Lookup lookup(RecipeType<?> type,BasinBlockEntity basin) {
        if(type==null || basin.getLevel()==null) return new Lookup(false,List.of());
        var level=basin.getLevel(); var index=GraphIndex.get(level);
        var items=level.getCapability(Capabilities.ItemHandler.BLOCK,basin.getBlockPos(),null);
        if(items==null) return new Lookup(false,List.of());
        List<Recipe<?>> found=new ArrayList<>(); List<GraphOperation> operations=new ArrayList<>();
        boolean ambiguous=false;
        var sourceTypes=CreateNativeRecipes.sourceTypes(type);
        for(int slot=0;slot<items.getSlots();slot++) {
            var input=items.getStackInSlot(slot);
            if(input.isEmpty() || input.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY)&&!NativeSequences.currentStep(level,input)) continue;
            try {
                var progress=Workpiece.read(level,input);
                List<GraphIndex.Entry> entries=new ArrayList<>();
                if(progress==null)for(var source:sourceTypes)entries.addAll(NativeSequences.entries(level,index,source));
                else {
                    var graph=index.graphs().get(progress.graphId());
                    if(graph==null) continue;
                    for(String id:GraphPipeline.allowed(graph,progress)) entries.add(new GraphIndex.Entry(graph,graph.nodes().get(id),null));
                }
                for(var entry:entries) {
                    var nativeType=NativeSequences.type(level,index,entry);
                    if(!sourceTypes.contains(nativeType)) continue;
                    if(progress==null && !entry.ingredient().test(input)) continue;
                    if(!WorkpieceInputs.matches(level,entry.graph(),entry.node(),0,input)) continue;
                    var operation=new GraphOperation(level,entry.graph(),entry.node(),input,List.of());
                    if(operations.stream().anyMatch(old->old.graph()==operation.graph() && old.node()==operation.node()
                        && ItemStack.isSameItemSameComponents(old.primary(),input))) continue;
                    var recipe=(BasinRecipe)(nativeType==type?NativeSteps.create(level,entry.graph(),entry.node(),input)
                        :CreateNativeRecipes.processing(level,entry.graph(),entry.node(),input,type));
                    if(recipe==null)continue;
                    GraphOperation.attach(recipe,operation);
                    var binding=bind(basin,recipe,operation);
                    if(binding.status()==InputAssignment.Status.NONE
                        || plan(basin,recipe,operation,binding.slots(),binding.status()==InputAssignment.Status.AMBIGUOUS)==null) continue;
                    if(binding.status()==InputAssignment.Status.AMBIGUOUS) {ambiguous=true; continue;}
                    if(binding.status()==InputAssignment.Status.UNIQUE) {
                        found.add(recipe); operations.add(operation);
                    }
                }
            } catch(IllegalArgumentException malformed) {
                // Malformed/stale components remain protected by the ordinary basin guard.
            }
        }
        if(ambiguous || found.size()>1) {
            GraphAssembly.LOGGER.debug("Ambiguous graph basin operation at {}",basin.getBlockPos());
            return new Lookup(true,List.of());
        }
        return new Lookup(!found.isEmpty(),found);
    }

    private static InputAssignment.Result bind(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation) {
        var level=basin.getLevel();
        if(level==null || !operation.current(level)) return new InputAssignment.Result(InputAssignment.Status.NONE,new int[0]);
        var items=level.getCapability(Capabilities.ItemHandler.BLOCK,basin.getBlockPos(),null);
        if(items==null) return new InputAssignment.Result(InputAssignment.Status.NONE,new int[0]);
        var stacks=new ArrayList<ItemStack>(items.getSlots());
        for(int slot=0;slot<items.getSlots();slot++) stacks.add(items.getStackInSlot(slot));
        return ItemInputBinding.bind(recipe.getIngredients(),stacks,
            (role,stack)->(role!=0 || operation.matchesPrimary(level,stack))
                && WorkpieceInputs.matches(level,operation.graph(),operation.node(),role,stack),
            role->role==0 || operation.node().inputs().workpieceInput(role)!=null);
    }

    public static boolean match(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation) {
        if(!currentMachine(basin,recipe))return false;
        var result=bind(basin,recipe,operation);
        return result.status()==InputAssignment.Status.UNIQUE && plan(basin,recipe,operation,result.slots())!=null;
    }

    private static Plan plan(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation,int[] slots) {
        return plan(basin,recipe,operation,slots,false);
    }
    private static Plan plan(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation,int[] slots,boolean ambiguous) {
        var level=basin.getLevel();
        if(level==null || !basin.canContinueProcessing() || !operation.current(level)) return null;
        var heat=BasinBlockEntity.getHeatLevelOf(level.getBlockState(basin.getBlockPos().below()));
        if(!recipe.getRequiredHeat().testBlazeBurner(heat)) return null;
        var filter=basin.getFilter(); if(filter==null) return null;
        if(!filter.test(filter.getFilter().getItem() instanceof GraphFilterItem ? operation.primary() : recipe.getResultItem(level.registryAccess()))) return null;
        var items=level.getCapability(Capabilities.ItemHandler.BLOCK,basin.getBlockPos(),null);
        var fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,basin.getBlockPos(),null);
        if(items==null || fluids==null) return null;
        int[] itemCounts=new int[items.getSlots()]; for(int slot:slots) itemCounts[slot]++;
        for(int slot=0;slot<itemCounts.length;slot++) if(itemCounts[slot]>0 && items.extractItem(slot,itemCounts[slot],true).getCount()!=itemCounts[slot]) return null;
        var ingredients=recipe.getFluidIngredients(); boolean[][] matrix=new boolean[ingredients.size()][fluids.getTanks()];
        int[] required=new int[ingredients.size()],available=new int[fluids.getTanks()];
        for(int j=0;j<available.length;j++) available[j]=fluids.getFluidInTank(j).getAmount();
        for(int i=0;i<required.length;i++) {
            required[i]=ingredients.get(i).amount();
            for(int j=0;j<available.length;j++) matrix[i][j]=ingredients.get(i).ingredient().test(fluids.getFluidInTank(j));
        }
        int[] fluidCounts=FluidAllocation.solve(matrix,required,available); if(fluidCounts==null) return null;
        // Create's DummyCraftingContainer has 0x0 dimensions in this version, so asCraftInput()
        // drops its items. Build an actual one-row crafting input to preserve each unit's remainder.
        var remainderInput=net.minecraft.world.item.crafting.CraftingInput.of(slots.length,1,
            Arrays.stream(slots).mapToObj(slot->items.getStackInSlot(slot).copyWithCount(1)).toList());
        var remainders=recipe.getRemainingItems(remainderInput).stream().filter(s->!s.isEmpty()).map(ItemStack::copy).toList();
        var tail=new ArrayList<>(GraphOutputs.maxByproducts(recipe));
        // For ambiguous roles, reserve a slot-order-independent upper bound of ALL eligible
        // container returns. Never let the first witness decide whether normal fallback is blocked.
        if(ambiguous) {
            for(int slot=0;slot<items.getSlots();slot++) {
                var stack=items.getStackInSlot(slot); if(stack.isEmpty()) continue;
                boolean eligible=false;
                for(int role=0;role<recipe.getIngredients().size();role++) {
                    if(recipe.getIngredients().get(role).test(stack)
                        && (role!=0 || operation.matchesPrimary(level,stack))
                        && WorkpieceInputs.matches(level,operation.graph(),operation.node(),role,stack)) {eligible=true;break;}
                }
                if(!eligible) continue;
                var remainder=stack.copyWithCount(1).getCraftingRemainingItem();
                if(!remainder.isEmpty()) for(int unit=0;unit<Math.min(stack.getCount(),recipe.getIngredients().size());unit++) tail.add(remainder.copy());
            }
        } else tail.addAll(remainders);
        var prepared=GraphOutputs.prepare(level,operation,recipe);
        if (!prepared.primary().allMaximalOutcomesFit(primary -> {
            var worst = new ArrayList<>(primary); worst.addAll(tail);
            return fits(basin, worst);
        })) return null;
        return new Plan(items,fluids,itemCounts,fluidCounts,remainders,prepared);
    }

    private static boolean fits(BasinBlockEntity basin,List<ItemStack> outputs) {
        if(!basin.acceptOutputs(outputs,List.of(),true)) return false;
        if(basin.getBlockState().getValue(BasinBlock.FACING)!=Direction.DOWN) return basin.canContinueProcessing();
        var inventory=basin.getOutputInventory();
        var copy=new ItemStackHandler(inventory.getSlots()) {
            @Override public int getSlotLimit(int slot) {return inventory.getSlotLimit(slot);}
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
                // BasinInventory keeps each component identity in one slot and uses the first free slot.
                // Do not instantiate BasinInventory here: its change callbacks would touch the live basin.
                int firstFree=-1;
                for(int i=0;i<getSlots();i++) {
                    if(i!=slot && ItemStack.isSameItemSameComponents(stack,getStackInSlot(i))) return stack;
                    if(getStackInSlot(i).isEmpty() && firstFree<0) firstFree=i;
                }
                if(getStackInSlot(slot).isEmpty() && firstFree!=slot) return stack;
                return super.insertItem(slot,stack,simulate);
            }
        };
        for(int i=0;i<copy.getSlots();i++) copy.setStackInSlot(i,inventory.getStackInSlot(i).copy());
        // Native simulate tests each output independently. Reserve cumulatively to prevent overflow.
        for(var stack:outputs) if(!ItemHandlerHelper.insertItemStacked(copy,stack.copy(),false).isEmpty()) return false;
        return true;
    }

    public static boolean apply(BasinBlockEntity basin,BasinRecipe recipe,GraphOperation operation,boolean simulate) {
        var level=basin.getLevel();
        if(level==null||!currentMachine(basin,recipe))return false;
        boolean committed=false;
        try(var scope=CommitScope.enter(basin)) {
        if(scope==null)return false;
        // Establish the baseline before index rebuild, predicates, remainder or output codecs.
        var baseline=Baseline.capture(basin,recipe,operation);
        if(baseline==null||!operation.current(level)||!baseline.current(scope,recipe,operation))return false;
        // The local recipe is bound to the accepting device, not the node's original CRAFTING
        // type. Re-run its actual current machine's config/compression/automation policy.
        var current=lookup(MACHINES.get(recipe).get(),basin);
        if(current.recipes().size()!=1) return false;
        var selected=GraphOperation.get(current.recipes().getFirst());
        if(selected.graph()!=operation.graph() || selected.node()!=operation.node()
            || !ItemStack.isSameItemSameComponents(selected.primary(),operation.primary())) return false;
        var binding=bind(basin,recipe,operation);
        if(binding.status()!=InputAssignment.Status.UNIQUE) return false;
        var plan=plan(basin,recipe,operation,binding.slots());
        if(plan==null||!baseline.current(scope,recipe,operation))return false;
        var prepared=plan.prepared();
        if(!CreateNativeRecipes.basinAdmitted(level,operation,recipe.getType())||!operation.current(level)
            ||!prepared.outputsEnabled(level)||!baseline.current(scope,recipe,operation))return false;
        if(simulate) return true;
        var outputs=new ArrayList<>(prepared.roll(level.random)); outputs.addAll(plan.remainders());
        // Both checks and commit run synchronously against the native basin handlers.
        // Guaranteed conservative capacity above includes every actual primary alternative.
        committed=true;
        for(int slot=0;slot<plan.itemCounts().length;slot++) if(plan.itemCounts()[slot]>0)
            plan.items().extractItem(slot,plan.itemCounts()[slot],false);
        for(int tank=0;tank<plan.fluidCounts().length;tank++) if(plan.fluidCounts()[tank]>0)
            plan.fluids().getFluidInTank(tank).shrink(plan.fluidCounts()[tank]);
        if(Arrays.stream(plan.fluidCounts()).anyMatch(i->i>0)) {
            basin.inputTank.forEach(SmartFluidTankBehaviour.TankSegment::onFluidStackChanged);
            basin.getBehaviour(SmartFluidTankBehaviour.OUTPUT).forEach(SmartFluidTankBehaviour.TankSegment::onFluidStackChanged);
        }
        if(!basin.acceptOutputs(outputs,List.of(),false)) throw new IllegalStateException("Validated native basin output capacity changed during synchronous commit");
        basin.notifyChangeOfContents(); basin.notifyUpdate();
        return true;
        } catch(RuntimeException invalidPreparation) {
            if(committed)throw invalidPreparation;
            return false;
        }
    }
    private static boolean currentMachine(BasinBlockEntity basin,Recipe<?> recipe) {
        var reference=MACHINES.get(recipe);var machine=reference==null?null:reference.get();var level=basin.getLevel();
        return level!=null&&machine!=null&&machine.getLevel()==level&&!machine.isRemoved()&&!basin.isRemoved()
            &&level.getBlockEntity(machine.getBlockPos())==machine&&level.getBlockEntity(basin.getBlockPos())==basin
            &&machine.getBlockPos().below(2).equals(basin.getBlockPos());
    }
}
