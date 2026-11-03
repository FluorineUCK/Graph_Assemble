package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class MechanicalCrafterBridgeGameTests {
    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeMechanicalCrafterInsertionAndTicksExecuteStandardGraphEntry(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        try {
            check(AllConfigs.server().recipes.allowRegularCraftingInCrafter.get(),"Tracer requires native regular-crafting-in-crafter configuration enabled");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:amethyst_shard"}],"result":{"id":"minecraft:stone"}}},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:mechanical_crafter_bridge"),graph)));GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,AllBlocks.MECHANICAL_CRAFTER.getDefaultState());
            var crafter=(MechanicalCrafterBlockEntity)level.getBlockEntity(pos);crafter.initialize();crafter.setSpeed(128);
            var remainder=crafter.getInventory().insertItem(0,new ItemStack(Items.AMETHYST_SHARD,2),false);
            check(remainder.is(Items.AMETHYST_SHARD)&&remainder.getCount()==1,"Native mechanical crafter inventory accepts exactly one unit into one grid cell");
            for(int tick=0;tick<50;tick++){crafter.setSpeed(128);crafter.tick();}
            var drops=level.getEntitiesOfClass(ItemEntity.class,bounds);check(drops.size()==1,"Native mechanical crafter round exports exactly one result entity");
            var output=drops.getFirst().getItem();var progress=Workpiece.read(level,output);
            check(output.is(Items.IRON_NUGGET)&&output.getCount()==1&&progress!=null&&progress.current().equals("s")
                &&progress.graphId().equals("test:mechanical_crafter_bridge"),
                "Native mechanical crafter must execute graph-only standard crafting and export once-advanced carrier, not eject raw input or template stone");
            check(crafter.getInventory().isEmpty(),"Native cell inventory contains no duplicate input after its graph round");
            helper.succeed();
        } finally {level.removeBlock(pos,false);level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
