package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Public natural redstone/scheduled-tick acceptance, without an adapter call. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCrafterGameTests {
    @GameTest(template="test_empty", timeoutTicks=60, batch="native_crafter")
    public static void redstonePulseDeliversGraphCarrierToChestExactlyOnce(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));
        var target=pos.east(); var power=pos.west();
        Runnable cleanup=()->{
            level.removeBlock(power,false); level.removeBlock(pos,false); level.removeBlock(target,false);
            manager.replaceRecipes(saved); GraphIndex.invalidate();
        };
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:crafting_shapeless",
                    "ingredients":[{"item":"minecraft:amethyst_shard"}],"result":{"id":"minecraft:iron_nugget"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"start","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:crafter_tracer"),graph)));
            GraphIndex.invalidate();
            level.setBlockAndUpdate(target,Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos,Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION,FrontAndTop.EAST_UP));
            var crafter=(CrafterBlockEntity)level.getBlockEntity(pos);
            crafter.setItem(0,new ItemStack(Items.AMETHYST_SHARD,2));
            level.setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(12,()->{
                try {
                    var chest=(ChestBlockEntity)level.getBlockEntity(target);
                    var output=chest.getItem(0);
                    check(output.is(Items.IRON_NUGGET)&&output.getCount()==1,"Scheduled native redstone pulse must deliver one graph carrier to chest");
                    var progress=Workpiece.read(level,output);
                    check(progress!=null&&progress.graphId().equals("test:crafter_tracer")&&progress.current().equals("start"),"Chest carrier must inherit exactly one start advancement");
                    check(crafter.getItem(0).is(Items.AMETHYST_SHARD)&&crafter.getItem(0).getCount()==1,"Held signal must consume one input round only");
                    helper.succeed();
                } finally {cleanup.run();}
            });
        } catch(RuntimeException|Error failure) {cleanup.run();throw failure;}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
