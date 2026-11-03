package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

/** Acceptance through the actual vanilla server tick; no direct graph completion calls. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeFurnaceGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void furnaceServerTickStartsAndContinuesGraphExactlyOnce(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:smelting",
                   "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":4,"experience":1}},
                   {"id":"next","recipe":{"type":"minecraft:smelting",
                   "ingredient":{"item":"minecraft:iron_nugget"},"result":{"id":"minecraft:stone"},"cookingtime":4,"experience":1}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"start","to":"next"},{"from":"next","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(java.util.List.of(new RecipeHolder<>(ResourceLocation.parse("test:furnace_tracer"),graph)));
            GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());
            var furnace=(FurnaceBlockEntity)level.getBlockEntity(pos);
            furnace.setItem(0,new ItemStack(Items.AMETHYST_SHARD,2));
            furnace.setItem(1,new ItemStack(Items.COAL,2));
            for(int i=0;i<4;i++) AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
            var first=furnace.getItem(2).copy();
            check(first.is(Items.IRON_NUGGET)&&first.getCount()==1,"Real furnace tick must emit exactly one graph carrier after native cookingtime");
            var start=Workpiece.read(level,first);
            check(start!=null&&start.current().equals("start"),"First furnace round must advance start exactly once");
            check(furnace.getItem(0).is(Items.AMETHYST_SHARD)&&furnace.getItem(0).getCount()==1,"First round consumes one input only");
            check(furnace.getItem(1).is(Items.COAL)&&furnace.getItem(1).getCount()==1,"Native burn loop consumes one coal, not one per cooking round");
            furnace.setItem(2,ItemStack.EMPTY);
            furnace.setItem(0,first);
            for(int i=0;i<4;i++) AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
            var next=furnace.getItem(2);
            var progress=Workpiece.read(level,next);
            check(next.is(Items.IRON_NUGGET)&&next.getCount()==1&&progress!=null&&progress.current().equals("next"),"Continuation through real tick must advance exactly once");
            check(furnace.getItem(0).isEmpty(),"Continuation consumes its sole carrier");
            check(furnace.getItem(1).getCount()==1,"Existing burn time must power continuation without extra coal");
            helper.succeed();
        } finally {
            level.removeBlock(pos,false);
            manager.replaceRecipes(saved); GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
