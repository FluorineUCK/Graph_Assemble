package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.fan.processing.*;
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
public final class NativeFanBridgeContractsGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:fan_contract");
    private static String cooking(String type,String input,String result){return "{\"type\":\"minecraft:"+type+"\",\"ingredient\":{\"item\":\"minecraft:"+input+"\"},\"result\":{\"id\":\"minecraft:"+result+"\"},\"cookingtime\":2}";}
    private static GraphRecipe graph(boolean staged,String outputs) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,%s"recipe":%s}%s],"edges":%s,"outputs":[{"results":%s}]}
            """.formatted(staged?"":"\"finish\":true,\"finish_mode\":\"strict\",",cooking("smoking","amethyst_shard","stone"),
                staged?",{\"id\":\"f\",\"finish\":true,\"finish_mode\":\"strict\",\"recipe\":"+cooking("smoking","iron_nugget","stone")+"}":"",
                staged?"[{\"from\":\"s\",\"to\":\"f\"}]":"[]",outputs)).getAsJsonObject());
    }
    private static final String BUNDLE="[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\"}]}]";
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final BlockPos pos;final AABB bounds;
        final FanProcessingType smoke=AllFanProcessingTypes.SMOKING;final int duration=AllConfigs.server().kinetics.fanProcessingTime.get();
        Session(GameTestHelper helper){this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());pos=helper.absolutePos(new BlockPos(1,1,1));bounds=new AABB(pos).inflate(2);}
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        RecipeHolder<?> ordinary(String id,String type,String input,String output){return new RecipeHolder<>(ResourceLocation.parse(id),Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(cooking(type,input,output))).getOrThrow());}
        ItemEntity entity(ItemStack input){var entity=new ItemEntity(helper.getLevel(),pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,input);helper.getLevel().addFreshEntity(entity);return entity;}
        void drive(ItemEntity entity,FanProcessingType type,int ticks){for(int tick=0;tick<ticks;tick++)if(entity.isAlive()&&FanProcessing.canProcess(entity,type))FanProcessing.applyProcessing(entity,type);}
        TransportedResult finish(TransportedItemStack input,int ticks){TransportedResult result=TransportedResult.doNothing();for(int tick=0;tick<ticks;tick++){result=FanProcessing.applyProcessing(input,helper.getLevel(),smoke);if(!result.doesNothing())break;}return result;}
        List<ItemEntity> entities(){return helper.getLevel().getEntitiesOfClass(ItemEntity.class,bounds);}
        int count(Item item){return entities().stream().map(ItemEntity::getItem).filter(stack->stack.is(item)).mapToInt(ItemStack::getCount).sum();}
        @Override public void close(){FurnaceCodecFixture.decode=()->{};entities().forEach(ItemEntity::discard);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void transportedBulkGroupedTimeKeepsOutputLocksUntilLiftAndReinsert(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ID,graph(true,BUNDLE)),s.ordinary("test:diamond_smoke","smoking","diamond","coal"));
            var input=new TransportedItemStack(new ItemStack(Items.AMETHYST_SHARD,17));
            check(FanProcessing.applyProcessing(input,helper.getLevel(),s.smoke).doesNothing()&&input.processingTime==s.duration*2+1,"Native transported fan timer groups17 inputs into two groups, not cookingtime2");
            for(int tick=0;tick<s.duration+1;tick++)check(FanProcessing.applyProcessing(input,helper.getLevel(),s.smoke).doesNothing(),"Bulk fan output must not appear after only one group duration");
            var result=s.finish(input,s.duration+8);check(!result.doesNothing()&&result.getOutputs().size()==17,"Every one of17 raw units must receive an independent graph carrier");
            for(var output:result.getOutputs()) {
                check(output.stack.getCount()==1&&Workpiece.read(helper.getLevel(),output.stack).current().equals("s")&&output.processedBy==s.smoke&&output.processingTime==-1,"Native getSimilar must preserve completed exposure on every independent carrier");
                for(int tick=0;tick<s.duration+4;tick++)check(FanProcessing.applyProcessing(output,helper.getLevel(),s.smoke).doesNothing(),"Continuous exposure cannot automatically chain another smoking node");
            }
            var lifted=new TransportedItemStack(result.getOutputs().getFirst().stack.copy());
            var settled=s.finish(lifted,s.duration+8);check(!settled.doesNothing(),"Lifting and reinserting into a new transported round can execute next smoking node");
            int diamonds=0,emeralds=0;for(var output:settled.getOutputs()){if(output.stack.is(Items.DIAMOND))diamonds+=output.stack.getCount();if(output.stack.is(Items.EMERALD))emeralds+=output.stack.getCount();}
            check(diamonds==2&&emeralds==1,"One transported finishing round grants every graph result and no native stone");
            for(var output:settled.getOutputs())for(int tick=0;tick<s.duration+4;tick++)
                check(FanProcessing.applyProcessing(output,helper.getLevel(),s.smoke).doesNothing()&&output.processingTime==-1,"Settled graph result keeps native exposure lock even if an ordinary next recipe exists");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void entityBulkSettlementPaysEveryUnitAndPreservesNativeCompletedLocks(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ID,graph(false,BUNDLE)));
            var entity=s.entity(new ItemStack(Items.AMETHYST_SHARD,17));s.drive(entity,s.smoke,s.duration+4);
            check(entity.getItem().is(Items.AMETHYST_SHARD)&&entity.getItem().getCount()==17&&s.entities().size()==1,"Native entity fan countdown must honor two stack-size groups before bulk settlement");
            s.drive(entity,s.smoke,s.duration+4);
            check(s.count(Items.DIAMOND)==34&&s.count(Items.EMERALD)==17&&s.count(Items.AMETHYST_SHARD)==0&&s.count(Items.STONE)==0,"Native world delivery must pay17 complete graph bundles, not replace whole stack with one round");
            for(var output:s.entities())check(output.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==-1,"All native entity graph results finish exposure-locked, including original terminal-result entity");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void savedTransportedCompletionCannotLockReplacementBeforeFirstFanCall(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ID,graph(true,BUNDLE)));
            var finished=s.finish(new TransportedItemStack(new ItemStack(Items.AMETHYST_SHARD)),s.duration+8);
            check(!finished.doesNothing()&&finished.getOutputs().size()==1,"Fixture must naturally produce one native exposure-locked transported carrier");
            var tag=finished.getOutputs().getFirst().serializeNBT(helper.getLevel().registryAccess());
            var unchanged=TransportedItemStack.read(tag.copy(),helper.getLevel().registryAccess());
            check(FanProcessing.applyProcessing(unchanged,helper.getLevel(),s.smoke).doesNothing()&&unchanged.processingTime==-1,"Native restored unchanged carrier must retain completed exposure");
            var replaced=TransportedItemStack.read(tag.copy(),helper.getLevel().registryAccess());
            check(replaced.processingTime==-1&&Workpiece.has(replaced.stack),"Mirror fixture must restore native completed timer and carrier without entering fan first");
            replaced.stack=new ItemStack(Items.AMETHYST_SHARD);
            var first=FanProcessing.applyProcessing(replaced,helper.getLevel(),s.smoke);var second=FanProcessing.applyProcessing(replaced,helper.getLevel(),s.smoke);
            check(first.doesNothing()&&second.doesNothing()&&replaced.processingTime>=s.duration&&replaced.stack.is(Items.AMETHYST_SHARD),
                "Restored transported completion must bind loaded output identity before first fan call, not lock substituted raw input forever");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void nativeEntityMergeKeepsCompletedLockButActiveCountChangeRestarts(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ID,graph(false,BUNDLE)),s.ordinary("test:merged_diamond_smoke","smoking","diamond","coal"));
            var input=s.entity(new ItemStack(Items.AMETHYST_SHARD,2));s.drive(input,s.smoke,s.duration+4);
            int before=s.entities().size();check(before==4,"Native merge fixture must start with four separate result entities");
            for(var output:s.entities()){output.setNoGravity(true);output.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
            for(int tick=0;tick<45;tick++)for(var output:new ArrayList<>(s.entities()))if(output.isAlive())output.tick();
            check(s.entities().size()<before&&s.count(Items.DIAMOND)==4&&s.count(Items.EMERALD)==2,"Actual native ItemEntity ticks must merge equal-component results without changing counts");
            for(var output:s.entities())if(output.getItem().is(Items.DIAMOND))
                check(output.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==-1&&!FanProcessing.canProcess(output,s.smoke),"Native count-only merge of completed same-component output cannot reopen ordinary smoking");
            s.entities().forEach(ItemEntity::discard);s.install(new RecipeHolder<>(ID,graph(true,BUNDLE)));
            var active=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(active,s.smoke,s.duration);
            check(active.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==1,"Active count fixture must reach final pre-completion tick");
            active.getItem().grow(16);if(FanProcessing.canProcess(active,s.smoke))FanProcessing.applyProcessing(active,s.smoke);
            check(active.getItem().is(Items.AMETHYST_SHARD)&&active.getItem().getCount()==17
                &&active.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==s.duration*2,"Active processing count changes still restart full native grouped duration");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void installedFoodSourcesProtectGraphAndKeepOrdinaryBurnAndSmeltingPriority(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph(true,BUNDLE).json();data.getAsJsonArray("nodes").get(1).getAsJsonObject().add("recipe",JsonParser.parseString(cooking("smelting","iron_nugget","cooked_porkchop")));
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),s.ordinary("test:food_smoking","smoking","iron_nugget","cooked_porkchop"),s.ordinary("test:food_smelting","smelting","iron_nugget","cooked_porkchop"));
            GraphIndex.get(helper.getLevel()); // Real installed/indexed definitions, no artificial ordinary marking.
            var first=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(first,s.smoke,s.duration+4);
            check(Workpiece.has(first.getItem()),"Food fixture must obtain a real graph carrier through native smoking");
            var carrier=first.getItem().copy();first.discard();var protectedEntity=s.entity(carrier.copy());var before=protectedEntity.getPersistentData().copy();
            s.drive(protectedEntity,AllFanProcessingTypes.BLASTING,s.duration+4);
            check(protectedEntity.isAlive()&&ItemStack.matches(carrier,protectedEntity.getItem())&&before.equals(protectedEntity.getPersistentData()),"Same original smoking/smelting result rejects graph blasting and preserves progressing item/countdown");
            var rawFood=s.entity(new ItemStack(Items.IRON_NUGGET));s.drive(rawFood,AllFanProcessingTypes.BLASTING,s.duration+4);
            check(rawFood.isRemoved(),"Ordinary same-result food still burns through unmodified native blasting");
            s.entities().forEach(ItemEntity::discard);
            var blast=graph(false,BUNDLE).json();blast.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString(cooking("blasting","amethyst_shard","stone")));
            s.install(new RecipeHolder<>(ID,new GraphRecipe(blast)),s.ordinary("test:smelting_priority","smelting","amethyst_shard","gold_ingot"));
            var priority=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(priority,AllFanProcessingTypes.BLASTING,s.duration+4);
            check(priority.isAlive()&&priority.getItem().is(Items.GOLD_INGOT)&&!Workpiece.has(priority.getItem()),"Ordinary automatic smelting precedes graph blasting fallback");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void fanOriginalReferenceAutomationAndCampfireExclusionStayNative(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            for(boolean blocked:List.of(true,false)) {
                String source=blocked?"test:fan_manual_only":"test:fan_allowed";
                var data=graph(true,BUNDLE).json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smoking\",\"recipe_id\":\""+source+"\"}"));
                var graphId=ResourceLocation.parse(blocked?"test:normal_fan_graph":"test:graph_manual_only");
                s.install(new RecipeHolder<>(graphId,new GraphRecipe(data)),s.ordinary(source,"smoking","amethyst_shard","stone"));
                var entity=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(entity,s.smoke,s.duration+4);
                if(blocked)check(entity.getItem().is(Items.AMETHYST_SHARD)&&!Workpiece.has(entity.getItem()),"Original _manual_only reference cannot gain fan automation through normal graph ID");
                else check(Workpiece.has(entity.getItem())&&Workpiece.read(helper.getLevel(),entity.getItem()).graphId().equals(graphId.toString()),"Allowed original ID, not containing graph ID suffix, controls native fan automation");
                entity.discard();
            }
            var campfire=graph(true,BUNDLE).json();for(var node:campfire.getAsJsonArray("nodes"))node.getAsJsonObject().getAsJsonObject("recipe").addProperty("type","minecraft:campfire_cooking");
            s.install(new RecipeHolder<>(ID,new GraphRecipe(campfire)));
            helper.getLevel().setBlockAndUpdate(s.pos,net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
            try {
                var fire=(net.minecraft.world.level.block.entity.CampfireBlockEntity)helper.getLevel().getBlockEntity(s.pos);var raw=new ItemStack(Items.AMETHYST_SHARD);
                var cookable=fire.getCookableRecipe(raw);check(cookable.isPresent()&&fire.placeFood(null,raw,cookable.orElseThrow().value().getCookingTime()),"Campfire fixture must use its actual supported native insertion");
                for(int tick=0;tick<2;tick++)net.minecraft.world.level.block.entity.CampfireBlockEntity.cookTick(helper.getLevel(),s.pos,fire.getBlockState(),fire);
                var carrier=s.entities().getFirst();check(Workpiece.has(carrier.getItem()),"Campfire fixture must produce real progress");
                var before=carrier.getPersistentData().copy();
                check(!FanProcessing.canProcess(carrier,s.smoke)&&!FanProcessing.canProcess(carrier,AllFanProcessingTypes.BLASTING)&&before.equals(carrier.getPersistentData()),"campfire_cooking is not a fan source and wrong-medium progress countdown stays untouched");
            } finally {helper.getLevel().removeBlock(s.pos,false);}
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void realOutputCodecMutationReloadReentryAndThrowAbortBeforeGraphRng(GameTestHelper helper) {
        for(String action:List.of("components","reload","reenter","throw"))try(var s=new Session(helper)) {
            var graph=graph(false,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"fan\"}}]");
            s.install(new RecipeHolder<>(ID,graph));var entity=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(entity,s.smoke,s.duration);
            check(entity.getPersistentData().getCompound("CreateData").getCompound("Processing").getInt("Time")==1,"Codec fixture must arm only at final native pre-completion tick");
            int[] calls={0};FurnaceCodecFixture.decode=()->{if(calls[0]++!=0)return;switch(action) {
                case "components" -> entity.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("callback changed"));
                case "reload" -> s.install(new RecipeHolder<>(ID,new GraphRecipe(graph.json())));
                case "reenter" -> FanProcessing.applyProcessing(entity,s.smoke);
                case "throw" -> throw new IllegalStateException("fan output fixture");
            }};
            var random=helper.getLevel().random;random.setSeed(19451);long expected=random.nextLong();random.setSeed(19451);
            try {if(FanProcessing.canProcess(entity,s.smoke))FanProcessing.applyProcessing(entity,s.smoke);}finally{FurnaceCodecFixture.decode=()->{};}
            check(calls[0]>0&&entity.isAlive()&&entity.getItem().is(Items.AMETHYST_SHARD)&&!Workpiece.has(entity.getItem())&&s.entities().size()==1,"Real codec "+action+" cannot pay or drop a stale graph round");
            check(random.nextLong()==expected,"Real codec "+action+" must abort before graph RNG");
            entity.setItem(new ItemStack(Items.AMETHYST_SHARD));s.install(new RecipeHolder<>(ID,graph(false,"[{\"item\":\"minecraft:diamond\"}]")));s.drive(entity,s.smoke,s.duration+4);
            check(entity.getItem().is(Items.DIAMOND),"Same actor processes a fresh round after invalid preparation scope releases: "+action);
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void blastingFoodSelectsNativeSmokingBeforeAutomationFilter(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph(true,BUNDLE).json();data.getAsJsonArray("nodes").get(1).getAsJsonObject().add("recipe",JsonParser.parseString(cooking("smelting","iron_nugget","cooked_porkchop")));
            var manual=s.ordinary("test:first_food_manual_only","smoking","iron_nugget","cooked_porkchop");
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),manual,s.ordinary("test:second_food_allowed","smoking","iron_nugget","cooked_porkchop"));
            var nativeSelected=helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMOKING,new SingleRecipeInput(new ItemStack(Items.IRON_NUGGET)),helper.getLevel());
            check(nativeSelected.isPresent()&&nativeSelected.get().id().equals(manual.id())
                &&nativeSelected.filter(com.simibubi.create.AllRecipeTypes.CAN_BE_AUTOMATED).isEmpty(),"Fixture must prove actual native first smoking match is manual-only, not a filter-first fallback");
            GraphIndex.get(helper.getLevel());var first=s.entity(new ItemStack(Items.AMETHYST_SHARD));s.drive(first,s.smoke,s.duration+4);
            check(Workpiece.has(first.getItem()),"Food-order fixture must obtain real carrier through native smoking");var carrier=first.getItem().copy();first.discard();
            var entity=s.entity(carrier);s.drive(entity,AllFanProcessingTypes.BLASTING,s.duration+4);
            check(s.count(Items.DIAMOND)==2&&s.count(Items.EMERALD)==1&&s.count(Items.IRON_NUGGET)==0,
                "Blasting food qualification must select first native smoking then filter automation, not skip manual-only to matching-result fallback");
        }helper.succeed();
    }
}
