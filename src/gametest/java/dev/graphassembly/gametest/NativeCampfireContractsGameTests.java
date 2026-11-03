package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCampfireContractsGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:campfire_contract");
    private static String recipe(String ingredient,String result,int time){return """
        {"type":"minecraft:campfire_cooking","ingredient":{"item":"minecraft:%s"},"result":{"id":"minecraft:%s"},"cookingtime":%d}
        """.formatted(ingredient,result,time);}
    private static GraphRecipe graph(int time,String results){var json=JsonParser.parseString("""
        {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
         "nodes":[{"id":"s","start":true,"finish":true,"finish_mode":"strict","recipe":%s}],"edges":[],"outputs":[{"results":%s}]}
        """.formatted(recipe("amethyst_shard","stone",time),results)).getAsJsonObject();
        if(results.equals("[]"))json.add("outputs",JsonParser.parseString("[]"));
        return new GraphRecipe(json);}
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final BlockPos pos;
        CampfireBlockEntity fire;
        Session(GameTestHelper helper){this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());pos=helper.absolutePos(new BlockPos(1,1,1));}
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        void graph(GraphRecipe graph){install(new RecipeHolder<>(ID,graph));}
        RecipeHolder<?> ordinary(String name,String ingredient,String result,int time){return new RecipeHolder<>(ResourceLocation.parse("test:"+name),Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(recipe(ingredient,result,time))).getOrThrow());}
        void machine(Block block){helper.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());fire=(CampfireBlockEntity)helper.getLevel().getBlockEntity(pos);}
        void insert(Item item){var input=new ItemStack(item);var found=fire.getCookableRecipe(input);check(found.isPresent(),"Fixture input must be natively cookable: "+item);check(fire.placeFood(null,input,found.orElseThrow().value().getCookingTime()),"Native insertion must succeed");}
        void tick(int ticks){for(int i=0;i<ticks;i++)CampfireBlockEntity.cookTick(helper.getLevel(),pos,helper.getLevel().getBlockState(pos),fire);}
        List<ItemEntity> drops(){return helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1));}
        int count(Item item){return drops().stream().map(ItemEntity::getItem).filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
        void clearDrops(){drops().forEach(ItemEntity::discard);}
        int progress(){return fire.saveWithoutMetadata(helper.getLevel().registryAccess()).getIntArray("CookingTimes")[0];}
        void reload(net.minecraft.nbt.CompoundTag tag){helper.getLevel().removeBlockEntity(pos);fire=(CampfireBlockEntity)BlockEntity.loadStatic(pos,helper.getLevel().getBlockState(pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(fire);}
        @Override public void close(){FurnaceCodecFixture.decode=()->{};clearDrops();helper.getLevel().removeBlock(pos,false);clearDrops();helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void normalNodeIsNotCookableOnSoulCampfire(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph(4,"[{\"item\":\"minecraft:diamond\"}]").json();
            data.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("campfire","normal");
            s.graph(new GraphRecipe(data));
            var input=new ItemStack(Items.AMETHYST_SHARD);
            s.machine(Blocks.CAMPFIRE);
            check(s.fire.getCookableRecipe(input).isPresent(),"Normal graph node must be cookable on a normal campfire");
            s.machine(Blocks.SOUL_CAMPFIRE);
            check(s.fire.getCookableRecipe(input).isEmpty(),"Normal graph node must not be cookable on a soul campfire");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void entrySelectionRespectsFireKindAndPreservesOrdinaryFallback(GameTestHelper helper) {
        var normal = graph(4,"[{\"item\":\"minecraft:diamond\"}]").json();
        normal.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("campfire","normal");
        var soul = graph(4,"[{\"item\":\"minecraft:emerald\"}]").json();
        var soulNode = soul.getAsJsonArray("nodes").get(0).getAsJsonObject();
        soulNode.addProperty("campfire","soul");
        soulNode.add("recipe",JsonParser.parseString("{\"type\":\"minecraft:campfire_cooking\",\"recipe_id\":\"test:fire_reference\"}"));
        var soulId=ResourceLocation.parse("test:soul_campfire_contract");
        for(var block:List.of(Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE))try(var s=new Session(helper)) {
            var ordinary=s.ordinary("fire_reference","amethyst_shard","stone",2);
            s.install(new RecipeHolder<>(ID,new GraphRecipe(normal)),new RecipeHolder<>(soulId,new GraphRecipe(soul)),ordinary);
            s.machine(block);s.insert(Items.AMETHYST_SHARD);s.tick(4);
            check(s.count(block==Blocks.CAMPFIRE?Items.DIAMOND:Items.EMERALD)==1&&s.count(Items.STONE)==0,
                "Each machine selects its own graph entry ahead of the ordinary collision");
            s.clearDrops();
            s.install(new RecipeHolder<>(block==Blocks.CAMPFIRE?soulId:ID,new GraphRecipe(block==Blocks.CAMPFIRE?soul:normal)),ordinary);
            s.insert(Items.AMETHYST_SHARD);s.tick(2);
            check(s.count(Items.STONE)==1,"A wrong-fire graph entry does not suppress an ordinary recipe");
            s.clearDrops();
            var any=normal.deepCopy();
            any.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("campfire","any");
            s.graph(new GraphRecipe(any));s.insert(Items.AMETHYST_SHARD);s.tick(4);
            check(s.count(Items.DIAMOND)==1,"Explicit any executes on both fires");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void workpieceSuccessorsDisambiguateByFireKindWithoutOrdinaryFallback(GameTestHelper helper) {
        var data=JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":%s},
                      {"id":"n","finish":true,"finish_mode":"strict","campfire":"normal","recipe":%s},
                      {"id":"f","finish":true,"finish_mode":"strict","campfire":"soul","recipe":%s}],
             "edges":[{"from":"s","to":"n"},{"from":"s","to":"f"}],
             "outputs":[{"finish":"n","results":[{"item":"minecraft:diamond"}]},
                        {"finish":"f","results":[{"item":"minecraft:emerald"}]}]}
            """.formatted(recipe("amethyst_shard","stone",2),recipe("iron_nugget","stone",4),recipe("iron_nugget","stone",4))).getAsJsonObject();
        for(var block:List.of(Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE))try(var s=new Session(helper)) {
            var ordinary=s.ordinary("carrier_collision","iron_nugget","stone",1);
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),ordinary);
            s.machine(block);s.insert(Items.AMETHYST_SHARD);s.tick(2);
            check(s.count(Items.IRON_NUGGET)==1,"Start delivers one workpiece");
            var workpiece=s.drops().getFirst().getItem().copy();s.clearDrops();
            var found=s.fire.getCookableRecipe(workpiece).orElseThrow();
            check(s.fire.placeFood(null,workpiece.copy(),found.value().getCookingTime()),"Current fire admits one successor");
            s.tick(2);
            var save=s.fire.saveWithFullMetadata(helper.getLevel().registryAccess());
            s.reload(save);s.tick(2);
            check(s.count(block==Blocks.CAMPFIRE?Items.DIAMOND:Items.EMERALD)==1,"Matching successor resumes after reload");
            s.clearDrops();
            var other=block==Blocks.CAMPFIRE?Blocks.SOUL_CAMPFIRE:Blocks.CAMPFIRE;
            s.machine(other);s.reload(save);s.tick(2);
            check(s.drops().isEmpty()&&s.progress()==2,"A restored job cannot use elapsed progress from a different successor");
            s.tick(2);
            check(s.count(other==Blocks.CAMPFIRE?Items.DIAMOND:Items.EMERALD)==1,"Other fire completes only its own full round");
            s.clearDrops();s.machine(block);
            var changed=data.deepCopy();
            var nodes=changed.getAsJsonArray("nodes");
            var opposite=block==Blocks.CAMPFIRE?"soul":"normal";
            nodes.get(1).getAsJsonObject().addProperty("campfire",opposite);
            nodes.get(2).getAsJsonObject().addProperty("campfire",opposite);
            found=s.fire.getCookableRecipe(workpiece).orElseThrow();
            check(s.fire.placeFood(null,workpiece.copy(),found.value().getCookingTime()),"Workpiece enters before condition reload");
            s.tick(3);
            s.install(new RecipeHolder<>(ID,new GraphRecipe(changed)),ordinary);
            check(s.fire.getCookableRecipe(workpiece).isEmpty(),"Reloaded wrong-fire successors reject protected workpieces");
            s.tick(5);
            check(s.progress()==0&&s.drops().isEmpty()&&Workpiece.has(s.fire.getItems().getFirst()),
                "Wrong fire resets elapsed progress and cannot pay the ordinary collision");
            s.machine(other);
            check(s.fire.getCookableRecipe(workpiece).isEmpty(),"Two successors on the same fire are still ambiguous");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void normalAndSoulFourPositionsMixOrdinaryAndGraphDurations(GameTestHelper helper) {
        for(var block:List.of(Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE))try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ID,graph(4,"[{\"item\":\"minecraft:diamond\"}]")),
                s.ordinary("collision","amethyst_shard","stone",1),s.ordinary("potato","potato","baked_potato",2),s.ordinary("gold","raw_gold","gold_ingot",6));
            s.machine(block);s.insert(Items.AMETHYST_SHARD);s.insert(Items.POTATO);s.insert(Items.AMETHYST_SHARD);s.insert(Items.RAW_GOLD);
            check(s.fire.getCookableRecipe(new ItemStack(Items.AMETHYST_SHARD)).isEmpty(),"Native four-position capacity is retained");
            s.tick(2);check(s.count(Items.BAKED_POTATO)==1&&s.count(Items.DIAMOND)==0&&s.count(Items.STONE)==0,"Ordinary short slot finishes while graph collision keeps its own duration");
            s.tick(2);check(s.count(Items.DIAMOND)==2&&s.count(Items.GOLD_INGOT)==0,"Two positions are independent one-input graph rounds, not merged workpieces");
            s.tick(2);check(s.count(Items.GOLD_INGOT)==1&&s.fire.getItems().stream().allMatch(ItemStack::isEmpty),"Slow ordinary slot completes unchanged on both campfire kinds");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void nativeCooldownAndGraphBundleEmptySettlementSemantics(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph(4,"[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":3}]}]"));
            s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);s.tick(3);
            helper.getLevel().setBlockAndUpdate(s.pos,s.fire.getBlockState().setValue(CampfireBlock.LIT,false));s.tick(5);
            check(s.progress()==3&&s.drops().isEmpty(),"Unlit cookTick cannot execute graph");
            CampfireBlockEntity.cooldownTick(helper.getLevel(),s.pos,s.fire.getBlockState(),s.fire);
            check(s.progress()==1,"Extinguished campfire retains native two-tick cooldown");
            helper.getLevel().setBlockAndUpdate(s.pos,s.fire.getBlockState().setValue(CampfireBlock.LIT,true));s.tick(3);
            check(s.count(Items.DIAMOND)==2&&s.count(Items.EMERALD)==3,"World drops preserve all graph bundles, not furnace one-slot capacity");
        }
        for(boolean strict:List.of(true,false))try(var s=new Session(helper)) {
            var data=graph(2,"[]").json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("finish_mode",strict?"strict":"lenient");
            s.graph(new GraphRecipe(data));s.machine(Blocks.SOUL_CAMPFIRE);s.insert(Items.AMETHYST_SHARD);s.tick(2);
            check(s.fire.getItems().getFirst().isEmpty(),"Empty settlement is still a paid native round");
            if(strict)check(s.drops().isEmpty(),"STRICT empty pool pays round with no placeholder output");
            else check(s.drops().size()==1&&Workpiece.has(s.drops().getFirst().getItem()),"LENIENT empty pool delivers progress-bearing carrier");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void operationIdentityReloadOldSaveAndNativeAdmissionBoundaries(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var g=graph(6,"[{\"item\":\"minecraft:diamond\"}]");s.graph(g);s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);s.tick(3);
            s.fire.getItems().getFirst().set(DataComponents.CUSTOM_NAME,Component.literal("replacement"));s.tick(3);
            check(s.progress()==3&&s.drops().isEmpty(),"Silent input component replacement starts its own full cookingtime");
            s.graph(new GraphRecipe(g.json()));s.tick(3);check(s.progress()==3&&s.drops().isEmpty(),"Live same-ID identical-content definition replacement resets graph progress");
            var tag=s.fire.saveWithFullMetadata(helper.getLevel().registryAccess());s.reload(tag);s.tick(3);
            check(s.count(Items.DIAMOND)==1,"Fresh BE resumes matching persisted identity and native progress");
            s.clearDrops();s.insert(Items.AMETHYST_SHARD);s.tick(3);tag=s.fire.saveWithFullMetadata(helper.getLevel().registryAccess());tag.remove("GraphAssemblyCampfireJobs");s.reload(tag);s.tick(3);
            check(s.drops().isEmpty()&&s.progress()==3,"Old graph input without identity cannot inherit unverified progress");
        }
        try(var s=new Session(helper)) {
            s.install(s.ordinary("ordinary_save","potato","baked_potato",6));s.machine(Blocks.CAMPFIRE);s.insert(Items.POTATO);s.tick(3);
            var tag=s.fire.saveWithFullMetadata(helper.getLevel().registryAccess());tag.remove("GraphAssemblyCampfireJobs");s.reload(tag);s.tick(3);
            check(s.count(Items.BAKED_POTATO)==1,"Old ordinary save retains elapsed native cookingtime");
            s.clearDrops();var protectedInput=Workpiece.write(new ItemStack(Items.POTATO),new Progress("test:missing","s","s",Map.of(),List.of(),Map.of(),null,List.of("s")));
            check(s.fire.getCookableRecipe(protectedInput).isEmpty(),"Missing graph progress cannot enter ordinary recipe");
            s.fire.placeFood(null,protectedInput,1);s.tick(3);check(s.drops().isEmpty()&&!s.fire.getItems().getFirst().isEmpty(),"Even direct native insertion cannot cook protected invalid progress as ordinary");
        }
        try(var s=new Session(helper)) {
            var g=graph(2,"[{\"item\":\"minecraft:diamond\"}]");s.graph(g);s.machine(Blocks.CAMPFIRE);
            var subclass=new CampfireBlockEntity(s.pos,s.fire.getBlockState()){};helper.getLevel().removeBlockEntity(s.pos);helper.getLevel().setBlockEntity(subclass);s.fire=subclass;
            check(subclass.getCookableRecipe(new ItemStack(Items.AMETHYST_SHARD)).isEmpty(),"Unknown BE subclasses do not receive graph authorization");
            helper.getLevel().removeBlockEntity(s.pos);s.fire=new CampfireBlockEntity(s.pos,helper.getLevel().getBlockState(s.pos));helper.getLevel().setBlockEntity(s.fire);
            s.install(new RecipeHolder<>(ID,g),new RecipeHolder<>(ResourceLocation.parse("test:ambiguous"),new GraphRecipe(g.json())),s.ordinary("ambiguous_collision","amethyst_shard","stone",1));
            check(s.fire.getCookableRecipe(new ItemStack(Items.AMETHYST_SHARD)).isEmpty(),"Ambiguous graph entry cannot fall back to ordinary collision");
            var extra=g.json();extra.getAsJsonArray("nodes").get(0).getAsJsonObject().add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":1}]"));s.graph(new GraphRecipe(extra));
            check(s.fire.getCookableRecipe(new ItemStack(Items.AMETHYST_SHARD)).isEmpty()&&GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null,"Campfire has only logical input0, not four workpiece roles");
            var ref=ResourceLocation.parse("test:campfire_reference");var data=g.json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:campfire_cooking\",\"recipe_id\":\"test:campfire_reference\"}"));
            var nativeRecipe=new CampfireCookingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),0,4);
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),new RecipeHolder<>(ref,nativeRecipe));s.insert(Items.AMETHYST_SHARD);s.tick(2);
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),new RecipeHolder<>(ref,new CampfireCookingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),0,6)));s.tick(4);
            check(s.drops().isEmpty()&&s.progress()==4,"Native reference replacement rebinds duration and resets old operation");
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),new RecipeHolder<>(ref,new CampfireCookingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),0,4){}));
            check(GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null,"Referenced recipe subclasses are rejected before codec normalization");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void realCodecCallbacksAbortBeforeRngAndReleasePositionScope(GameTestHelper helper) {
        for(String action:List.of("components","count","reload","remove","replace_reenter","reenter","throw"))try(var s=new Session(helper)) {
            var g=graph(2,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"campfire\"}}]");
            s.graph(g);s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);var original=s.fire;int[] calls={0};
            FurnaceCodecFixture.decode=()->{if(calls[0]++!=0)return;switch(action) {
                case "components" -> original.getItems().getFirst().set(DataComponents.CUSTOM_NAME,Component.literal("callback"));
                case "count" -> original.getItems().getFirst().grow(1);
                case "reload" -> s.graph(new GraphRecipe(g.json()));
                case "remove" -> helper.getLevel().removeBlockEntity(s.pos);
                case "replace_reenter" -> {helper.getLevel().removeBlockEntity(s.pos);var replacement=new CampfireBlockEntity(s.pos,helper.getLevel().getBlockState(s.pos));helper.getLevel().setBlockEntity(replacement);replacement.getItems().set(0,new ItemStack(Items.AMETHYST_SHARD));CampfireBlockEntity.cookTick(helper.getLevel(),s.pos,replacement.getBlockState(),replacement);}
                case "reenter" -> s.tick(1);
                case "throw" -> throw new IllegalStateException("campfire preparation fixture");
            }};
            var random=helper.getLevel().random;random.setSeed(17843);long expected=random.nextLong();random.setSeed(17843);
            try{s.tick(2);}finally{FurnaceCodecFixture.decode=()->{};}
            check(calls[0]>0,"Real component decoder callback must be exercised");
            check(s.drops().isEmpty()&&!original.getItems().getFirst().isEmpty(),"Callback "+action+" must not consume or deliver stale graph round");
            check(random.nextLong()==expected,"Callback "+action+" must abort before graph/drop RNG");
            helper.getLevel().removeBlockEntity(s.pos);s.fire=new CampfireBlockEntity(s.pos,helper.getLevel().getBlockState(s.pos));helper.getLevel().setBlockEntity(s.fire);
            s.graph(graph(2,"[{\"item\":\"minecraft:diamond\"}]"));s.insert(Items.AMETHYST_SHARD);s.tick(2);
            check(s.count(Items.DIAMOND)==1,"Synchronous scope releases after callback abort or exception: "+action);
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void featureDisabledPossibleOutputBlocksBeforeRng(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            check(!new ItemStack(CampfireFeatureFixture.ITEM).isItemEnabled(helper.getLevel().enabledFeatures()),"Fixture output has a feature outside the vanilla feature universe");
            s.graph(graph(2,"[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\"},{\"item\":\"graph_assembly:campfire_disabled_fixture\",\"chance\":0.5}]}]"));s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);
            var random=helper.getLevel().random;random.setSeed(9123);long expected=random.nextLong();random.setSeed(9123);s.tick(4);
            check(random.nextLong()==expected&&s.drops().isEmpty()&&!s.fire.getItems().getFirst().isEmpty(),"Every possible feature-disabled output blocks before RNG and input removal");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void corruptCountCannotPayOneRoundAndLeavingGraphRetainsNativeDuration(GameTestHelper helper) {
        boolean countPreserved,durationPreserved;
        try(var s=new Session(helper)) {
            s.graph(graph(6,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);
            s.fire.getItems().getFirst().grow(1);s.tick(6);
            countPreserved=s.fire.getItems().getFirst().getCount()==2&&s.drops().isEmpty();
        }
        try(var s=new Session(helper)) {
            s.graph(graph(6,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.CAMPFIRE);s.insert(Items.AMETHYST_SHARD);s.tick(3);
            s.install();s.tick(1);
            durationPreserved=s.fire.saveWithoutMetadata(helper.getLevel().registryAccess()).getIntArray("CookingTotalTimes")[0]==6&&s.progress()==1;
        }
        check(countPreserved&&durationPreserved,"One-item count preserved="+countPreserved+"; leaving graph retains native duration="+durationPreserved);
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void supportedCampfireStepHasNativeDescriptionAndMachineIcon(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var recipe=s.ordinary("campfire_presentation","amethyst_shard","stone",4).value();
            var description=dev.graphassembly.client.StepDescription.describe(recipe);
            var player=dev.graphassembly.client.StepDescription.player(recipe);
            boolean described=description.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                &&text.getKey().equals("block.minecraft.campfire");
            boolean playerDescribed=player.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                &&text.getKey().equals("block.minecraft.campfire");
            boolean icon=dev.graphassembly.client.StepDescription.machine(recipe).is(Items.CAMPFIRE);
            check(described&&playerDescribed&&icon,"Campfire native description="+described+"; player description="+playerDescribed+"; native icon="+icon);
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_campfire")
    public static void graphCampfirePresentationDistinguishesSoulAndAny(GameTestHelper helper) {
        var data=graph(4,"[]").json();
        var node=data.getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.addProperty("campfire","soul");
        var soul=new GraphRecipe(data).definition(ID.toString()).nodes().get("s");
        node.remove("campfire");
        var any=new GraphRecipe(data).definition(ID.toString()).nodes().get("s");
        check(dev.graphassembly.client.StepDescription.campfireMachine(soul).is(Items.SOUL_CAMPFIRE),
            "Soul-only node has a soul campfire icon");
        var soulText=dev.graphassembly.client.StepDescription.describeCampfire(soul).getContents();
        var anyText=dev.graphassembly.client.StepDescription.describeCampfire(any).getContents();
        check(soulText instanceof net.minecraft.network.chat.contents.TranslatableContents text
            &&text.getKey().equals("block.minecraft.soul_campfire"),"Soul-only node uses the native soul campfire name");
        check(anyText instanceof net.minecraft.network.chat.contents.TranslatableContents text
            &&text.getKey().equals("graph_assembly.campfire.any")&&text.getArgs().length==2,"Any node names both fire kinds");
        helper.succeed();
    }
}
