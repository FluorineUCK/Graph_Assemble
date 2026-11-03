package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.content.kinetics.fan.processing.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
public final class NativeFanBridgeGameTests {
    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeSmokingCountdownAdvancesGraphOncePerExposure(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,new ItemStack(Items.AMETHYST_SHARD));
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:smoking",
                   "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":2}},
                   {"id":"f","finish":true,"recipe":{"type":"minecraft:smoking",
                   "ingredient":{"item":"minecraft:iron_nugget"},"result":{"id":"minecraft:stone"},"cookingtime":2}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:fan_smoking_bridge"),graph)));GraphIndex.invalidate();level.addFreshEntity(entity);
            int duration=AllConfigs.server().kinetics.fanProcessingTime.get();var type=AllFanProcessingTypes.SMOKING;
            for(int tick=0;tick<duration;tick++)if(FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);
            check(entity.getItem().is(Items.AMETHYST_SHARD)&&!Workpiece.has(entity.getItem()),"Fan must honor native exposure countdown, not cookingtime2 or immediate graph completion");
            for(int tick=0;tick<8;tick++)if(FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);
            var output=entity.getItem();var progress=Workpiece.read(level,output);
            check(entity.isAlive()&&output.is(Items.IRON_NUGGET)&&output.getCount()==1&&progress!=null
                &&progress.graphId().equals("test:fan_smoking_bridge")&&progress.current().equals("s"),
                "Native smoking fan countdown must execute graph-only smoking and preserve one-exposure once-advanced carrier");
            check(!FanProcessing.canProcess(entity,type),"Native completed exposure must retain its -1 lock instead of chaining the next smoking node");
            check(level.getEntitiesOfClass(ItemEntity.class,bounds).size()==1,"Native fan graph round must not duplicate input or grant template stone");
            helper.succeed();
        } finally {entity.discard();level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeBlastingAdmitsSmeltingBeforeBlastingAndFallsBackToBlasting(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        try {
            for(boolean smelting:List.of(true,false)) {
                var graph=new GraphRecipe(JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:%s",
                       "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":2}},
                       %s
                       {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                     "edges":[{"from":"s","to":"f"}%s],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """.formatted(smelting?"smelting":"blasting",smelting?"""
                        {"id":"b","start":true,"recipe":{"type":"minecraft:blasting","ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:gravel"},"cookingtime":2}},
                        """:"",smelting?",{\"from\":\"b\",\"to\":\"f\"}":"")).getAsJsonObject());
                manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:fan_blasting_bridge"),graph)));GraphIndex.invalidate();
                var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,new ItemStack(Items.AMETHYST_SHARD));level.addFreshEntity(entity);
                try {
                    for(int tick=0;tick<AllConfigs.server().kinetics.fanProcessingTime.get()+4;tick++)
                        if(FanProcessing.canProcess(entity,AllFanProcessingTypes.BLASTING))FanProcessing.applyProcessing(entity,AllFanProcessingTypes.BLASTING);
                    var output=entity.getItem();var progress=Workpiece.read(level,output);
                    check(entity.isAlive()&&output.is(Items.IRON_NUGGET)&&progress!=null&&progress.current().equals("s"),
                        "Native blasting must execute graph "+(smelting?"smelting before competing blasting":"blasting fallback without smelting")+" instead of destroying raw input");
                } finally {entity.discard();}
            }
            helper.succeed();
        } finally {level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void changedFanInputAndReloadCannotBorrowEntityOrTransportedCountdown(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);
        var type=AllFanProcessingTypes.SMOKING;int duration=AllConfigs.server().kinetics.fanProcessingTime.get();
        var outcomes=new ArrayList<String>();
        try {
            for(boolean belt:List.of(false,true))for(boolean reload:List.of(false,true)) {
                var graph=new GraphRecipe(JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:smoking","ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":2}},
                       {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],"edges":[{"from":"s","to":"f"}]}
                    """).getAsJsonObject());
                var id=ResourceLocation.parse("test:fan_timer_identity");manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();
                boolean preserved;
                if(belt) {
                    var transported=new TransportedItemStack(new ItemStack(Items.AMETHYST_SHARD));
                    for(int tick=0;tick<=duration;tick++)check(FanProcessing.applyProcessing(transported,level,type).doesNothing(),"Transported timer fixture must remain before native completion");
                    check(transported.processingTime==1,"Transported fixture must reach final pre-completion tick");
                    if(reload){manager.replaceRecipes(List.of(new RecipeHolder<>(id,new GraphRecipe(graph.json()))));GraphIndex.invalidate();}
                    else transported.stack.set(DataComponents.CUSTOM_NAME,Component.literal("replaced input"));
                    var first=FanProcessing.applyProcessing(transported,level,type);var second=FanProcessing.applyProcessing(transported,level,type);
                    preserved=first.doesNothing()&&second.doesNothing()&&transported.processingTime>=duration&&!Workpiece.has(transported.stack);
                } else {
                    var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,new ItemStack(Items.AMETHYST_SHARD));level.addFreshEntity(entity);
                    try {
                        for(int tick=0;tick<duration;tick++)if(FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);
                        check(entity.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==1&&!Workpiece.has(entity.getItem()),"Entity fixture must reach final pre-completion tick");
                        if(reload){manager.replaceRecipes(List.of(new RecipeHolder<>(id,new GraphRecipe(graph.json()))));GraphIndex.invalidate();}
                        else entity.getItem().set(DataComponents.CUSTOM_NAME,Component.literal("replaced input"));
                        if(FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);
                        preserved=entity.getItem().is(Items.AMETHYST_SHARD)&&!Workpiece.has(entity.getItem())
                            &&entity.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==duration;
                    } finally {entity.discard();}
                }
                outcomes.add((belt?"transported":"entity")+"/"+(reload?"reload":"input")+"="+preserved);
            }
            check(outcomes.stream().allMatch(result->result.endsWith("=true")),"Changed fan operation must start fresh native countdown: "+outcomes);
            helper.succeed();
        } finally {level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void savedCompletedFanEntityCannotImposeItsLockOnReplacementInput(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var bounds=new AABB(pos).inflate(2);var type=AllFanProcessingTypes.SMOKING;
        var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,new ItemStack(Items.AMETHYST_SHARD));
        ItemEntity loaded=null;
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:smoking","ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":2}},
                 {"id":"f","finish":true,"recipe":{"type":"minecraft:smoking","ingredient":{"item":"minecraft:iron_nugget"},"result":{"id":"minecraft:stone"},"cookingtime":2}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:fan_completed_identity"),graph)));GraphIndex.invalidate();level.addFreshEntity(entity);
            int duration=AllConfigs.server().kinetics.fanProcessingTime.get();
            for(int tick=0;tick<duration+4;tick++)if(FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);
            check(Workpiece.has(entity.getItem())&&!FanProcessing.canProcess(entity,type),"Fixture must finish exactly one graph round and retain native completed exposure lock");
            var tag=entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());entity.discard();
            loaded=new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,ItemStack.EMPTY);loaded.load(tag);loaded.setUUID(UUID.randomUUID());level.addFreshEntity(loaded);
            check(Workpiece.has(loaded.getItem())&&!FanProcessing.canProcess(loaded,type),"Freshly loaded unchanged completed carrier must retain its own native lock");
            loaded.setItem(new ItemStack(Items.AMETHYST_SHARD));boolean eligible=FanProcessing.canProcess(loaded,type);
            if(eligible)FanProcessing.applyProcessing(loaded,type);
            check(eligible&&loaded.getItem().is(Items.AMETHYST_SHARD)&&!Workpiece.has(loaded.getItem())
                &&loaded.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==duration,
                "Completed saved entity lock must be bound to loaded output identity, not impose stale completion on replacement raw input");
            helper.succeed();
        } finally {entity.discard();if(loaded!=null)loaded.discard();level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
