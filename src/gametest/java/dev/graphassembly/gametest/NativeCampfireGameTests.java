package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCampfireGameTests {
    @GameTest(template="test_empty",timeoutTicks=60,batch="native_campfire")
    public static void nativeInsertionAndLitTickDropGraphCarrier(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:campfire_cooking",
                   "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":4}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"start","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:campfire_tracer"),graph)));
            GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,Blocks.CAMPFIRE.defaultBlockState());
            var fire=(CampfireBlockEntity)level.getBlockEntity(pos);
            var held=new ItemStack(Items.AMETHYST_SHARD,2);
            var cookable=fire.getCookableRecipe(held);
            check(cookable.isPresent(),"Native campfire insertion must recognize graph-only entry");
            check(fire.placeFood(null,held,cookable.orElseThrow().value().getCookingTime()),"Native placeFood must accept graph entry");
            check(held.getCount()==1,"Native insertion must consume only one input");
            for(int i=0;i<3;i++)CampfireBlockEntity.cookTick(level,pos,level.getBlockState(pos),fire);
            check(!fire.getItems().getFirst().isEmpty(),"Native cookingtime must not complete early");
            CampfireBlockEntity.cookTick(level,pos,level.getBlockState(pos),fire);
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1));
            check(drops.size()==1,"One native cooking round must produce exactly one drop");
            var output=drops.getFirst().getItem();var progress=Workpiece.read(level,output);
            check(output.is(Items.IRON_NUGGET)&&output.getCount()==1&&progress!=null
                &&progress.graphId().equals("test:campfire_tracer")&&progress.current().equals("start"),
                "Lit native cookTick must drop exactly-once advanced graph carrier");
            check(fire.getItems().stream().allMatch(ItemStack::isEmpty),"Paid cooking slot must be empty");
            drops.forEach(ItemEntity::discard);
            helper.succeed();
        } finally {level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
