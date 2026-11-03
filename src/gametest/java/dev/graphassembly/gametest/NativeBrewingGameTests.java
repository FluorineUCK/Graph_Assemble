package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeBrewingGameTests {
    @GameTest(template="test_empty", batch="native_brewing_runtime")
    public static void naturalWaterBrewRetainsGraph(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=List.copyOf(manager.getRecipes());
        try {
            var recipe=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:potion"},"transitional_item":"minecraft:iron_nugget","nodes":[
                {"id":"brew","start":true,"recipe":{"type":"graph_assembly:brewing"}},
                {"id":"end","finish":true,"recipe":{"type":"graph_assembly:brewing"}}],
                "edges":[{"from":"brew","to":"end"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:natural_brew"),recipe)));
            var pos=helper.absolutePos(new BlockPos(1,1,1));level.setBlock(pos,Blocks.BREWING_STAND.defaultBlockState(),3);
            var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);
            stand.setItem(0,PotionContents.createItemStack(Items.POTION,Potions.WATER));
            stand.setItem(3,new ItemStack(Items.NETHER_WART,2));stand.setItem(4,new ItemStack(Items.BLAZE_POWDER));
            for(int i=0;i<401;i++)BrewingStandBlockEntity.serverTick(level,pos,level.getBlockState(pos),stand);
            if(!Workpiece.has(stand.getItem(0)))throw new GameTestAssertException("Natural 400-tick brew lost graph progress");
            if(!stand.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).is(Potions.AWKWARD))throw new GameTestAssertException("Native carrier must be awkward potion");
            if(stand.getItem(3).getCount()!=1)throw new GameTestAssertException("Exactly one shared material");
        } finally {manager.replaceRecipes(saved);GraphIndex.invalidate();}
        helper.succeed();
    }
}
