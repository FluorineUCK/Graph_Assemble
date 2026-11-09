package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.graphassembly.GraphAssembly;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSequenceGameTests {
    public static final String FURNACE_SEQUENCE="""
        {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:iron_ingot"},
         "transitional_item":{"id":"minecraft:iron_nugget"},"loops":2,
         "sequence":[{"type":"graph_assembly:sequence_step","step":{"recipe":{"type":"minecraft:smelting","ingredient":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:iron_nugget"},"cookingtime":2,"experience":0.0}}},
                     {"type":"create:pressing","ingredients":[{"item":"minecraft:iron_nugget"}],"results":[{"id":"minecraft:iron_nugget"}]}],
         "results":[{"id":"minecraft:diamond"}]}
        """;
    @GameTest(template="test_empty",batch="native_sequence")
    public static void supportedNativeStepRegistersInsideRealCreateSequence(GameTestHelper helper) {
        var ops=helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var recipe=Recipe.CODEC.parse(ops,JsonParser.parseString(FURNACE_SEQUENCE)).getOrThrow();
        if(!(recipe instanceof SequencedAssemblyRecipe sequence)||sequence.getLoops()!=2||sequence.getSequence().size()!=2)
            throw new AssertionError("Must remain a genuine Create sequence with native loops");
        if(!sequence.getSequence().getFirst().getAsAssemblyRecipe().supportsAssembly())throw new AssertionError("Step must support native assembly");
        helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_sequence")
    public static void furnaceAndNativePressShareCreateLoopsAndProgress(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=java.util.List.copyOf(manager.getRecipes());
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        try {
            var recipe=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(FURNACE_SEQUENCE)).getOrThrow();
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("test:native_furnace"),recipe)));
            level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)level.getBlockEntity(pos);
            furnace.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
            var input=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
            for(int loop=0;loop<2;loop++) {
                furnace.setItem(0,input);furnace.setItem(2,net.minecraft.world.item.ItemStack.EMPTY);
                for(int tick=0;tick<4;tick++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
                var heated=furnace.getItem(2).copy();
                if(heated.isEmpty())throw new AssertionError("Real furnace must execute the native sequence step");
                var progress=heated.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
                if(progress==null||progress.step()!=loop*2+1||dev.graphassembly.recipe.Workpiece.has(heated))throw new AssertionError("Only Create progress may own the sequence");
                var press=SequencedAssemblyRecipe.getRecipe(level,heated,com.simibubi.create.AllRecipeTypes.PRESSING.getType(),com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow();
                input=press.value().rollResults(level.random).getFirst();
            }
            if(!input.is(net.minecraft.world.item.Items.DIAMOND)||input.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY))throw new AssertionError("Create must settle after the second full loop");
            if(!furnace.getItem(0).isEmpty())throw new AssertionError("Each furnace round consumes exactly its primary input");
        } finally {level.removeBlock(pos,false);manager.replaceRecipes(saved);}
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="native_sequence")
    public static void sameParentCannotAuthorizeAReplacedNativeReference(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=java.util.List.copyOf(manager.getRecipes());
        try {
            var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var json=JsonParser.parseString(FURNACE_SEQUENCE).getAsJsonObject();
            var reference=JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"recipe_id\":\"test:sequence_heat\"}");
            json.getAsJsonArray("sequence").get(0).getAsJsonObject().getAsJsonObject("step").add("recipe",reference);
            var parent=new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("test:sequence_reference"),Recipe.CODEC.parse(ops,json).getOrThrow());
            var nativeJson=JsonParser.parseString(FURNACE_SEQUENCE).getAsJsonObject().getAsJsonArray("sequence").get(0).getAsJsonObject().getAsJsonObject("step").get("recipe");
            var source=Recipe.CODEC.parse(ops,nativeJson).getOrThrow();var id=net.minecraft.resources.ResourceLocation.parse("test:sequence_heat");
            manager.replaceRecipes(java.util.List.of(parent,new net.minecraft.world.item.crafting.RecipeHolder<>(id,source)));
            var operation=dev.graphassembly.integration.vanilla.furnace.CookingLookup.select(level,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT),net.minecraft.world.item.crafting.RecipeType.SMELTING).operation();
            if(operation==null||!operation.current(level))throw new AssertionError("Reference fixture must bind before replacement");
            manager.replaceRecipes(java.util.List.of(parent,new net.minecraft.world.item.crafting.RecipeHolder<>(id,source)));
            if(operation.current(level))throw new AssertionError("Replacing a referenced holder must invalidate the old native operation even with the same parent");
        } finally {manager.replaceRecipes(saved);}
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="native_sequence")
    public static void nativeSequenceJsonAndWireRetainInitializedInputs(GameTestHelper helper) {
        var ops=helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var recipe=Recipe.CODEC.parse(ops,JsonParser.parseString(FURNACE_SEQUENCE)).getOrThrow();
        var encoded=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow();
        var roundTrip=(SequencedAssemblyRecipe)Recipe.CODEC.parse(ops,encoded).getOrThrow();
        var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(bytes,roundTrip);
            var received=(SequencedAssemblyRecipe)Recipe.STREAM_CODEC.decode(bytes);
            var input=received.getSequence().getFirst().getRecipe().getIngredients().getFirst();
            if(!input.test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT))
                ||!input.test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET)))throw new AssertionError("Network must retain parent-initialized initial/transitional alternatives");
            var step=(dev.graphassembly.integration.sequence.SequenceStepRecipe)received.getSequence().getFirst().getRecipe();
            if(step.machine()!=net.minecraft.world.level.block.Blocks.FURNACE||received.getLoops()!=2)throw new AssertionError("Native metadata and target must survive transport");
            var machines=new java.util.HashSet<net.minecraft.world.level.ItemLike>();step.addRequiredMachines(machines);
            if(!machines.contains(net.minecraft.world.level.block.Blocks.FURNACE))throw new AssertionError("Common machine hint must work without JEI installed");
            var changed=step.step();changed.remove("recipe");if(!step.step().has("recipe"))throw new AssertionError("Definitions cannot be mutated through accessors");
        } finally {bytes.release();}
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="native_sequence")
    public static void sequencePreparationKeepsComponentsAndNativeRandomConsumption(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=java.util.List.copyOf(manager.getRecipes());
        try {
            var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var json=JsonParser.parseString(FURNACE_SEQUENCE).getAsJsonObject();json.addProperty("loops",1);json.getAsJsonArray("sequence").remove(1);
            var result=json.getAsJsonArray("results").get(0).getAsJsonObject();result.addProperty("count",2);
            var components=new com.google.gson.JsonObject();components.addProperty("minecraft:custom_name","\"native result\"");result.add("components",components);
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("test:sequence_components"),Recipe.CODEC.parse(ops,json).getOrThrow())));
            var operation=dev.graphassembly.integration.vanilla.furnace.CookingLookup.select(level,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT),net.minecraft.world.item.crafting.RecipeType.SMELTING).operation();
            var prepared=dev.graphassembly.integration.OperationCompletion.prepare(level,operation);
            level.random.setSeed(182);var expected=level.random.nextLong();level.random.setSeed(182);
            if(prepared.fitsSingleItem(level)||!prepared.allMaximalOutcomesFit(items->items.size()==1&&items.getFirst().getCount()==2))throw new AssertionError("Native weighted output count must not be treated as a chance quantity");
            prepared.preview().setCount(1);if(prepared.preview().getCount()!=2||level.random.nextLong()!=expected)throw new AssertionError("Preview must be isolated and RNG-free");
            var random=net.minecraft.util.RandomSource.create(513);var control=net.minecraft.util.RandomSource.create(513);control.nextFloat();var expectedAfter=control.nextLong();
            var output=prepared.roll(random).getFirst();
            if(random.nextLong()!=expectedAfter||output.getCount()!=2||!output.getHoverName().getString().equals("native result")
                ||output.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY)||dev.graphassembly.recipe.Workpiece.has(output))throw new AssertionError("Native settlement must preserve components and exactly Create's weighted draw");
        } finally {manager.replaceRecipes(saved);}
        helper.succeed();
    }


    @GameTest(template="test_empty",batch="native_sequence")
    public static void fullFurnaceOutputDoesNotPayOrRollAndCarrierSurvivesSave(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=java.util.List.copyOf(manager.getRecipes());
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        try {
            var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var recipe=Recipe.CODEC.parse(ops,JsonParser.parseString(FURNACE_SEQUENCE)).getOrThrow();
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("test:native_furnace_capacity"),recipe)));
            level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)level.getBlockEntity(pos);
            furnace.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            furnace.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
            furnace.setItem(2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
            level.random.setSeed(329);var expected=level.random.nextLong();level.random.setSeed(329);
            for(int i=0;i<4;i++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
            if(furnace.getItem(0).getCount()!=1||furnace.getItem(1).getCount()!=1||furnace.getItem(2).getCount()!=64||level.random.nextLong()!=expected)
                throw new AssertionError("Blocked native sequence must not pay input/fuel or roll outputs");
            furnace.setItem(2,net.minecraft.world.item.ItemStack.EMPTY);
            for(int i=0;i<4;i++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
            var carrier=furnace.getItem(2).copy();
            var restored=net.minecraft.world.item.ItemStack.parse(level.registryAccess(),carrier.save(level.registryAccess())).orElseThrow();
            if(!net.minecraft.world.item.ItemStack.matches(carrier,restored)||dev.graphassembly.recipe.Workpiece.has(restored))throw new AssertionError("Native carrier component must survive item NBT roundtrip without graph progress");
            // A valid carrier waiting for the native press is not allowed back into the furnace.
            furnace.setItem(0,restored);furnace.setItem(2,net.minecraft.world.item.ItemStack.EMPTY);
            for(int i=0;i<4;i++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
            if(!net.minecraft.world.item.ItemStack.matches(furnace.getItem(0),restored)||!furnace.getItem(2).isEmpty())throw new AssertionError("Wrong native phase must not be consumed");
            // Recipe reload rebuilds transient views; a serialized carrier still resumes via Create.
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("test:native_furnace_capacity"),Recipe.CODEC.parse(ops,JsonParser.parseString(FURNACE_SEQUENCE)).getOrThrow())));
            var press=SequencedAssemblyRecipe.getRecipe(level,restored,com.simibubi.create.AllRecipeTypes.PRESSING.getType(),com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow();
            var next=press.value().rollResults(level.random).getFirst();
            if(next.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY).step()!=2)throw new AssertionError("Reloaded native parent must resume next loop");
        } finally {level.removeBlock(pos,false);manager.replaceRecipes(saved);}
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="native_sequence")
    public static void wrappersRejectUnknownRecursiveAndGraphOnlyFields(GameTestHelper helper) {
        var ops=helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var type:java.util.List.of("addon:unknown","create:sequenced_assembly","graph_assembly:sequence_step","create:conversion")) {
            var json=JsonParser.parseString(FURNACE_SEQUENCE).getAsJsonObject();
            json.getAsJsonArray("sequence").get(0).getAsJsonObject().getAsJsonObject("step").getAsJsonObject("recipe").addProperty("type",type);
            if(Recipe.CODEC.parse(ops,json).result().isPresent())throw new AssertionError("Unverified/recursive step accepted: "+type);
        }
        var json=JsonParser.parseString(FURNACE_SEQUENCE).getAsJsonObject();
        json.getAsJsonArray("sequence").get(0).getAsJsonObject().getAsJsonObject("step").addProperty("checkpoint",true);
        if(Recipe.CODEC.parse(ops,json).result().isPresent())throw new AssertionError("Graph-only semantics cannot silently enter a native sequence");
        helper.succeed();
    }

}
