package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class BasinNativeBridgeContractsGameTests {
    private static final String SHAPELESS="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:amethyst_shard"},{"item":"minecraft:copper_ingot"}],"result":{"id":"minecraft:stone"}}
        """;
    private static GraphRecipe graph(String ingredient,String recipe) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:%s"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":%s},{"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """.formatted(ingredient,recipe)).getAsJsonObject());
    }
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final BlockPos pos,machinePos;
        final BasinBlockEntity basin;final BasinOperatingBlockEntity machine;
        Session(GameTestHelper helper,boolean press) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            pos=helper.absolutePos(new BlockPos(1,1,1));machinePos=pos.above(2);
            helper.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());basin=(BasinBlockEntity)helper.getLevel().getBlockEntity(pos);basin.initialize();
            helper.getLevel().setBlockAndUpdate(machinePos,press?AllBlocks.MECHANICAL_PRESS.getDefaultState():AllBlocks.MECHANICAL_MIXER.getDefaultState());
            machine=(BasinOperatingBlockEntity)helper.getLevel().getBlockEntity(machinePos);machine.initialize();machine.setSpeed(256);
        }
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();caches();}
        void inputs(){basin.inputInventory.setStackInSlot(0,new ItemStack(Items.AMETHYST_SHARD));basin.inputInventory.setStackInSlot(1,new ItemStack(Items.COPPER_INGOT));}
        RecipeHolder<?> source(String id){return new RecipeHolder<>(ResourceLocation.parse(id),Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(SHAPELESS)).getOrThrow());}
        @SuppressWarnings("unchecked") List<Recipe<?>> matching() {
            try {var method=BasinOperatingBlockEntity.class.getDeclaredMethod("getMatchingRecipes");method.setAccessible(true);return (List<Recipe<?>>)method.invoke(machine);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Native basin recipe query fixture",failure);}
        }
        List<ItemStack> outputs(){var output=new ArrayList<ItemStack>();for(int slot=0;slot<basin.getOutputInventory().getSlots();slot++){var stack=basin.getOutputInventory().getStackInSlot(slot);if(!stack.isEmpty())output.add(stack);}return output;}
        void caches(){var resources=helper.getLevel().getServer().getResourceManager();com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(resources);com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(resources);}
        @Override public void close(){helper.getLevel().removeBlock(machinePos,false);helper.getLevel().removeBlock(pos,false);helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).forEach(ItemEntity::discard);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();caches();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void pressUsesOriginalCompressibilityAndRechecksConfigBeforeNativeCommit(GameTestHelper helper) {
        var config=AllConfigs.server().recipes.allowShapedSquareInPress;boolean saved=config.get();
        try(var s=new Session(helper,true)) {
            config.set(true);
            var graph=graph("copper_ingot","""
                {"type":"minecraft:crafting_shaped","pattern":["AA","AA"],"key":{"A":{"item":"minecraft:amethyst_shard"}},"result":{"id":"minecraft:stone"}}
                """);
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:compress_bridge"),graph));
            s.basin.inputInventory.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT));s.basin.inputInventory.setStackInSlot(1,new ItemStack(Items.AMETHYST_SHARD,3));
            var found=s.matching();check(found.size()==1,"Original four equivalent amethyst ingredients qualify even though logical primary0 is copper");
            var cached=found.getFirst();config.set(false);
            check(!BasinRecipe.apply(s.basin,cached)&&s.basin.inputInventory.getStackInSlot(1).getCount()==3&&s.outputs().isEmpty(),"Cached compression cannot bypass disabled upstream press config");
            config.set(true);s.basin.notifyChangeOfContents();s.machine.basinChecker.scheduleUpdate();
            for(int tick=0;tick<250;tick++){s.machine.setSpeed(256);s.basin.tick();s.machine.tick();}
            var outputs=s.outputs();check(outputs.size()==1,"Native press/basin tick must execute admitted standard compression");
            var result=outputs.getFirst();var progress=Workpiece.read(helper.getLevel(),result);
            check(result.is(Items.IRON_NUGGET)&&progress!=null&&progress.current().equals("s")&&s.basin.inputInventory.isEmpty(),"Native compression consumes one bound four-ingredient round and grants only graph carrier");
        } finally {config.set(saved);}
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void mixerReferenceAutomationConfigAndActualMachineIdentityRemainAuthoritative(GameTestHelper helper) {
        var config=AllConfigs.server().recipes.allowShapelessInMixer;boolean saved=config.get();
        try(var s=new Session(helper,false)) {
            config.set(true);s.install(new RecipeHolder<>(ResourceLocation.parse("test:mixer_gate"),graph("amethyst_shard",SHAPELESS)));s.inputs();
            var found=s.matching();check(found.size()==1,"Mixer config fixture must cache one native graph recipe");config.set(false);
            check(!BasinRecipe.apply(s.basin,found.getFirst())&&!s.basin.inputInventory.getStackInSlot(0).isEmpty()&&s.outputs().isEmpty(),"Cached mixer recipe cannot bypass changed native config");config.set(true);
            String blocked="{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:mixer_manual_only\"}";
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:normal_graph"),graph("amethyst_shard",blocked)),s.source("test:mixer_manual_only"));
            check(s.matching().isEmpty(),"Original referenced _manual_only ID must retain upstream automation exclusion");
            String allowed="{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:mixer_allowed\"}";
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:graph_manual_only"),graph("amethyst_shard",allowed)),s.source("test:mixer_allowed"));
            found=s.matching();check(found.size()==1,"Allowed original reference ID, not graph ID suffix, determines upstream eligibility");
            var cached=found.getFirst();helper.getLevel().setBlockAndUpdate(s.machinePos,AllBlocks.MECHANICAL_PRESS.getDefaultState());
            check(helper.getLevel().getBlockEntity(s.machinePos) instanceof MechanicalPressBlockEntity,"Fixture must replace actual mixer with wrong machine type");
            var random=helper.getLevel().random;random.setSeed(5317);long expected=random.nextLong();random.setSeed(5317);
            check(!BasinRecipe.match(s.basin,cached)&&!BasinRecipe.apply(s.basin,cached),"Cached recipe cannot authorize a replaced/wrong actual machine");
            check(random.nextLong()==expected&&s.outputs().isEmpty()&&!s.basin.inputInventory.getStackInSlot(0).isEmpty(),"Rejected cached machine identity preserves inputs and graph RNG");
        } finally {config.set(saved);}
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void finalBasinPreparationCallbacksCannotCommitStaleInputDefinitionOrConfig(GameTestHelper helper) {
        var config=AllConfigs.server().recipes.allowShapelessInMixer;boolean savedConfig=config.get();var outcomes=new ArrayList<String>();
        try {
            for(String action:List.of("input","config","reload","reenter","throw"))try(var s=new Session(helper,false)) {
                config.set(true);var data=graph("amethyst_shard",SHAPELESS).json();
                var node=data.getAsJsonArray("nodes").get(0).getAsJsonObject();node.addProperty("finish",true);node.addProperty("finish_mode","strict");data.getAsJsonArray("nodes").remove(1);
                data.add("edges",JsonParser.parseString("[]"));data.add("outputs",JsonParser.parseString("[{\"results\":[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"basin_final\"}}]}]"));
                var graph=new GraphRecipe(data);var id=ResourceLocation.parse("test:basin_final_callback");s.install(new RecipeHolder<>(id,graph));s.inputs();
                var found=s.matching();check(found.size()==1,"Final preparation fixture must cache a real native mixer graph recipe");var cached=found.getFirst();int[] calls={0};
                FurnaceCodecFixture.decode=()->{if(++calls[0]!=2)return;switch(action) {
                    case "input" -> s.basin.inputInventory.setStackInSlot(0,new ItemStack(Items.DIRT));
                    case "config" -> config.set(false);
                    case "reload" -> s.install(new RecipeHolder<>(id,new GraphRecipe(graph.json())));
                    case "reenter" -> BasinRecipe.apply(s.basin,cached);
                    case "throw" -> throw new IllegalStateException("final basin preparation fixture");
                }};
                var random=helper.getLevel().random;random.setSeed(72131);long expected=random.nextLong();random.setSeed(72131);boolean applied=false,threw=false;
                try {applied=BasinRecipe.apply(s.basin,cached);}catch(RuntimeException invalidPreparation){threw=true;}finally{FurnaceCodecFixture.decode=()->{};}
                boolean noRng=random.nextLong()==expected;
                boolean inputPreserved=s.basin.inputInventory.getStackInSlot(0).is(action.equals("input")?Items.DIRT:Items.AMETHYST_SHARD)
                    &&s.basin.inputInventory.getStackInSlot(1).is(Items.COPPER_INGOT)&&s.outputs().isEmpty();
                outcomes.add(action+"="+(calls[0]>=2&&!applied&&!threw&&inputPreserved&&noRng));
                config.set(true);for(int slot=0;slot<s.basin.inputInventory.getSlots();slot++)s.basin.inputInventory.setStackInSlot(slot,ItemStack.EMPTY);
                for(int slot=0;slot<s.basin.getOutputInventory().getSlots();slot++)s.basin.getOutputInventory().setStackInSlot(slot,ItemStack.EMPTY);
                s.install(new RecipeHolder<>(id,new GraphRecipe(graph.json())));s.inputs();var recovery=s.matching();
                check(recovery.size()==1&&BasinRecipe.apply(s.basin,recovery.getFirst())&&s.basin.inputInventory.isEmpty(),"Final basin preparation abort must release synchronous scope: "+action);
            }
        } finally {FurnaceCodecFixture.decode=()->{};config.set(savedConfig);}
        check(outcomes.stream().allMatch(outcome->outcome.endsWith("=true")),"Last basin output-codec preparation must reject stale commit before RNG: "+outcomes);
        helper.succeed();
    }
}
