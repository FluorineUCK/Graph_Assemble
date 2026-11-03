package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Synchronous device regressions; each restores the shared recipe manager before returning. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class BulkGraphGameTests {
    private static final String BATCH = "graph_assembly_device_regressions";

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void sawBulkCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var recipe = graph("""
                    [{"id":"s","start":true,"recipe":{"type":"create:cutting","processing_time":20,
                      "results":[{"id":"minecraft:flint","count":64}]}}]
                    """, "[]");
            manager.replaceRecipes(List.of(holder("test:saw_bulk", recipe)));
            GraphIndex.invalidate();
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(SawBlock.FACING, Direction.UP));
            var saw = (SawBlockEntity) helper.getBlockEntity(pos);
            saw.initialize();
            // Same inventory mode as bulkCutting=true, without mutating global server configuration.
            saw.inventory.withSlotLimit(false);
            saw.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
            AABB bounds = new AABB(saw.getBlockPos()).inflate(3);
            int entitiesBefore = level.getEntitiesOfClass(ItemEntity.class, bounds).size();

            applySaw(saw);

            check(saw.inventory.getStackInSlot(0).is(Items.IRON_INGOT)
                    && saw.inventory.getStackInSlot(0).getCount() == 64,
                    "A bulk batch exceeding saw storage must preserve all 64 raw inputs");
            for (int slot = 1; slot < saw.inventory.getSlots(); slot++)
                check(saw.inventory.getStackInSlot(slot).isEmpty(), "Rejected batch must not roll partial outputs");
            check(level.getEntitiesOfClass(ItemEntity.class, bounds).size() == entitiesBefore,
                    "Capacity rejection must not compensate with dropped entities");

            saw.inventory.clear();
            saw.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 12));
            applySaw(saw);
            int workpieces = 0, byproducts = 0;
            for (int slot = 0; slot < saw.inventory.getSlots(); slot++) {
                var stack = saw.inventory.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                if (Workpiece.has(stack)) {
                    check(stack.getCount() == 1 && stack.getMaxStackSize() == 1, "Each main output must remain one independent workpiece");
                    check(Workpiece.read(stack).entry().equals("s"), "Each bulk operation must complete its own start");
                    workpieces++;
                } else {
                    check(stack.is(Items.FLINT), "Fitting batch cannot retain consumed raw material or invent output");
                    byproducts += stack.getCount();
                }
            }
            check(workpieces == 12 && byproducts == 12 * 64,
                    "Fitting batch must retain all 12 main outputs and all 768 byproducts");
            check(level.getEntitiesOfClass(ItemEntity.class, bounds).size() == entitiesBefore,
                    "Fitting batch must use inventory, not world drops");

            saw.inventory.clear();
            saw.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            saw.inventory.setStackInSlot(1, new ItemStack(Items.DIAMOND, 64));
            applySaw(saw);
            check(saw.inventory.getStackInSlot(0).is(Items.IRON_INGOT)
                            && saw.inventory.getStackInSlot(1).is(Items.DIAMOND)
                            && saw.inventory.getStackInSlot(1).getCount() == 64,
                    "Existing outputs must never be erased by native inventory.clear");
            helper.succeed();
        } finally {
            manager.replaceRecipes(originals);
            GraphIndex.invalidate();
        }
    }

    @GameTest(template = "test_empty", batch = BATCH, timeoutTicks = 100)
    public static void millReloadInvalidatesVanillaCache(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            Recipe<?> vanilla = Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString("""
                            {"type":"create:milling","ingredients":[{"item":"minecraft:iron_ingot"}],
                             "results":[{"id":"minecraft:flint"}],"processing_time":7}
                            """)).getOrThrow();
            var vanillaHolder = holder("test:cached_vanilla_milling", vanilla);
            manager.replaceRecipes(List.of(vanillaHolder));
            GraphIndex.invalidate();
            BlockPos pos = new BlockPos(2, 1, 2);
            helper.setBlock(pos, AllBlocks.MILLSTONE.getDefaultState());
            var mill = (MillstoneBlockEntity) helper.getBlockEntity(pos);
            mill.initialize();
            // Isolate recipe timing from network attachment while executing the real native tick.
            setField(mill, "updateSpeed", false);
            setField(mill, "validationCountdown", Integer.MAX_VALUE);
            mill.setSpeed(16);
            mill.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            mill.tick();
            check(mill.timer == 7, "Fixture must first cache the native seven-tick recipe");

            var graph = graph("""
                    [{"id":"s","start":true,"finish":true,
                      "recipe":{"type":"create:milling","processing_time":73}}]
                    """, "[{\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
            // Reload while the old recipe is already partway through processing.
            mill.timer = 3;
            manager.replaceRecipes(List.of(vanillaHolder, holder("test:new_graph_milling", graph)));
            GraphIndex.invalidate();
            mill.tick();
            check(mill.timer == 73, "Reload must restart using graph processing_time, not cached vanilla timing");
            check(mill.inputInv.getStackInSlot(0).getCount() == 2, "Cache refresh must not consume raw input");
            mill.tick();
            check(mill.timer == 72, "A stable graph index must not reset the timer every tick");
            mill.timer = 1;
            mill.tick();
            check(mill.inputInv.getStackInSlot(0).getCount() == 1
                            && mill.outputInv.getStackInSlot(0).is(Items.DIAMOND),
                    "The refreshed native mill operation must consume one input and settle the graph");
            helper.succeed();
        } finally {
            manager.replaceRecipes(originals);
            GraphIndex.invalidate();
        }
    }

    private static GraphRecipe graph(String nodes, String outputs) {
        return new GraphRecipe(JsonParser.parseString("{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},"
                + "\"transitional_item\":\"minecraft:iron_nugget\",\"nodes\":" + nodes
                + ",\"edges\":[],\"outputs\":" + outputs + "}").getAsJsonObject());
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(String id, R recipe) {
        return new RecipeHolder<>(ResourceLocation.parse(id), recipe);
    }

    private static void applySaw(SawBlockEntity saw) {
        try {
            var method = SawBlockEntity.class.getDeclaredMethod("applyRecipe");
            method.setAccessible(true);
            method.invoke(saw);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Native saw applyRecipe invocation failed", e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Kinetic fixture fields are inherited by the native millstone.
            } catch (IllegalAccessException e) {
                throw new AssertionError("Cannot set fixture field " + name, e);
            }
        }
        throw new AssertionError("Missing native fixture field " + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
