package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class MultiWorkpieceGameTests {
    private static final String ID = "test:multi_workpiece";
    private static GraphRecipe fixture(String type, boolean keep) {
        return new GraphRecipe(JsonParser.parseString("""
          {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
           "nodes":[
             {"id":"body","start":true,"mark_amounts":{"quality":3},"recipe":{"type":"create:pressing"}},
             {"id":"insert","start":true,"ingredient":{"item":"minecraft:copper_ingot"},"mark_amounts":{"quality":99},"recipe":{"type":"create:cutting"}},
             {"id":"join","checkpoint":true,"recipe":{"type":"%s","ingredients":[{"item":"minecraft:iron_nugget"}]%s},
               "workpiece_inputs":[{"item_input":0,"current_nodes":["body"]},
                 {"item_input":1,"current_nodes":["insert"],"requirements":{"mark_ranges":{"quality":{"min":90,"max":100}}}}]},
             {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
           "edges":[{"from":"body","to":"join"},{"from":"insert","to":"join"},{"from":"join","to":"finish"}],
           "outputs":[{"requirements":{"mark_ranges":{"quality":{"min":3,"max":3}},"checkpoint_counts":{"join":{"min":1,"max":1}}},"results":[{"item":"minecraft:diamond"}]}]}
          """.formatted(type, type.equals("create:deploying") ? ",\"keep_held_item\":" + keep : "")).getAsJsonObject());
    }
    private static ItemStack piece(GraphRecipe recipe, String start) {
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(recipe.definition(ID), null, start, "minecraft:iron_ingot"), () -> 0)).progress();
        return Workpiece.write(new ItemStack(Items.IRON_NUGGET), state);
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void joined(ItemStack result) {
        var state = Workpiece.read(result);
        check(state.current().equals("join") && state.marks().values().get("quality") == 3, "Only primary progress may advance");
        check(state.checkpoints().equals(List.of("body", "join")), "Auxiliary history must not merge");
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void logicalItemBindingPreservesRolesIdentitiesAndCapacity(GameTestHelper helper) {
        var graph=fixture("create:mixing",false);
        var definition=graph.definition(ID); var node=definition.nodes().get("join");
        var main=piece(graph,"body"); var auxiliary=piece(graph,"insert");
        var ingredients=List.of(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_NUGGET),
            net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_NUGGET));
        java.util.function.BiPredicate<Integer,ItemStack> predicate=(role,stack)->
            dev.graphassembly.integration.WorkpieceInputs.matches(helper.getLevel(),definition,node,role,stack);
        java.util.function.IntPredicate significant=role->role==0 || node.inputs().workpieceInput(role)!=null;
        for(var stacks:List.of(List.of(auxiliary,ItemStack.EMPTY,main),List.of(main,auxiliary))) {
            var bound=dev.graphassembly.integration.ItemInputBinding.bind(ingredients,stacks,predicate,significant);
            check(bound.status()==dev.graphassembly.integration.InputAssignment.Status.UNIQUE,"Permutation must preserve logical roles");
            check(ItemStack.matches(stacks.get(bound.slots()[0]),main),"Primary role is not physical slot zero");
            check(ItemStack.matches(stacks.get(bound.slots()[1]),auxiliary),"Auxiliary role must bind its own progress");
        }
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(main,new ItemStack(Items.IRON_NUGGET)),predicate,significant).status()
            ==dev.graphassembly.integration.InputAssignment.Status.NONE,"Ordinary material cannot satisfy declared progress role");
        var undeclared=definition.nodes().get("body");
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(new ItemStack(Items.IRON_NUGGET),auxiliary),
            (role,stack)->dev.graphassembly.integration.WorkpieceInputs.matches(helper.getLevel(),definition,undeclared,role,stack),role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.NONE,"Undeclared auxiliary progress cannot be consumed");
        var ordinary=new ItemStack(Items.IRON_NUGGET);
        var named=ordinary.copy(); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("distinct"));
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(ordinary,named),(role,stack)->true,role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.AMBIGUOUS,"Different component identities are ambiguous");
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(ordinary,ordinary.copy()),(role,stack)->true,role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.UNIQUE,"Equivalent item/components are interchangeable");
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(ordinary.copyWithCount(2)),(role,stack)->true,role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.UNIQUE,"Two units in one slot satisfy two logical roles");
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(ordinary),(role,stack)->true,role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.NONE,"A physical unit cannot satisfy two roles");
        check(dev.graphassembly.integration.ItemInputBinding.bind(ingredients,List.of(new ItemStack(Items.STONE,2)),(role,stack)->true,role->true).status()
            ==dev.graphassembly.integration.InputAssignment.Status.NONE,"Impossible ingredients remain unmatched");
        helper.succeed();
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void deployerConsumesAndKeepsDeclaredWorkpieces(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            helper.setBlock(new BlockPos(1, 3, 1), AllBlocks.DEPLOYER.getDefaultState());
            var deployer = (DeployerBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 1));
            deployer.initialize();
            helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.DEPOT.getDefaultState());
            var depot = (DepotBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            depot.initialize();
            for (boolean keep : List.of(false, true)) {
                var graph = fixture("create:deploying", keep);
                manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), graph))); GraphIndex.invalidate();
                var main = piece(graph, "body"); var auxiliary = piece(graph, "insert");
                deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, auxiliary.copy());
                var recipe = deployer.getRecipe(main);
                check(recipe != null, "Deployer must accept a declared progressing auxiliary");
                var transported = new TransportedItemStack(main.copy());
                depot.getBehaviour(DepotBehaviour.TYPE).setHeldItem(transported);
                // Replacement during the animation invalidates an already found native recipe.
                deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, main.copy());
                BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, recipe.value());
                check(ItemStack.matches(main, depot.getHeldItem()), "Changed held progress must cancel without consuming primary");
                deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, auxiliary.copy());
                recipe = deployer.getRecipe(main);
                BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, recipe.value());
                joined(depot.getHeldItem());
                check(keep ? ItemStack.matches(auxiliary, deployer.getPlayer().getMainHandItem())
                    : deployer.getPlayer().getMainHandItem().isEmpty(), "Native held-item consumption policy must survive");
            }
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void kubeMultiWorkpieceExamplePreservesRolesAndChoosesPathOutputs(GameTestHelper helper) {
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            var holder=helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("example:multi_workpiece")).orElseThrow();
            var recipe=(GraphRecipe)holder.value(); var graph=recipe.definition("example:multi_workpiece");
            var join=graph.nodes().get("join");
            check(join.inputs().workpieceInput(0).currentNodes().equals(List.of("body","polish")),"Primary role must survive real KubeJS loading");
            check(join.inputs().workpieceInput(1).requirements().marks().ranges().get("quality").min()==99,"Auxiliary range must survive real KubeJS loading");
            var restored=dev.graphassembly.codec.GraphCodec.parse(graph.id(),com.google.gson.JsonParser.parseString(recipe.json().toString()).getAsJsonObject());
            check(graph.equals(restored),"New fields must survive recipe JSON round trip");
            for(boolean polished:List.of(false,true)) {
                var state=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "body", "minecraft:gold_ingot"), ()->0)).progress();
                if(polished) state=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "polish", "minecraft:iron_nugget"), ()->0)).progress();
                state=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "join", "minecraft:iron_nugget"), ()->0)).progress();
                var result=GraphPipeline.resolve(GraphPipeline.plan(graph, state, "finish", "minecraft:iron_nugget"), ()->0);
                check((result instanceof TransitionResult.Settled) && ((TransitionResult.Settled) result).outputs().getFirst().item().equals(polished ? "minecraft:diamond" : "minecraft:emerald"),
                    "Actual script must distinguish ordered paths plus inclusive mark/count ranges");
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static List<Recipe<?>> matching(BasinOperatingBlockEntity machine) throws ReflectiveOperationException {
        // Fixtures use RecipeManager.replaceRecipes rather than a datapack reload. Emulate the
        // native reload listeners so another test's cached empty mixer trie cannot leak into this one.
        var resources=machine.getLevel().getServer().getResourceManager();
        com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(resources);
        com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(resources);
        var method = BasinOperatingBlockEntity.class.getDeclaredMethod("getMatchingRecipes"); method.setAccessible(true);
        return (List<Recipe<?>>) method.invoke(machine);
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinMixingAndCompactingConsumeBothAndSettle(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            for (String type : List.of("create:mixing", "create:compacting")) {
                var graph = fixture(type, false);
                manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), graph))); GraphIndex.invalidate();
                helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.BASIN.getDefaultState());
                var basin = (BasinBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1)); basin.initialize();
                // setBlock(same basin state) preserves its BE; start each device case with drained output.
                for(int slot=0;slot<basin.getOutputInventory().getSlots();slot++) basin.getOutputInventory().setStackInSlot(slot,ItemStack.EMPTY);
                helper.setBlock(new BlockPos(1, 3, 1), type.equals("create:mixing") ? AllBlocks.MECHANICAL_MIXER.getDefaultState() : AllBlocks.MECHANICAL_PRESS.getDefaultState());
                var machine = (BasinOperatingBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 1)); machine.initialize();
                var main = piece(graph, "body"); var auxiliary = piece(graph, "insert");
                // Opposite physical order from logical roles.
                basin.inputInventory.setStackInSlot(0, auxiliary.copy()); basin.inputInventory.setStackInSlot(1, main.copy());
                var recipes = matching(machine);
                check(recipes.size() == 1, "Real basin must find a unique graph operation: " + type);
                for (int i=0;i<5;i++) check(BasinRecipe.match(basin, recipes.getFirst()), "Read-only repeat match");
                check(ItemStack.matches(main, basin.inputInventory.getStackInSlot(1)), "Match must not alter progress");
                for (int i=0;i<basin.getOutputInventory().getSlots();i++) basin.getOutputInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE,64));
                check(!BasinRecipe.apply(basin, recipes.getFirst()), "Full output must cancel before consumption");
                check(ItemStack.matches(auxiliary, basin.inputInventory.getStackInSlot(0)), "Auxiliary must remain on capacity failure");
                for (int i=0;i<basin.getOutputInventory().getSlots();i++) basin.getOutputInventory().setStackInSlot(i, ItemStack.EMPTY);
                check(BasinRecipe.apply(basin, recipes.getFirst()), "Basin native application must succeed");
                check(basin.inputInventory.getStackInSlot(0).isEmpty() && basin.inputInventory.getStackInSlot(1).isEmpty(), "Both workpieces consumed exactly once");
                var result = basin.getOutputInventory().getStackInSlot(0); joined(result);
                helper.setBlock(new BlockPos(2, 3, 1), AllBlocks.MECHANICAL_PRESS.getDefaultState());
                var finisher=(com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity)helper.getBlockEntity(new BlockPos(2,3,1)); finisher.initialize();
                var outputs=new ArrayList<ItemStack>();
                check(finisher.tryProcessOnBelt(new TransportedItemStack(result.copy()),outputs,false), "Native finishing press must execute the next node");
                check(outputs.size()==1 && outputs.getFirst().is(Items.DIAMOND) && !Workpiece.has(outputs.getFirst()), "Range conditions must settle the actual basin product");
            }
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinNativeGatesPrecedeAmbiguityAndPreserveCreateSequence(GameTestHelper helper) throws Exception {
        var level=helper.getLevel(); var manager=level.getRecipeManager(); var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var data=fixture("create:mixing",false).json();
            data.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonObject("recipe").addProperty("heat_requirement","heated");
            var graph=new GraphRecipe(data);
            var normal=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),JsonParser.parseString("""
                {"type":"create:mixing","ingredients":[{"item":"minecraft:cobblestone"}],"results":[{"id":"minecraft:stone"}]}
                """)).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph),new RecipeHolder<>(ResourceLocation.parse("test:ordinary"),normal))); GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1,1,1),AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)helper.getBlockEntity(new BlockPos(1,1,1)); basin.initialize();
            helper.setBlock(new BlockPos(1,3,1),AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var machine=(BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1,3,1)); machine.initialize();
            var main=piece(graph,"body"); var aux=piece(graph,"insert"); var other=aux.copy();
            other.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("another auxiliary"));
            basin.inputInventory.setStackInSlot(0,main); basin.inputInventory.setStackInSlot(1,aux);
            basin.inputInventory.setStackInSlot(2,other); basin.inputInventory.setStackInSlot(3,new ItemStack(Items.COBBLESTONE));
            check(matching(machine).size()==1 && matching(machine).getFirst().getResultItem(level.registryAccess()).is(Items.STONE),"Unheated graph with item-only ambiguity must not block unrelated ordinary recipe");
            check(BasinRecipe.apply(basin,normal),"Ordinary native fallback remains usable alongside protected inputs");
            for(int i=0;i<basin.inputInventory.getSlots();i++) basin.inputInventory.setStackInSlot(i,ItemStack.EMPTY);
            for(int i=0;i<basin.getOutputInventory().getSlots();i++) basin.getOutputInventory().setStackInSlot(i,ItemStack.EMPTY);
            var start=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_ingot",
                 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:mixing"}}],"edges":[],"outputs":[]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),start))); GraphIndex.invalidate();
            var sequenced=new ItemStack(Items.IRON_INGOT);
            sequenced.set(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY,
                new com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly(ResourceLocation.parse("test:create_sequence"),1,0.5f));
            basin.inputInventory.setStackInSlot(0,sequenced);
            check(matching(machine).isEmpty(),"Create sequence carrier must not enter a new graph through basin raw-material matching");
            helper.succeed();
        } finally {manager.replaceRecipes(saved); GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinAmbiguousContainerBoundIsSlotIndependentAndReturnsOrdinaryBucket(GameTestHelper helper) throws Exception {
        var level=helper.getLevel(); var manager=level.getRecipeManager(); var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var data=fixture("create:mixing",false).json();
            data.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonObject("recipe").getAsJsonArray("ingredients").set(0,
                JsonParser.parseString("[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"minecraft:water_bucket\"}]"));
            var graph=new GraphRecipe(data);
            var normal=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),JsonParser.parseString("""
                {"type":"create:mixing","ingredients":[{"item":"minecraft:cobblestone"}],"results":[{"id":"minecraft:stone"}]}
                """)).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph),new RecipeHolder<>(ResourceLocation.parse("test:ordinary"),normal))); GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1,1,1),AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)helper.getBlockEntity(new BlockPos(1,1,1)); basin.initialize();
            helper.setBlock(new BlockPos(1,3,1),AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var machine=(BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1,3,1)); machine.initialize();
            var main=piece(graph,"body"); var aux=piece(graph,"insert");
            var water=Workpiece.write(new ItemStack(Items.WATER_BUCKET),Workpiece.read(aux));
            basin.inputInventory.setStackInSlot(0,main.copy()); basin.inputInventory.setStackInSlot(3,new ItemStack(Items.COBBLESTONE));
            basin.getOutputInventory().setStackInSlot(0,new ItemStack(Items.BUCKET,16));
            for(boolean reverse:List.of(false,true)) {
                basin.inputInventory.setStackInSlot(1,reverse?water.copy():aux.copy());
                basin.inputInventory.setStackInSlot(2,reverse?aux.copy():water.copy());
                check(matching(machine).size()==1 && matching(machine).getFirst().getResultItem(level.registryAccess()).is(Items.STONE),"Ambiguous container-capacity conservative wait must not depend on slot order or suppress ordinary material");
            }
            basin.getOutputInventory().setStackInSlot(0,ItemStack.EMPTY);
            basin.inputInventory.setStackInSlot(1,water.copy()); basin.inputInventory.setStackInSlot(2,ItemStack.EMPTY);
            var recipe=matching(machine).getFirst();
            check(BasinRecipe.apply(basin,recipe),"Single feasible container workpiece must commit");
            boolean bucket=false;
            for(int i=0;i<basin.getOutputInventory().getSlots();i++) {
                var out=basin.getOutputInventory().getStackInSlot(i);
                if(out.is(Items.BUCKET)) {bucket=true;check(!Workpiece.has(out),"Container return must not inherit either graph progress");}
            }
            check(bucket,"Native basin must return an ordinary empty bucket");
            helper.succeed();
        } finally {manager.replaceRecipes(saved); GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinCachedOperationRechecksAmbiguityAndRetainedOutputs(GameTestHelper helper) throws Exception {
        var level=helper.getLevel(); var manager=level.getRecipeManager(); var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var graph=fixture("create:mixing",false);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph))); GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1,1,1),AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)helper.getBlockEntity(new BlockPos(1,1,1)); basin.initialize();
            helper.setBlock(new BlockPos(1,3,1),AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var machine=(BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1,3,1)); machine.initialize();
            var main=piece(graph,"body"); var aux=piece(graph,"insert");
            basin.inputInventory.setStackInSlot(0,main.copy()); basin.inputInventory.setStackInSlot(1,aux.copy());
            var cached=matching(machine).getFirst();
            var distinct=main.copy(); distinct.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("second primary"));
            basin.inputInventory.setStackInSlot(2,distinct);
            check(matching(machine).isEmpty(),"Two different eligible primary states must be ambiguous");
            check(!BasinRecipe.apply(basin,cached),"Cached animation must recheck global primary/node ambiguity before committing");
            basin.inputInventory.setStackInSlot(2,ItemStack.EMPTY);
            check(BasinRecipe.apply(basin,cached),"Removing ambiguity allows the original operation");
            var first=basin.getOutputInventory().getStackInSlot(0).copy(); joined(first);
            basin.inputInventory.setStackInSlot(0,main.copy()); basin.inputInventory.setStackInSlot(1,aux.copy());
            check(!BasinRecipe.apply(basin,cached),"Native one-slot-per-components rule must block repeated identical unstackable graph output");
            check(ItemStack.matches(main,basin.inputInventory.getStackInSlot(0)) && ItemStack.matches(first,basin.getOutputInventory().getStackInSlot(0)),"Retained-output capacity failure must not consume or duplicate");
            basin.getOutputInventory().setStackInSlot(0,ItemStack.EMPTY);
            check(BasinRecipe.apply(basin,cached),"After output drains, a second real batch may commit");
            joined(basin.getOutputInventory().getStackInSlot(0));
            helper.succeed();
        } finally {manager.replaceRecipes(saved); GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinRepeatedByproductsReserveNativeCapacity(GameTestHelper helper) throws Exception {
        var manager=helper.getLevel().getRecipeManager(); var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var data=fixture("create:mixing",false).json();
            data.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonObject("recipe").add("results",JsonParser.parseString("""
                [{"id":"minecraft:cobblestone","count":64},{"id":"minecraft:cobblestone","count":1}]
                """));
            var graph=new GraphRecipe(data);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph))); GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1,1,1),AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)helper.getBlockEntity(new BlockPos(1,1,1)); basin.initialize();
            helper.setBlock(new BlockPos(1,3,1),AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var machine=(BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1,3,1)); machine.initialize();
            var primary=piece(graph,"body"); var auxiliary=piece(graph,"insert");
            basin.inputInventory.setStackInSlot(0,primary.copy()); basin.inputInventory.setStackInSlot(1,auxiliary.copy());
            check(matching(machine).isEmpty(),"Native basin cannot place identical components in multiple slots; reserve all byproducts cumulatively");
            check(ItemStack.matches(primary,basin.inputInventory.getStackInSlot(0)) && ItemStack.matches(auxiliary,basin.inputInventory.getStackInSlot(1)),"Impossible batch must leave both workpieces untouched");
            helper.succeed();
        } finally {manager.replaceRecipes(saved); GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void basinHeatFluidsReloadAndNormalRecipeProtection(GameTestHelper helper) throws Exception {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var data=fixture("create:mixing",false).json();
            var join=data.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonObject("recipe");
            join.addProperty("heat_requirement","heated");
            join.getAsJsonArray("ingredients").add(JsonParser.parseString("{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}"));
            var graph=new GraphRecipe(data);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph))); GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1,1,1),AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)helper.getBlockEntity(new BlockPos(1,1,1)); basin.initialize();
            helper.setBlock(new BlockPos(1,3,1),AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var machine=(BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1,3,1)); machine.initialize();
            var main=piece(graph,"body"); var auxiliary=piece(graph,"insert");
            basin.inputInventory.setStackInSlot(0,main.copy()); basin.inputInventory.setStackInSlot(1,auxiliary.copy());
            basin.inputTank.getPrimaryHandler().setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,300));
            check(matching(machine).isEmpty(),"Missing heat must not execute a graph");
            helper.setBlock(new BlockPos(1,0,1),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(
                com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
            basin.inputTank.getPrimaryHandler().setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,200));
            check(matching(machine).isEmpty(),"Insufficient fluid must leave both workpieces untouched");
            basin.inputTank.getPrimaryHandler().setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,300));
            var recipe=matching(machine).getFirst();
            level.random.setSeed(654321); long expected=level.random.nextLong(); level.random.setSeed(654321);
            for(int i=0;i<6;i++) check(BasinRecipe.match(basin,recipe),"Repeat basin simulation");
            check(expected==level.random.nextLong(),"Graph matching must not advance world RNG");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),new GraphRecipe(data))));
            check(!BasinRecipe.apply(basin,recipe),"Same-ID recipe reload must invalidate cached graph operation");
            check(ItemStack.matches(main,basin.inputInventory.getStackInSlot(0)),"Reload rejection must preserve primary");
            recipe=matching(machine).getFirst();
            basin.inputInventory.setStackInSlot(1,main.copy());
            check(!BasinRecipe.apply(basin,recipe),"Changed auxiliary snapshot must invalidate cached operation");
            basin.inputInventory.setStackInSlot(1,auxiliary.copy());
            check(BasinRecipe.apply(basin,recipe),"Revalidated heated/fluid operation must commit");
            check(basin.inputTank.getPrimaryHandler().getFluidAmount()==50,"Exactly configured fluid consumed");
            joined(basin.getOutputInventory().getStackInSlot(0));

            var normal=net.minecraft.world.item.crafting.Recipe.CODEC.parse(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),JsonParser.parseString("""
                {"type":"create:mixing","ingredients":[{"item":"minecraft:iron_nugget"}],"results":[{"id":"minecraft:gold_ingot"}]}
                """)).getOrThrow();
            for(int i=0;i<basin.getOutputInventory().getSlots();i++) basin.getOutputInventory().setStackInSlot(i,ItemStack.EMPTY);
            basin.inputInventory.setStackInSlot(0,main.copy());
            check(!BasinRecipe.apply(basin,normal),"Ordinary basin recipe must not eat a progressing input");
            basin.inputInventory.setStackInSlot(1,new ItemStack(Items.IRON_NUGGET));
            check(BasinRecipe.apply(basin,normal),"Unrelated ordinary material must remain usable alongside protected workpiece");
            check(ItemStack.matches(main,basin.inputInventory.getStackInSlot(0)),"Native fallback must skip the protected item even when it comes first");
            helper.succeed();
        } finally {manager.replaceRecipes(saved); GraphIndex.invalidate();}
    }
}
