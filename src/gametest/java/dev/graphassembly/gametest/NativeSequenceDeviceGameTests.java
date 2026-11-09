package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executes real native hosts; the outer Create recipe alone owns progress and settlement. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSequenceDeviceGameTests {
    private static String sequence(String ingredient, String transit, String recipe, int loops) {
        return """
            {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:%s"},
             "transitional_item":{"id":"minecraft:%s"},"loops":%d,
             "sequence":[{"type":"graph_assembly:sequence_step","step":{"recipe":%s}}],
             "results":[{"id":"minecraft:diamond"}]}
            """.formatted(ingredient, transit, loops, recipe);
    }

    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;
        final List<RecipeHolder<?>> saved;
        final BlockPos pos;
        final List<BlockPos> blocks = new ArrayList<>();
        final FakePlayer player;
        Session(GameTestHelper helper) {
            this.helper = helper;
            saved = List.copyOf(helper.getLevel().getRecipeManager().getRecipes());
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "native-sequence-device"));
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + 2);
            player.getInventory().clearContent();
        }
        void install(String json) {
            var level = helper.getLevel();
            var recipe = Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:native_sequence_device"), recipe)));
            GraphIndex.invalidate();
        }
        void block(BlockPos target, net.minecraft.world.level.block.state.BlockState state) {
            blocks.add(target);
            helper.getLevel().setBlockAndUpdate(target, state);
        }
        List<ItemEntity> drops() { return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)); }
        @Override public void close() {
            player.containerMenu = player.inventoryMenu;
            player.getInventory().clearContent();
            for (var target : blocks) helper.getLevel().removeBlock(target, false);
            drops().forEach(ItemEntity::discard);
            helper.getLevel().getRecipeManager().replaceRecipes(saved);
            GraphIndex.invalidate();
        }
    }

    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static void finished(ItemStack stack) {
        check(stack.is(Items.DIAMOND) && stack.getCount() == 1, "Parent Create sequence must deliver exactly one final diamond: " + stack);
        check(!stack.has(AllDataComponents.SEQUENCED_ASSEMBLY) && !Workpiece.has(stack), "Final result must carry neither Create nor graph progress");
    }
    private static int count(ChestBlockEntity chest, Item item) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) total += chest.getItem(i).getCount();
        return total;
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void realMillstoneExecutesTwoNativeLoopsWithoutTemplateReward(GameTestHelper helper) throws Exception {
        try (var s = new Session(helper)) {
            s.install(sequence("iron_ingot", "iron_nugget", "{\"type\":\"create:milling\",\"processing_time\":20}", 2));
            s.block(s.pos, AllBlocks.MILLSTONE.getDefaultState());
            var mill = (MillstoneBlockEntity) helper.getLevel().getBlockEntity(s.pos);
            mill.initialize();
            mill.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
            // Existing GraphGameTests exercises this same native private processing boundary.
            var process = MillstoneBlockEntity.class.getDeclaredMethod("process");
            process.setAccessible(true);
            process.invoke(mill);
            var carrier = mill.outputInv.extractItem(0, 64, false);
            check(carrier.is(Items.IRON_NUGGET) && carrier.getCount() == 1, "First milling round emits exactly one native carrier");
            var progress = carrier.get(AllDataComponents.SEQUENCED_ASSEMBLY);
            check(progress != null && progress.step() == 1 && !Workpiece.has(carrier), "Only native Create progress owns the first loop");
            check(mill.inputInv.getStackInSlot(0).getCount() == 2, "First round consumes one raw input");
            var untouched = mill.inputInv.extractItem(0, 64, false);
            mill.inputInv.setStackInSlot(0, carrier);
            process.invoke(mill);
            finished(mill.outputInv.extractItem(0, 64, false));
            check(untouched.getCount() == 2 && mill.inputInv.getStackInSlot(0).isEmpty(), "Second round consumes carrier once, not the saved raw stack");
            for (int i = 0; i < mill.outputInv.getSlots(); i++) check(mill.outputInv.getStackInSlot(i).isEmpty(), "No placeholder or duplicate milling reward");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void realCrafterConsumesShapelessAuxiliaryAndOnlyExpelsParentResult(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            s.install(sequence("iron_ingot", "iron_nugget", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:stick"}],"result":{"id":"minecraft:gold_ingot"}}
                """, 1));
            s.block(s.pos.east(), Blocks.CHEST.defaultBlockState());
            s.block(s.pos, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
            var crafter = (CrafterBlockEntity) helper.getLevel().getBlockEntity(s.pos);
            var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(s.pos.east());
            crafter.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            crafter.setItem(1, new ItemStack(Items.STICK, 2));
            helper.getLevel().getBlockState(s.pos).tick(helper.getLevel(), s.pos, helper.getLevel().random);
            check(crafter.getItem(0).getCount() == 1 && crafter.getItem(1).getCount() == 1, "Actual crafter pays each matched physical cell once");
            check(count(chest, Items.DIAMOND) == 1 && count(chest, Items.GOLD_INGOT) == 0 && count(chest, Items.IRON_NUGGET) == 0, "Native expulsion delivers only parent settlement, not recipe template/transit");
            finished(chest.getItem(0));
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void actualCampfireTickCompletesNativeSequenceOnce(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            s.install(sequence("iron_ingot", "iron_nugget", """
                {"type":"minecraft:campfire_cooking","ingredient":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:gold_ingot"},"cookingtime":2,"experience":0.0}
                """, 1));
            s.block(s.pos, Blocks.CAMPFIRE.defaultBlockState());
            var fire = (CampfireBlockEntity) helper.getLevel().getBlockEntity(s.pos);
            var raw = new ItemStack(Items.IRON_INGOT, 2);
            var found = fire.getCookableRecipe(raw).orElseThrow();
            check(fire.placeFood(null, raw, found.value().getCookingTime()), "Native campfire admits the sequence operation");
            check(raw.getCount() == 1, "Native insertion pays only one item");
            for (int i = 0; i < 4; i++) CampfireBlockEntity.cookTick(helper.getLevel(), s.pos, helper.getLevel().getBlockState(s.pos), fire);
            check(s.drops().size() == 1 && fire.getItems().stream().allMatch(ItemStack::isEmpty), "Exactly one completed native drop and no retained input");
            finished(s.drops().getFirst().getItem());
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void actualStonecutterPickupPaysOneAndNeverAwardsNativeTemplate(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            s.install(sequence("iron_ingot", "iron_nugget", """
                {"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:gold_ingot"}}
                """, 1));
            s.block(s.pos, Blocks.STONECUTTER.defaultBlockState());
            var menu = new StonecutterMenu(7, s.player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), s.pos));
            s.player.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT, 2));
            check(menu.clickMenuButton(s.player, 0), "Native sequence is a selectable stonecutter choice");
            menu.clicked(1, 0, ClickType.PICKUP, s.player);
            finished(menu.getCarried());
            check(menu.getSlot(0).getItem().getCount() == 1 && s.player.getInventory().isEmpty(), "Actual result pickup pays one input with no extra native reward");
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void actualSmithingPickupConsumesTemplateBaseAndAdditionOnce(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            s.install(sequence("iron_ingot", "iron_nugget", """
                {"type":"minecraft:smithing_transform","template":{"item":"minecraft:netherite_upgrade_smithing_template"},"base":{"item":"minecraft:iron_ingot"},"addition":{"item":"minecraft:copper_ingot"},"result":{"id":"minecraft:netherite_sword"}}
                """, 1));
            s.block(s.pos, Blocks.SMITHING_TABLE.defaultBlockState());
            var menu = new SmithingMenu(8, s.player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), s.pos));
            s.player.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 2));
            menu.getSlot(2).set(new ItemStack(Items.COPPER_INGOT, 2));
            menu.clicked(3, 0, ClickType.PICKUP, s.player);
            finished(menu.getCarried());
            for (int i = 0; i < 3; i++) check(menu.getSlot(i).getItem().getCount() == 1, "Actual smithing pickup pays each physical role once");
            check(s.player.getInventory().isEmpty(), "Native smithing template result is not an additional reward");
            menu.setCarried(ItemStack.EMPTY);
            for (int i = 0; i < 3; i++) menu.getSlot(i).set(ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void actualBrewingTickPaysOneSharedIngredientAndNativeFuel(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            s.install(sequence("potion", "potion", "{\"type\":\"graph_assembly:brewing\",\"primary\":\"bottle\"}", 1));
            s.block(s.pos, Blocks.BREWING_STAND.defaultBlockState());
            var stand = (BrewingStandBlockEntity) helper.getLevel().getBlockEntity(s.pos);
            stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            stand.setItem(3, new ItemStack(Items.NETHER_WART, 2));
            stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            for (int i = 0; i < 401; i++) BrewingStandBlockEntity.serverTick(helper.getLevel(), s.pos, helper.getLevel().getBlockState(s.pos), stand);
            finished(stand.getItem(0));
            check(stand.getItem(1).isEmpty() && stand.getItem(2).isEmpty() && stand.getItem(3).getCount() == 1 && stand.getItem(4).isEmpty(), "One actual native brewing round pays material/powder without duplicating bottle outcomes");
            check(stand.saveWithoutMetadata(helper.getLevel().registryAccess()).getByte("Fuel") == 19, "Native powder load of twenty spends one fuel");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void nativeFanBlastingRoutesSecondSourceAcrossTwoCreateLoops(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            // No smelting recipe exists. BLASTING must continue to its second native source,
            // including the second loop, when the input already has Create assembly progress.
            s.install(sequence("iron_ingot", "iron_nugget", """
                {"type":"minecraft:blasting","ingredient":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:gold_ingot"},"cookingtime":2,"experience":0.0}
                """, 2));
            int duration = AllConfigs.server().kinetics.fanProcessingTime.get();
            var input = new ItemStack(Items.IRON_INGOT);
            for (int loop = 0; loop < 2; loop++) {
                // A fresh native transported object models lifting/reinserting the previous
                // carrier: uninterrupted exposure must retain Create's completed -1 lock.
                var transported = new TransportedItemStack(input.copy());
                TransportedResult result = TransportedResult.doNothing();
                for (int tick = 0; tick < duration + 8; tick++) {
                    result = FanProcessing.applyProcessing(transported, helper.getLevel(), AllFanProcessingTypes.BLASTING);
                    if (!result.doesNothing()) break;
                }
                check(!result.doesNothing() && result.getOutputs().size() == 1,
                    "Actual Create fan must resolve blasting source and emit exactly one output on native loop " + loop);
                var output = result.getOutputs().getFirst();
                input = output.stack.copy();
                if (loop == 0) {
                    var progress = input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
                    check(input.is(Items.IRON_NUGGET) && input.getCount() == 1 && progress != null && progress.step() == 1 && !Workpiece.has(input),
                        "First native fan round must produce one Create-owned carrier, not native gold template");
                    for (int tick = 0; tick < duration + 4; tick++)
                        check(FanProcessing.applyProcessing(output, helper.getLevel(), AllFanProcessingTypes.BLASTING).doesNothing(),
                            "Continuous fan exposure must not auto-chain the next native loop");
                    check(output.processingTime == -1, "Completed native fan exposure remains locked until reinserted");
                } else finished(input);
            }
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="native_sequence_devices")
    public static void actualAnvilRepairRetainsNativeFeesButAwardsOnlyParentResult(GameTestHelper helper) {
        try (var s = new Session(helper)) {
            helper.getLevel().getRecipeManager().replaceRecipes(List.of());
            GraphIndex.invalidate();
            s.block(s.pos, Blocks.ANVIL.defaultBlockState());
            s.player.getAbilities().instabuild = false;
            s.player.experienceLevel = 30;
            var menu = new AnvilMenu(9, s.player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), s.pos));
            s.player.containerMenu = menu;
            var sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(100);
            menu.getSlot(0).set(sword.copy());
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 3));
            menu.slotsChanged(menu.getSlot(0).container);
            int cost = menu.getCost();
            check(cost > 0 && menu.getSlot(2).getItem().is(Items.IRON_SWORD) && menu.getSlot(2).getItem().getDamageValue() < 100,
                "Ordinary native control exposes a real sword material repair with an observed XP fee");
            menu.clicked(2, 0, ClickType.PICKUP, s.player);
            int paidXp = s.player.experienceLevel;
            var paidMaterial = menu.getSlot(1).getItem().copy();
            check(paidXp == 30 - cost && menu.getSlot(0).getItem().isEmpty() && paidMaterial.getCount() > 0 && paidMaterial.getCount() < 3,
                "Native control pays exact observed XP and only required repair material");
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            helper.getLevel().setBlockAndUpdate(s.pos, Blocks.ANVIL.defaultBlockState());
            s.player.experienceLevel = 30;
            s.install(sequence("iron_sword", "iron_sword", """
                {"type":"graph_assembly:anvil","action":"repair","addition":{"item":"minecraft:iron_ingot"}}
                """, 1));
            menu.getSlot(0).set(sword.copy());
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 3));
            menu.slotsChanged(menu.getSlot(0).container);
            check(menu.getCost() == cost && menu.getSlot(2).getItem().is(Items.DIAMOND), "Native sequence preview preserves actual repair cost and shows parent final result");
            check(s.player.experienceLevel == 30 && ItemStack.matches(menu.getSlot(0).getItem(), sword)
                && menu.getSlot(1).getItem().is(Items.IRON_INGOT) && menu.getSlot(1).getItem().getCount() == 3,
                "Preview alone changes neither primary repair input, material payment nor XP");
            menu.clicked(2, 0, ClickType.PICKUP, s.player);
            finished(menu.getCarried());
            check(s.player.experienceLevel == paidXp && menu.getSlot(0).getItem().isEmpty() && ItemStack.matches(menu.getSlot(1).getItem(), paidMaterial),
                "Sequence result pickup pays the same native XP/material once, not a free synthetic repair");
            check(s.player.getInventory().isEmpty(), "Native repaired sword is not awarded alongside parent final diamond");
            menu.setCarried(ItemStack.EMPTY);
            for (int i = 0; i < 3; i++) menu.getSlot(i).set(ItemStack.EMPTY);
        }
        helper.succeed();
    }
}
