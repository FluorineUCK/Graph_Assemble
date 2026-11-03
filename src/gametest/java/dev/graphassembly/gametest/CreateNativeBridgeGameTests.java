package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Native Create insertion and machine ticks, not a direct GraphRuntime settlement. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class CreateNativeBridgeGameTests {
    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeSawInsertsAndProcessesGraphStonecuttingEntry(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        try {
            check(AllConfigs.server().recipes.allowStonecuttingOnSaw.get(),"Tracer requires native stonecutting-on-saw configuration enabled");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:stonecutting",
                   "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"}}},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:saw_stonecutting_bridge"),graph)));GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(SawBlock.FACING,Direction.UP));
            var saw=(SawBlockEntity)level.getBlockEntity(pos);saw.initialize();saw.setSpeed(128);
            var input=new ItemEntity(level,pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5,new ItemStack(Items.AMETHYST_SHARD));
            level.addFreshEntity(input);saw.insertItem(input);
            check(!input.isAlive()&&saw.inventory.getStackInSlot(0).is(Items.AMETHYST_SHARD),"Real native saw insertion must accept the item entity");
            for(int tick=0;tick<40;tick++){saw.setSpeed(128);saw.tick();}
            var drops=level.getEntitiesOfClass(ItemEntity.class,bounds);
            check(drops.size()==1,"Native saw processing and export must produce exactly one item entity");
            var output=drops.getFirst().getItem();var progress=Workpiece.read(level,output);
            check(output.is(Items.IRON_NUGGET)&&output.getCount()==1&&progress!=null
                &&progress.graphId().equals("test:saw_stonecutting_bridge")&&progress.current().equals("s"),
                "Native saw must execute graph-only minecraft:stonecutting and export its once-advanced carrier, not return raw input or native result");
            check(saw.inventory.isEmpty(),"Native export must leave no duplicate input or result in saw inventory");
            helper.succeed();
        } finally {
            level.removeBlock(pos,false);level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);
            manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeMixerTicksExecuteGraphShapelessEntry(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var mixerPos=pos.above(2);
        try {
            check(AllConfigs.server().recipes.allowShapelessInMixer.get(),"Tracer requires native shapeless-on-mixer configuration enabled");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:crafting_shapeless",
                   "ingredients":[{"item":"minecraft:amethyst_shard"},{"item":"minecraft:copper_ingot"}],"result":{"id":"minecraft:stone"}}},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:mixer_shapeless_bridge"),graph)));GraphIndex.invalidate();
            clearCreateRecipeCaches(helper);
            level.setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)level.getBlockEntity(pos);basin.initialize();
            level.setBlockAndUpdate(mixerPos,AllBlocks.MECHANICAL_MIXER.getDefaultState());
            var mixer=(MechanicalMixerBlockEntity)level.getBlockEntity(mixerPos);mixer.initialize();mixer.setSpeed(256);
            basin.inputInventory.setStackInSlot(0,new ItemStack(Items.AMETHYST_SHARD));
            basin.inputInventory.setStackInSlot(1,new ItemStack(Items.COPPER_INGOT));
            basin.notifyChangeOfContents();mixer.basinChecker.scheduleUpdate();
            for(int tick=0;tick<100;tick++){mixer.setSpeed(256);basin.tick();mixer.tick();}
            var outputs=new ArrayList<ItemStack>();
            for(int slot=0;slot<basin.getOutputInventory().getSlots();slot++) {
                var stack=basin.getOutputInventory().getStackInSlot(slot);if(!stack.isEmpty())outputs.add(stack);
            }
            check(outputs.size()==1,"Native mixer ticks must execute graph-only shapeless entry into one basin result");
            var output=outputs.getFirst();var progress=Workpiece.read(level,output);
            check(output.is(Items.IRON_NUGGET)&&output.getCount()==1&&progress!=null
                &&progress.graphId().equals("test:mixer_shapeless_bridge")&&progress.current().equals("s"),
                "Native mixer must produce once-advanced graph carrier, not ordinary native stone");
            check(basin.inputInventory.isEmpty(),"Native mixer must consume primary and auxiliary exactly once");
            helper.succeed();
        } finally {
            level.removeBlock(mixerPos,false);level.removeBlock(pos,false);
            level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
            manager.replaceRecipes(saved);GraphIndex.invalidate();clearCreateRecipeCaches(helper);
        }
    }
    private static void clearCreateRecipeCaches(GameTestHelper helper) {
        var resources=helper.getLevel().getServer().getResourceManager();
        com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(resources);
        com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(resources);
    }
    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void sawNativeDurationCarrierFilterAndSavedProgressRespectAdmission(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var config=AllConfigs.server().recipes.allowStonecuttingOnSaw;boolean savedConfig=config.get();
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        try {
            config.set(true);var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"}}},
                   {"id":"n","recipe":{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:iron_nugget"},"result":{"id":"minecraft:stone"}}},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],"edges":[{"from":"s","to":"n"},{"from":"n","to":"f"}]}
                """).getAsJsonObject());var id=ResourceLocation.parse("test:saw_policy");manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();clearCreateRecipeCaches(helper);
            level.setBlockAndUpdate(pos,AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(SawBlock.FACING,Direction.UP));var saw=(SawBlockEntity)level.getBlockEntity(pos);saw.initialize();saw.setSpeed(128);
            var filtering=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(saw,com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
            filtering.setFilter(new ItemStack(Items.STONE));insertSaw(level,saw,new ItemStack(Items.AMETHYST_SHARD));
            check(saw.inventory.recipeDuration==10,"Filter for ungranted native stone template must reject carrier-representative graph route");saw.inventory.clear();
            filtering.setFilter(new ItemStack(Items.IRON_NUGGET));insertSaw(level,saw,new ItemStack(Items.AMETHYST_SHARD));
            check(saw.inventory.recipeDuration==50,"Admitted graph stonecutting keeps exact upstream native fifty-tick saw duration");
            for(int tick=0;tick<40;tick++){saw.setSpeed(128);saw.tick();}
            var outputs=level.getEntitiesOfClass(ItemEntity.class,bounds);check(outputs.size()==1&&Workpiece.has(outputs.getFirst().getItem()),"Carrier output filter must admit real native graph saw processing");var carrier=outputs.getFirst().getItem().copy();outputs.forEach(ItemEntity::discard);
            insertSaw(level,saw,carrier);check(saw.inventory.recipeDuration==50,"Continuation must enter native stonecutting timing before save");
            var savedJob=saw.saveWithFullMetadata(level.registryAccess());level.removeBlockEntity(pos);config.set(false);
            saw=(SawBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,level.getBlockState(pos),savedJob,level.registryAccess());level.setBlockEntity(saw);saw.initialize();
            for(int tick=0;tick<40;tick++){saw.setSpeed(128);saw.tick();}
            outputs=level.getEntitiesOfClass(ItemEntity.class,bounds);check(outputs.size()==1&&Workpiece.read(level,outputs.getFirst().getItem()).current().equals("s"),"Restored native pending saw job cannot bypass disabled config to advance progress");outputs.forEach(ItemEntity::discard);
            config.set(true);var data=graph.json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"recipe_id\":\"test:saw_manual_only\"}"));
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,new GraphRecipe(data)),new RecipeHolder<>(ResourceLocation.parse("test:saw_manual_only"),new net.minecraft.world.item.crafting.StonecutterRecipe("",net.minecraft.world.item.crafting.Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE)))));GraphIndex.invalidate();clearCreateRecipeCaches(helper);
            filtering=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(saw,com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);filtering.setFilter(ItemStack.EMPTY);saw.inventory.clear();insertSaw(level,saw,new ItemStack(Items.AMETHYST_SHARD));
            check(saw.inventory.recipeDuration==10,"Original referenced manual-only ID must not gain native saw automation through graph wrapper ID");
        } finally {config.set(savedConfig);level.removeBlock(pos,false);level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();clearCreateRecipeCaches(helper);}
        helper.succeed();
    }
    private static void insertSaw(net.minecraft.world.level.Level level,SawBlockEntity saw,ItemStack stack) {
        var pos=saw.getBlockPos();var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5,stack);level.addFreshEntity(entity);saw.insertItem(entity);
        check(!entity.isAlive(),"Native saw policy fixture must accept supplied entity into empty inventory");
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
