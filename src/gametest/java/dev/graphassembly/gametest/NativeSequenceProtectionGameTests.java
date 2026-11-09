package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSequenceProtectionGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("test:native_sequence_guard");
    private static final String MILL = """
        {"type":"create:milling","ingredients":[{"item":"minecraft:iron_nugget"}],"results":[{"id":"minecraft:redstone"}],"processing_time":20}
        """;
    private static final String DEPLOY = """
        {"type":"create:deploying","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:iron_nugget"}],"results":[{"id":"minecraft:redstone"}]}
        """;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Session implements AutoCloseable {
        final GameTestHelper h;
        final List<RecipeHolder<?>> saved;
        final BlockPos furnacePos, millPos, depotPos, deployerPos;
        Session(GameTestHelper h) {
            this.h = h;
            saved = List.copyOf(h.getLevel().getRecipeManager().getRecipes());
            furnacePos = h.absolutePos(new BlockPos(1,1,1));
            millPos = h.absolutePos(new BlockPos(3,1,1));
            depotPos = h.absolutePos(new BlockPos(2,1,1));
            deployerPos = depotPos.above(2);
        }
        RecipeHolder<?> decode(ResourceLocation id, String json) {
            return new RecipeHolder<>(id, Recipe.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow());
        }
        void install(RecipeHolder<?>... recipes) { h.getLevel().getRecipeManager().replaceRecipes(List.of(recipes)); GraphIndex.invalidate(); }
        ItemStack carrier() {
            install(decode(ID, NativeSequenceGameTests.FURNACE_SEQUENCE));
            var level = h.getLevel();
            level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
            var furnace = (FurnaceBlockEntity) level.getBlockEntity(furnacePos);
            furnace.setItem(0, new ItemStack(Items.IRON_INGOT));
            furnace.setItem(1, new ItemStack(Items.COAL));
            for (int i = 0; i < 4; i++) AbstractFurnaceBlockEntity.serverTick(level, furnacePos, level.getBlockState(furnacePos), furnace);
            var carrier = furnace.getItem(2).copy();
            check(carrier.is(Items.IRON_NUGGET) && carrier.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 1, "Real furnace produces a native step-1 carrier waiting for upstream press");
            return carrier;
        }
        DeployerBlockEntity deployer() {
            var level = h.getLevel();
            level.setBlockAndUpdate(depotPos, AllBlocks.DEPOT.getDefaultState());
            ((DepotBlockEntity) level.getBlockEntity(depotPos)).initialize();
            level.setBlockAndUpdate(deployerPos, AllBlocks.DEPLOYER.getDefaultState());
            var deployer = (DeployerBlockEntity) level.getBlockEntity(deployerPos);
            deployer.initialize();
            return deployer;
        }
        @Override public void close() {
            for (var pos : List.of(furnacePos, millPos, depotPos, deployerPos)) h.getLevel().removeBlock(pos, false);
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(depotPos).inflate(3)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
            h.getLevel().getRecipeManager().replaceRecipes(saved);
            GraphIndex.invalidate();
        }
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void wrongHostAndMissingOrDisabledParentNeverMillMixedCarrier(GameTestHelper h) throws Exception {
        try (var s = new Session(h)) {
            var carrier = s.carrier();
            var level = h.getLevel();
            var ordinary = s.decode(ResourceLocation.parse("test:ordinary_carrier_milling"), MILL);
            level.setBlockAndUpdate(s.millPos, AllBlocks.MILLSTONE.getDefaultState());
            var mill = (MillstoneBlockEntity) level.getBlockEntity(s.millPos);
            mill.initialize();
            var process = MillstoneBlockEntity.class.getDeclaredMethod("process");
            process.setAccessible(true);
            for (String parent : List.of("valid", "missing", "disabled")) {
                var recipes = new ArrayList<RecipeHolder<?>>();
                recipes.add(ordinary);
                if (!parent.equals("missing")) recipes.add(s.decode(ID, parent.equals("valid") ? NativeSequenceGameTests.FURNACE_SEQUENCE
                    : NativeSequenceGameTests.FURNACE_SEQUENCE.replace("\"cookingtime\":2", "\"cookingtime\":0")));
                s.install(recipes.toArray(RecipeHolder<?>[]::new));
                mill.inputInv.setStackInSlot(0, carrier.copy());
                process.invoke(mill);
                check(ItemStack.matches(carrier, mill.inputInv.getStackInSlot(0)), "Wrong millstone must not consume carrier while parent is " + parent);
                for (int i = 0; i < mill.outputInv.getSlots(); i++) check(mill.outputInv.getStackInSlot(i).isEmpty(), "No unrelated ordinary milling reward while parent is " + parent);
                if (parent.equals("valid")) {
                    var lookup = dev.graphassembly.integration.GraphRuntime.lookup(level, carrier, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, r -> true);
                    check(!lookup.handled(), "Correct expected native press delegates through the shared routing gate to Create");
                    var press = SequencedAssemblyRecipe.getRecipe(level, carrier, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class).orElseThrow();
                    var advanced = press.value().rollResults(level.random).getFirst();
                    check(advanced.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 2, "Actual expected upstream press remains executable after wrong-host rejection");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void ordinaryDeployerLookupRejectsNativeCarrierAuxiliary(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = s.carrier();
            s.install(s.decode(ID, NativeSequenceGameTests.FURNACE_SEQUENCE), s.decode(ResourceLocation.parse("test:ordinary_deploy_guard"), DEPLOY));
            var deployer = s.deployer();
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
            check(deployer.getRecipe(new ItemStack(Items.IRON_INGOT)) == null, "Ordinary raw-primary lookup must not borrow native sequence auxiliary");
            check(ItemStack.matches(deployer.getPlayer().getMainHandItem(), carrier), "Failed lookup preserves native auxiliary");
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void ordinaryDeployerActivationRejectsReplacedNativeCarrierAuxiliary(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = s.carrier();
            s.install(s.decode(ID, NativeSequenceGameTests.FURNACE_SEQUENCE), s.decode(ResourceLocation.parse("test:ordinary_deploy_guard"), DEPLOY));
            var deployer = s.deployer();
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET));
            var primary = new ItemStack(Items.IRON_INGOT);
            var selected = deployer.getRecipe(primary);
            check(selected != null, "Ordinary deployer control with raw auxiliary remains executable");
            var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(s.depotPos);
            var transported = new TransportedItemStack(primary.copy());
            depot.getBehaviour(DepotBehaviour.TYPE).setHeldItem(transported);
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
            BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, selected.value());
            check(ItemStack.matches(primary, depot.getHeldItem()) && ItemStack.matches(carrier, deployer.getPlayer().getMainHandItem()), "Activation must reject replaced native auxiliary without payment or unrelated result");
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void ordinaryNativeDeployingPrimaryStillActivates(GameTestHelper h) {
        try (var s = new Session(h)) {
            var ordinary = """
                {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":{"id":"minecraft:iron_nugget"},"loops":2,
                 "sequence":[{"type":"create:deploying","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:flint"}],"results":[{"id":"minecraft:iron_nugget"}]}],"results":[{"id":"minecraft:diamond"}]}
                """;
            s.install(s.decode(ID, ordinary));
            var deployer = s.deployer();
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT, 2));
            var carrier = new ItemStack(Items.IRON_NUGGET);
            carrier.set(AllDataComponents.SEQUENCED_ASSEMBLY, new SequencedAssemblyRecipe.SequencedAssembly(ID, 1, .5f));
            var recipe = deployer.getRecipe(carrier);
            check(recipe != null && recipe.value() instanceof DeployerApplicationRecipe, "Genuine ordinary native deploying primary is selected upstream");
            var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(s.depotPos);
            var transported = new TransportedItemStack(carrier.copy());
            depot.getBehaviour(DepotBehaviour.TYPE).setHeldItem(transported);
            BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, recipe.value());
            check(depot.getHeldItem().is(Items.DIAMOND) && deployer.getPlayer().getMainHandItem().getCount() == 1, "Valid upstream native deploying primary still pays and settles");
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void ordinaryDeployerActivationRejectsReplacedMixedPrimary(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = s.carrier();
            // Same item as the current carrier: the already selected ordinary recipe
            // remains ingredient-compatible, but cannot authorize a different parent stage.
            var ordinary = DEPLOY.replace("minecraft:iron_ingot", "minecraft:iron_nugget");
            s.install(s.decode(ID, NativeSequenceGameTests.FURNACE_SEQUENCE), s.decode(ResourceLocation.parse("test:ordinary_primary_deploy_guard"), ordinary));
            var deployer = s.deployer();
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 2));
            var selected = deployer.getRecipe(new ItemStack(Items.IRON_NUGGET));
            check(selected != null, "Raw-primary control selects an ordinary ingredient-compatible deploying recipe");
            var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(s.depotPos);
            var transported = new TransportedItemStack(carrier.copy());
            depot.getBehaviour(DepotBehaviour.TYPE).setHeldItem(transported);
            BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, selected.value());
            check(ItemStack.matches(carrier, depot.getHeldItem()) && deployer.getPlayer().getMainHandItem().getCount() == 2,
                "Already selected ordinary recipe must not consume mixed primary waiting for native press");
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void ordinaryDeployerRecipeCannotImpersonateExpectedMixedDeployingStage(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = s.carrier();
            var mixed = JsonParser.parseString(NativeSequenceGameTests.FURNACE_SEQUENCE).getAsJsonObject();
            mixed.getAsJsonArray("sequence").set(1, JsonParser.parseString("""
                {"type":"create:deploying","ingredients":[{"item":"minecraft:iron_nugget"},{"item":"minecraft:iron_nugget"}],"results":[{"id":"minecraft:iron_nugget"}]}
                """));
            var ordinary = DEPLOY.replace("minecraft:iron_ingot", "minecraft:iron_nugget");
            s.install(s.decode(ID, mixed.toString()), s.decode(ResourceLocation.parse("test:ordinary_impersonated_deploy_guard"), ordinary));
            var deployer = s.deployer();
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 2));
            var selected = deployer.getRecipe(new ItemStack(Items.IRON_NUGGET));
            check(selected != null, "Raw control finds ordinary deploying of the same type/class as the mixed parent's expected stage");
            var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(s.depotPos);
            var transported = new TransportedItemStack(carrier.copy());
            depot.getBehaviour(DepotBehaviour.TYPE).setHeldItem(transported);
            BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, selected.value());
            check(ItemStack.matches(carrier, depot.getHeldItem()) && deployer.getPlayer().getMainHandItem().getCount() == 2,
                "Same recipe type/class does not authorize an ordinary recipe object to impersonate the parent's expected native step");
            var actual = deployer.getRecipe(carrier);
            check(actual != null, "Actual expected mixed deploying stage remains available");
            BeltDeployerCallbacks.activate(transported, depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE), deployer, actual.value());
            check(depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 2 && deployer.getPlayer().getMainHandItem().getCount() == 1,
                "Exact expected mixed native recipe identity may activate and pay once");
        }
        h.succeed();
    }

    private static ItemStack fillingCarrier(Session s, String ordinaryFluid, int ordinaryAmount) {
        var carrier = s.carrier();
        var mixed = JsonParser.parseString(NativeSequenceGameTests.FURNACE_SEQUENCE).getAsJsonObject();
        mixed.getAsJsonArray("sequence").set(1, JsonParser.parseString("""
            {"type":"create:filling","ingredients":[{"item":"minecraft:iron_nugget"},{"type":"neoforge:single","fluid":"minecraft:water","amount":250}],"results":[{"id":"minecraft:iron_nugget"}]}
            """));
        var ordinary = """
            {"type":"create:filling","ingredients":[{"item":"minecraft:iron_nugget"},{"type":"neoforge:single","fluid":"minecraft:%s","amount":%d}],"results":[{"id":"minecraft:redstone"}]}
            """.formatted(ordinaryFluid, ordinaryAmount);
        s.install(s.decode(ID, mixed.toString()), s.decode(ResourceLocation.parse("test:ordinary_filling_guard"), ordinary));
        return carrier;
    }

    private static void correctWaterStillFills(Session s, ItemStack carrier) {
        var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        int amount = com.simibubi.create.content.fluids.spout.FillingBySpout.getRequiredAmountForItem(s.h.getLevel(), carrier, water);
        check(amount == 250, "Correct water must resolve the expected mixed parent's native filling amount");
        var input = carrier.copy();
        var output = com.simibubi.create.content.fluids.spout.FillingBySpout.fillItem(s.h.getLevel(), amount, input, water);
        check(output.is(Items.IRON_NUGGET) && output.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 2
            && input.isEmpty() && water.getAmount() == 750, "Correct native filling advances only parent Create progress and pays 250 water once");
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void mixedSpoutWrongFluidQueryCannotChooseOrdinaryFilling(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = fillingCarrier(s, "lava", 250);
            var lava = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
            check(com.simibubi.create.content.fluids.spout.FillingBySpout.getRequiredAmountForItem(h.getLevel(), carrier, lava) == -1,
                "Wrong-fluid query must not select unrelated ordinary lava filling for mixed water stage");
            check(lava.getAmount() == 1000, "Query does not consume fluid");
            correctWaterStillFills(s, carrier);
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void mixedSpoutWrongFluidCommitCannotConsumeCarrier(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = fillingCarrier(s, "lava", 250);
            var input = carrier.copy();
            var lava = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
            var result = com.simibubi.create.content.fluids.spout.FillingBySpout.fillItem(h.getLevel(), 250, input, lava);
            check(result.isEmpty() && ItemStack.matches(carrier, input) && lava.getAmount() == 1000,
                "Wrong-fluid actual filling must preserve carrier/fluid and never emit ordinary redstone");
            correctWaterStillFills(s, carrier);
        }
        h.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_protection")
    public static void mixedSpoutStaleAmountCannotFallbackToOrdinarySameFluidRecipe(GameTestHelper h) {
        try (var s = new Session(h)) {
            var carrier = fillingCarrier(s, "water", 100);
            var input = carrier.copy();
            var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
            check(com.simibubi.create.content.fluids.spout.FillingBySpout.getRequiredAmountForItem(h.getLevel(), input, water) == 250,
                "Native expected amount remains 250 despite competing ordinary 100-water recipe");
            var result = com.simibubi.create.content.fluids.spout.FillingBySpout.fillItem(h.getLevel(), 100, input, water);
            check(result.isEmpty() && ItemStack.matches(carrier, input) && water.getAmount() == 1000,
                "Stale commit amount must not reopen ordinary same-fluid fallback or consume carrier");
            correctWaterStillFills(s, carrier);
        }
        h.succeed();
    }
}
