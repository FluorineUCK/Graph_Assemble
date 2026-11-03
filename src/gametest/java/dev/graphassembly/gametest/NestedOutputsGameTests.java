package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.BasinGraphRuntime;
import dev.graphassembly.integration.OutputEnvelope;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static dev.graphassembly.gametest.ForeignGraphGameTests.*;

/** Actual native transaction boundaries; unit tests separately cover deterministic probability paths. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NestedOutputsGameTests {
    private static GraphRecipe fixture(String type) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:copper_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"finish","start":true,"finish":true,"recipe":{"type":"%s"%s}}],"edges":[],
             "outputs":[{"results":[{"mode":"all","results":[{"item":"minecraft:diamond"},
               {"mode":"weighted","results":[{"mode":"all","results":[
                 {"item":"minecraft:emerald","count":2},{"item":"minecraft:gold_ingot","chance":1}]}]}]}]}]}
            """.formatted(type, type.equals("create:milling") || type.equals("create:cutting") ? ",\"processing_time\":10" : "")).getAsJsonObject());
    }
    private static void install(GameTestHelper helper, GraphRecipe recipe) {
        helper.getLevel().getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:nested_outputs"), recipe)));
        GraphIndex.invalidate();
        check(GraphIndex.get(helper.getLevel()).graphs().containsKey("test:nested_outputs"),
            "Graph disabled: " + GraphIndex.get(helper.getLevel()).disabledReason("test:nested_outputs"));
    }
    private static void bundle(List<ItemStack> items) {
        check(items.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 1, "Missing A");
        check(items.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 2, "Missing B");
        check(items.stream().filter(s -> s.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum() == 1, "Missing C");
        check(items.stream().noneMatch(Workpiece::has), "Settlement must remove graph progress");
    }
    private static List<ItemStack> contents(net.neoforged.neoforge.items.IItemHandler inventory) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty()) result.add(inventory.getStackInSlot(i).copy());
        return result;
    }

    @GameTest(template="test_empty", batch="nested_outputs", timeoutTicks=100)
    public static void nativePressDeliversWholeNestedBundleAndSpoutRefusesWithoutConsumption(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager(); var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            install(helper, fixture("create:pressing"));
            helper.setBlock(new BlockPos(1, 2, 1), AllBlocks.MECHANICAL_PRESS.getDefaultState());
            var press = (MechanicalPressBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 1)); press.initialize();
            var nativeRecipe = press.getRecipe(new ItemStack(Items.COPPER_INGOT)).orElseThrow().value();
            bundle(nativeRecipe.rollResults(helper.getLevel().random));
            var result = new ArrayList<ItemStack>();
            check(press.tryProcessOnBelt(new TransportedItemStack(new ItemStack(Items.COPPER_INGOT)), result, false), "Native press process");
            bundle(result);
            var empty = fixture("create:pressing").json();
            empty.getAsJsonArray("outputs").get(0).getAsJsonObject().add("results", JsonParser.parseString("[{\"mode\":\"all\",\"results\":[]}]"));
            install(helper, new GraphRecipe(empty));
            result.clear();
            check(press.tryProcessOnBelt(new TransportedItemStack(new ItemStack(Items.COPPER_INGOT)), result, false), "Empty successful settlement is still processed");
            check(result.isEmpty(), "No placeholder workpiece for empty success");
            var json = fixture("create:filling").json();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("ingredients",
                JsonParser.parseString("[{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}]"));
            install(helper, new GraphRecipe(json));
            var input = new ItemStack(Items.COPPER_INGOT);
            var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
            check(!com.simibubi.create.content.fluids.spout.FillingBySpout.canItemBeFilled(helper.getLevel(), input), "Spout cannot output this bundle");
            check(com.simibubi.create.content.fluids.spout.FillingBySpout.fillItem(helper.getLevel(), 250, input, water).isEmpty(), "No truncation");
            check(input.getCount() == 1 && water.getAmount() == 1000, "Spout must preserve inputs");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template="test_empty", batch="nested_outputs", timeoutTicks=100)
    public static void sawBulkNestedOutputsAreNeitherTruncatedNorRolledWithoutSpace(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager(); var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            install(helper, fixture("create:cutting"));
            helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(
                com.simibubi.create.content.kinetics.saw.SawBlock.FACING, net.minecraft.core.Direction.UP));
            var saw = (com.simibubi.create.content.kinetics.saw.SawBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1)); saw.initialize();
            saw.inventory.withSlotLimit(false);
            var apply = saw.getClass().getDeclaredMethod("applyRecipe"); apply.setAccessible(true);
            saw.inventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 64));
            apply.invoke(saw);
            check(saw.inventory.getStackInSlot(0).getCount() == 64, "Oversized nested batch preserves all input");
            saw.inventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
            apply.invoke(saw); bundle(contents(saw.inventory));
            check(contents(saw.inventory).stream().noneMatch(s -> s.is(Items.COPPER_INGOT)), "Fitting batch consumes input");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template="test_empty", batch="nested_outputs", timeoutTicks=100)
    public static void bothBasinDevicesReserveSimultaneousBundleBeforeConsuming(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager(); var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            for (var type : List.of("create:mixing", "create:compacting")) {
                install(helper, fixture(type));
                helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.BASIN.getDefaultState());
                var basin = (BasinBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1)); basin.initialize();
                helper.setBlock(new BlockPos(1, 3, 1), type.equals("create:mixing") ? AllBlocks.MECHANICAL_MIXER.getDefaultState() : AllBlocks.MECHANICAL_PRESS.getDefaultState());
                var machine = (BasinOperatingBlockEntity)helper.getBlockEntity(new BlockPos(1, 3, 1)); machine.initialize();
                basin.inputInventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
                var out = basin.getOutputInventory();
                for (int i = 0; i < out.getSlots(); i++) out.setStackInSlot(i, i == 0 ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE, 64));
                check(BasinGraphRuntime.lookup(machine, basin).recipes().isEmpty(), "One free slot cannot hold simultaneous A/B/C");
                check(basin.inputInventory.getStackInSlot(0).getCount() == 1, "Capacity preview consumes nothing");
                for (int i = 0; i < out.getSlots(); i++) out.setStackInSlot(i, ItemStack.EMPTY);
                var recipe = BasinGraphRuntime.lookup(machine, basin).recipes().getFirst();
                check(BasinRecipe.apply(basin, recipe), "Basin commits complete bundle " + type);
                bundle(contents(out));
                check(basin.inputInventory.getStackInSlot(0).isEmpty(), "Only one input consumed");
            }
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template="test_empty", batch="nested_outputs", timeoutTicks=100)
    public static void millstoneReservesBeforeRollingAndEnvelopeAddsSimultaneousDuplicates(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager(); var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            install(helper, fixture("create:milling"));
            helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.MILLSTONE.getDefaultState());
            var mill = (MillstoneBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1)); mill.initialize();
            mill.inputInv.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
            for (int i = 1; i < mill.outputInv.getSlots(); i++) mill.outputInv.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            var process = MillstoneBlockEntity.class.getDeclaredMethod("process"); process.setAccessible(true); process.invoke(mill);
            check(mill.inputInv.getStackInSlot(0).getCount() == 1, "Blocked mill must preserve input");
            for (int i = 0; i < mill.outputInv.getSlots(); i++) mill.outputInv.setStackInSlot(i, ItemStack.EMPTY);
            process.invoke(mill); bundle(contents(mill.outputInv));
            check(mill.inputInv.getStackInSlot(0).isEmpty(), "Mill consumes one");
            var envelope = new OutputEnvelope();
            envelope.include(List.of(new ItemStack(Items.DIAMOND, 40), new ItemStack(Items.DIAMOND, 40)));
            envelope.include(List.of(new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.EMERALD)));
            check(envelope.stacks().stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 80, "Envelope uses max of simultaneous sums, not sum of alternative pools");
            check(envelope.stacks().stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()), "Envelope splits oversized totals");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

}
