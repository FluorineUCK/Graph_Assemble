package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.filter.FilterSelection;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

/** Bundled resource + real device entry points, independent of optional JEI/KubeJS. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class FilterCraftingGameTests {
    private static final String ID = "graph_assembly:graph_filter";
    private static final String BATCH = "graph_filter_crafting";
    private static final List<net.minecraft.world.item.Item> INFESTED = List.of(
        Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE, Items.INFESTED_STONE_BRICKS,
        Items.INFESTED_MOSSY_STONE_BRICKS, Items.INFESTED_CRACKED_STONE_BRICKS,
        Items.INFESTED_CHISELED_STONE_BRICKS, Items.INFESTED_DEEPSLATE);

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static GraphRecipe bundled(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(ID)).orElseThrow().value();
        check(recipe instanceof GraphRecipe, "Bundled teaching recipe must load through the real serializer");
        return (GraphRecipe) recipe;
    }
    private static void install(GameTestHelper helper, GraphRecipe graph, RecipeHolder<?>... extra) {
        var recipes = new ArrayList<RecipeHolder<?>>();
        recipes.add(new RecipeHolder<>(ResourceLocation.parse(ID), graph));
        recipes.addAll(List.of(extra));
        helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        GraphIndex.invalidate();
        check(GraphIndex.get(helper.getLevel()).graphs().containsKey(ID), "Teaching recipe must not be disabled");
    }
    private static MechanicalPressBlockEntity press(GameTestHelper helper) {
        var pos = new BlockPos(3, 1, 1);
        helper.setBlock(pos, AllBlocks.MECHANICAL_PRESS.getDefaultState());
        var press = (MechanicalPressBlockEntity) helper.getBlockEntity(pos);
        press.initialize();
        return press;
    }
    private static ItemStack press(MechanicalPressBlockEntity press, ItemStack input, String node) {
        var transported = new TransportedItemStack(input.copy());
        var outputs = new ArrayList<ItemStack>();
        check(press.tryProcessOnBelt(transported, outputs, false), "Native press must execute " + node);
        check(outputs.size() == 1, "Press returns precisely one workpiece with no extra output");
        check(Workpiece.read(outputs.getFirst()).current().equals(node), "Native press must advance to " + node);
        return outputs.getFirst();
    }
    private static ItemStack body(GameTestHelper helper, MechanicalPressBlockEntity press) throws Exception {
        var started = press(press, AllItems.ATTRIBUTE_FILTER.asStack(), "body_start");
        check(started.is(GraphAssembly.INCOMPLETE_FILTER.get()), "Body entrance must replace Attribute Filter with exclusive carrier");
        var pos = new BlockPos(4, 1, 1);
        helper.setBlock(pos, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(SawBlock.FACING, Direction.UP));
        var saw = (SawBlockEntity) helper.getBlockEntity(pos);
        saw.initialize();
        saw.inventory.clear();
        saw.inventory.setStackInSlot(0, started.copy());
        // Native applyRecipe clears the inventory: retained output must block it before consumption.
        saw.inventory.setStackInSlot(1, new ItemStack(Items.COBBLESTONE));
        applySaw(saw);
        check(ItemStack.matches(started, saw.inventory.getStackInSlot(0)), "Saw capacity failure must preserve body");
        check(saw.inventory.getStackInSlot(1).is(Items.COBBLESTONE), "Saw cannot erase retained output");
        saw.inventory.setStackInSlot(1, ItemStack.EMPTY);
        applySaw(saw);
        ItemStack ready = ItemStack.EMPTY;
        int count = 0;
        for (int slot = 0; slot < saw.inventory.getSlots(); slot++) {
            var out = saw.inventory.getStackInSlot(slot);
            if (!out.isEmpty()) { ready = out.copy(); count++; }
        }
        check(count == 1 && ready.is(GraphAssembly.INCOMPLETE_FILTER.get()), "Native saw must return one body only");
        check(Workpiece.read(ready).checkpoints().equals(List.of("body_start", "body_ready")), "Saw records readiness checkpoint");
        saw.inventory.clear();
        return ready;
    }
    private static BasinBlockEntity basin(GameTestHelper helper, boolean mixer) {
        helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.BASIN.getDefaultState());
        var basin = (BasinBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        basin.initialize();
        clear(basin);
        helper.setBlock(new BlockPos(1, 3, 1), mixer ? AllBlocks.MECHANICAL_MIXER.getDefaultState() : AllBlocks.MECHANICAL_PRESS.getDefaultState());
        ((BasinOperatingBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 1))).initialize();
        return basin;
    }
    private static void clear(BasinBlockEntity basin) {
        for (int i = 0; i < basin.inputInventory.getSlots(); i++) basin.inputInventory.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, ItemStack.EMPTY);
    }
    @SuppressWarnings("unchecked")
    private static List<Recipe<?>> matching(GameTestHelper helper) throws Exception {
        var resources = helper.getLevel().getServer().getResourceManager();
        com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(resources);
        com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(resources);
        var method = BasinOperatingBlockEntity.class.getDeclaredMethod("getMatchingRecipes");
        method.setAccessible(true);
        return (List<Recipe<?>>) method.invoke(helper.getBlockEntity(new BlockPos(1, 3, 1)));
    }
    private static void applySaw(SawBlockEntity saw) throws Exception {
        var method = SawBlockEntity.class.getDeclaredMethod("applyRecipe");
        method.setAccessible(true);
        method.invoke(saw);
    }
    private static ItemStack output(BasinBlockEntity basin) {
        ItemStack result = ItemStack.EMPTY;
        int count = 0;
        for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) {
            var out = basin.getOutputInventory().getStackInSlot(i);
            if (!out.isEmpty()) { result = out.copy(); count++; }
        }
        check(count == 1 && result.getCount() == 1, "Teaching operation produces exactly one item, no scrap/byproduct");
        return result;
    }
    private static ItemStack insert(GameTestHelper helper) throws Exception {
        var basin = basin(helper, true);
        basin.inputInventory.setStackInSlot(0, new ItemStack(Items.INFESTED_DEEPSLATE));
        basin.inputInventory.setStackInSlot(2, AllItems.ANDESITE_ALLOY.asStack());
        basin.inputInventory.setStackInSlot(4, new ItemStack(Items.INFESTED_COBBLESTONE));
        var recipes = matching(helper);
        var operation = dev.graphassembly.integration.GraphOperation.get(recipes.getFirst());
        check(operation != null && operation.primary().is(AllItems.ANDESITE_ALLOY.get()), "Alloy, not either infested auxiliary, must be the mixer primary");
        check(operation.node().id().equals("insert_start") && operation.node().workpieceInputs().isEmpty(), "Raw auxiliaries use ordinary Ingredient binding, not declared progress roles");
        check(recipes.size() == 1 && BasinRecipe.apply(basin, recipes.getFirst()), "Real mixer binds alloy plus two mixed infested inputs");
        var result = output(basin);
        check(result.is(GraphAssembly.INFESTED_INSERT.get()) && Workpiece.read(result).current().equals("insert_start"), "Mixer creates exclusive insert carrier");
        for (int i = 0; i < basin.inputInventory.getSlots(); i++) check(basin.inputInventory.getStackInSlot(i).isEmpty(), "Mixer consumes all three ingredients exactly once");
        return result;
    }
    private static void finalOutput(BasinBlockEntity basin) {
        var result = output(basin);
        check(result.is(GraphAssembly.FILTER.get()) && !Workpiece.has(result), "Final output is one settled Graph Assembly Filter");
        check(FilterSelection.parse(result.get(GraphAssembly.FILTER_SELECTION.get())).equals(FilterSelection.EMPTY), "Final filter must be unconfigured");
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void previewsUseRawEntrancesAndBranchLocalCarriers(GameTestHelper helper) {
        var graph = bundled(helper).definition(ID);
        for (var row : List.of(
            List.of("body_start", "create:attribute_filter", "graph_assembly:incomplete_graph_filter"),
            List.of("body_ready", "graph_assembly:incomplete_graph_filter", "graph_assembly:incomplete_graph_filter"),
            List.of("insert_start", "create:andesite_alloy", "graph_assembly:infested_filter_insert"),
            List.of("insert_ready", "graph_assembly:infested_filter_insert", "graph_assembly:infested_filter_insert"),
            List.of("assemble", "graph_assembly:incomplete_graph_filter", "graph_assembly:incomplete_graph_filter"))) {
            var preview = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>)
                dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(), graph, graph.nodes().get(row.get(0))).value();
            var expectedInput = dev.graphassembly.integration.NativeSteps.stack(row.get(1));
            var expectedOutput = dev.graphassembly.integration.NativeSteps.stack(row.get(2));
            check(preview.getIngredients().getFirst().test(expectedInput), row.get(0) + " preview uses wrong primary input: " + row.get(1));
            check(ItemStack.isSameItem(preview.getRollableResults().getFirst().getStack(), expectedOutput), row.get(0) + " preview changes branch carrier");
            if (graph.nodes().get(row.get(0)).start()) {
                check(!preview.getIngredients().getFirst().test(expectedOutput), "Start must show raw ingredient, not require its own output");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void untaggedCarrierItemCanInitializeOnlyWhenDeclaredAsRawIngredient(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var json = bundled(helper).json();
            json.add("ingredient", JsonParser.parseString("{\"item\":\"graph_assembly:incomplete_graph_filter\"}"));
            json.getAsJsonArray("nodes").get(2).getAsJsonObject().add("ingredient",
                JsonParser.parseString("{\"item\":\"graph_assembly:infested_filter_insert\"}"));
            install(helper, new GraphRecipe(json));
            var raw = new ItemStack(GraphAssembly.INCOMPLETE_FILTER.get());
            check(!Workpiece.has(raw), "Bare carrier item starts without progress");
            var press = press(helper);
            var started = press(press, raw, "body_start");
            check(started.is(GraphAssembly.INCOMPLETE_FILTER.get()) && Workpiece.has(started), "Same-ID raw input must initialize rather than be rejected as already in progress");
            check(!press.tryProcessOnBelt(new TransportedItemStack(started.copy()), new ArrayList<>(), false), "A progressed item must follow outgoing edges, not restart by raw item ID");
            var basin = basin(helper, true);
            basin.inputInventory.setStackInSlot(0, new ItemStack(GraphAssembly.INFESTED_INSERT.get()));
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.INFESTED_STONE, 2));
            var recipes = matching(helper);
            check(recipes.size() == 1 && BasinRecipe.apply(basin, recipes.getFirst()), "Declared bare insert plus raw auxiliaries can initialize its own branch");
            var insert = output(basin);
            check(insert.is(GraphAssembly.INFESTED_INSERT.get()) && Workpiece.read(insert).entry().equals("insert_start"), "Insert entrance initializes progress without converting to filter");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeFiveOperationChainAndFullOutput(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            install(helper, bundled(helper));
            var press = press(helper);
            var body = body(helper, press);
            var insert = press(press, insert(helper), "insert_ready");
            check(Workpiece.read(insert).checkpoints().equals(List.of("insert_start", "insert_ready")), "Auxiliary readiness history remains independent");
            check(dev.graphassembly.integration.GraphRuntime.diagnostic(helper.getLevel(), insert) == null,
                "A ready auxiliary joining the body must not be reported as a blocked primary");
            var basin = basin(helper, false);
            // Physical order deliberately opposes logical main/side roles.
            basin.inputInventory.setStackInSlot(0, insert.copy());
            basin.inputInventory.setStackInSlot(3, body.copy());
            var recipes = matching(helper);
            check(recipes.size() == 1, "Final native press must find exactly one compacting operation");
            var recipe = recipes.getFirst();
            for (int i = 0; i < 4; i++) check(BasinRecipe.match(basin, recipe), "Repeated native simulation must remain read-only");
            for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            check(!BasinRecipe.apply(basin, recipe), "Full output must cancel final settlement");
            check(ItemStack.matches(insert, basin.inputInventory.getStackInSlot(0)) && ItemStack.matches(body, basin.inputInventory.getStackInSlot(3)), "Full-output failure cannot consume either ready workpiece");
            for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, ItemStack.EMPTY);
            check(BasinRecipe.apply(basin, recipe), "Draining output allows native final join");
            check(basin.inputInventory.getStackInSlot(0).isEmpty() && basin.inputInventory.getStackInSlot(3).isEmpty(), "Final join consumes exactly two ready pieces");
            finalOutput(basin);
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void mixerAcceptsSevenVariantsAndRejectsMissingCosts(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            install(helper, bundled(helper));
            var tag = TagKey.create(Registries.ITEM, GraphAssembly.id("filter_infested_blocks"));
            for (var item : INFESTED) check(new ItemStack(item).is(tag), "Bundled item tag must contain all seven variants");
            check(!new ItemStack(Items.STONE).is(tag), "Ordinary stone is not infested");
            var basin = basin(helper, true);
            for (var a : INFESTED) for (var b : INFESTED) for (boolean reverse : List.of(false, true)) {
                clear(basin);
                basin.inputInventory.setStackInSlot(reverse ? 4 : 0, AllItems.ANDESITE_ALLOY.asStack());
                basin.inputInventory.setStackInSlot(2, new ItemStack(a));
                basin.inputInventory.setStackInSlot(reverse ? 0 : 4, new ItemStack(b));
                var found = matching(helper);
                check(found.size() == 1 && dev.graphassembly.integration.GraphOperation.get(found.getFirst()).primary().is(AllItems.ANDESITE_ALLOY.get()), "Slot order cannot change logical alloy-primary identity");
                check(found.size() == 1 && BasinRecipe.apply(basin, found.getFirst()), "Every mixed-variant pair and slot order must enter mixer");
                check(output(basin).is(GraphAssembly.INFESTED_INSERT.get()), "Mixed-variant pair yields processed insert");
            }
            clear(basin);
            basin.inputInventory.setStackInSlot(0, AllItems.ANDESITE_ALLOY.asStack());
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.INFESTED_STONE));
            check(matching(helper).isEmpty(), "One infested block is insufficient");
            check(basin.inputInventory.getStackInSlot(0).getCount() == 1 && basin.inputInventory.getStackInSlot(1).getCount() == 1, "Missing-cost lookup cannot consume");
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.INFESTED_STONE, 2));
            var cached = matching(helper).getFirst();
            basin.inputInventory.setStackInSlot(0, ItemStack.EMPTY);
            check(matching(helper).isEmpty() && !BasinRecipe.apply(basin, cached), "Missing alloy invalidates a cached mixer operation");
            check(basin.inputInventory.getStackInSlot(1).getCount() == 2, "Missing alloy cannot consume infested blocks");
            basin.inputInventory.setStackInSlot(0, AllItems.ANDESITE_ALLOY.asStack());
            for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            check(!BasinRecipe.apply(basin, cached), "Full mixer output must cancel entry");
            check(basin.inputInventory.getStackInSlot(0).getCount() == 1 && basin.inputInventory.getStackInSlot(1).getCount() == 2, "Capacity failure preserves all three raw ingredients");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void finalJoinRejectsRawWrongStaleAndUnreadyRoles(GameTestHelper helper) throws Exception {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var graph = bundled(helper);
            install(helper, graph);
            var press = press(helper);
            var body = body(helper, press);
            var unreadyBody = press(press, AllItems.ATTRIBUTE_FILTER.asStack(), "body_start");
            var unreadyInsert = insert(helper);
            var insert = press(press, unreadyInsert, "insert_ready");
            var basin = basin(helper, false);
            var wrongGraph = Workpiece.read(insert);
            var stale = Workpiece.write(insert.copy(), new Progress("test:other_graph", wrongGraph.entry(), wrongGraph.current(), wrongGraph.marks(), wrongGraph.checkpoints(), wrongGraph.rewardCounts(), null, wrongGraph.history()));
            var wrongHistory = Workpiece.write(insert.copy(), new Progress(ID, "insert_start", "insert_ready", wrongGraph.marks(), List.of("insert_ready", "insert_start"), wrongGraph.rewardCounts(), null, wrongGraph.history()));
            var wrongRole = Workpiece.write(insert.copy(), Workpiece.read(body));
            var cases = List.of(
                List.of(body, ItemStack.EMPTY), List.of(body, new ItemStack(Items.INFESTED_STONE, 2)),
                List.of(body, AllItems.ANDESITE_ALLOY.asStack()), List.of(body, new ItemStack(GraphAssembly.INFESTED_INSERT.get())),
                List.of(new ItemStack(GraphAssembly.INCOMPLETE_FILTER.get()), insert), List.of(unreadyBody, insert),
                List.of(body, unreadyInsert), List.of(body, stale), List.of(body, wrongHistory), List.of(body, wrongRole), List.of(insert, insert));
            for (var pair : cases) {
                clear(basin);
                basin.inputInventory.setStackInSlot(0, body.copy()); basin.inputInventory.setStackInSlot(1, insert.copy());
                var cached = matching(helper).getFirst();
                basin.inputInventory.setStackInSlot(0, pair.get(0).copy()); basin.inputInventory.setStackInSlot(1, pair.get(1).copy());
                check(matching(helper).isEmpty() && !BasinRecipe.apply(basin, cached), "Raw, wrong, stale or unfinished role must not join");
                check(ItemStack.matches(pair.get(0), basin.inputInventory.getStackInSlot(0)) && ItemStack.matches(pair.get(1), basin.inputInventory.getStackInSlot(1)), "Rejected pair must remain byte-for-byte unchanged");
            }
            clear(basin);
            basin.inputInventory.setStackInSlot(0, body.copy()); basin.inputInventory.setStackInSlot(1, insert.copy());
            var cached = matching(helper).getFirst();
            install(helper, new GraphRecipe(graph.json()));
            check(!BasinRecipe.apply(basin, cached), "Same-ID recipe reload invalidates cached final operation");
            check(ItemStack.matches(body, basin.inputInventory.getStackInSlot(0)) && ItemStack.matches(insert, basin.inputInventory.getStackInSlot(1)), "Stale operation does not consume either piece");
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void ordinaryInfestedCompactingAndCrushingRemainUsable(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            // Exact recipes audited in create-enchantment-industry-2.5.3b.jar; no upstream removals.
            var compact = decode(helper, """
                {"type":"create:compacting","ingredients":[{"item":"minecraft:infested_stone"},{"item":"minecraft:infested_stone"},{"item":"minecraft:infested_stone"},{"item":"minecraft:infested_stone"}],
                 "results":[{"id":"minecraft:stone_bricks"},{"id":"create:experience_nugget"}]}
                """);
            var crush = (CrushingRecipe) decode(helper, """
                {"type":"create:crushing","ingredients":[{"item":"minecraft:infested_cobblestone"}],
                 "results":[{"id":"minecraft:gravel"},{"id":"create:experience_nugget","chance":0.5}]}
                """);
            install(helper, bundled(helper), new RecipeHolder<>(ResourceLocation.parse("create_enchantment_industry:compacting/infested_stone"), compact),
                new RecipeHolder<>(ResourceLocation.parse("create_enchantment_industry:crushing/infested_cobblestone"), crush));
            var basin = basin(helper, false);
            basin.inputInventory.setStackInSlot(0, new ItemStack(Items.INFESTED_STONE, 4));
            var found = matching(helper);
            check(found.size() == 1 && found.getFirst() == compact && BasinRecipe.apply(basin, compact), "Ordinary four-infested compacting remains selected and executes");
            check(basin.inputInventory.getStackInSlot(0).isEmpty(), "Ordinary compacting consumes all four infested stone");
            int bricks = 0, xp = 0;
            for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) {
                var out = basin.getOutputInventory().getStackInSlot(i);
                check(!Workpiece.has(out), "Ordinary output never inherits graph progress");
                if (out.is(Items.STONE_BRICKS)) bricks += out.getCount();
                if (out.is(AllItems.EXP_NUGGET.get())) xp += out.getCount();
            }
            check(bricks == 1 && xp == 1, "Original compacting output is preserved");
            var raw = new ItemStack(Items.INFESTED_COBBLESTONE);
            check(crush.matches(new SingleRecipeInput(raw), level), "Original infested-cobble crushing Ingredient remains usable");
            var nativeRecipe = manager.getRecipeFor(AllRecipeTypes.CRUSHING.getType(), new SingleRecipeInput(raw), level);
            check(nativeRecipe.isPresent() && nativeRecipe.get().id().equals(ResourceLocation.parse("create_enchantment_industry:crushing/infested_cobblestone")), "Native crushing manager still finds original fixture");
            for (var item : INFESTED) {
                var lookup = dev.graphassembly.integration.GraphRuntime.lookup(level, new ItemStack(item), AllRecipeTypes.PRESSING.getType(), com.simibubi.create.content.kinetics.press.PressingRecipe.class, r -> true);
                check(!lookup.handled(), "Bare infested blocks must never become a single-input graph entrance");
            }
            helper.succeed();
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
    }
    private static Recipe<?> decode(GameTestHelper helper, String json) {
        return Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
    }
}
