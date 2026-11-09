package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.GraphPreviewRecipes;
import dev.graphassembly.integration.PreviewRecipeData;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Real-menu tracer and bounded native-trim contracts; no new dynamic device family. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeTrimGameTests {
    static final ResourceLocation ID=ResourceLocation.parse("test:trim_contract");
    static GraphRecipe graph(String outputs,boolean finish) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:diamond_chestplate"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"trim","start":true,%s"recipe":{"type":"minecraft:smithing_trim",
              "template":{"item":"minecraft:coast_armor_trim_smithing_template"},"base":{"item":"minecraft:diamond_chestplate"},"addition":{"item":"minecraft:amethyst_shard"}}}%s],
             "edges":%s,"outputs":%s}
            """.formatted(finish?"\"finish\":true,\"finish_mode\":\"strict\",":"",
                finish?"":",{\"id\":\"finish\",\"finish\":true,\"recipe\":{\"type\":\"minecraft:smithing_trim\",\"template\":{\"item\":\"minecraft:coast_armor_trim_smithing_template\"},\"base\":{\"item\":\"minecraft:diamond_chestplate\"},\"addition\":{\"item\":\"minecraft:quartz\"}}}",
                finish?"[]":"[{\"from\":\"trim\",\"to\":\"finish\"}]",outputs.equals("[]")?"[]":"[{\"results\":"+outputs+"}]")).getAsJsonObject());
    }
    static GraphRecipe fixtureGraph(String outputs,boolean finish) {
        var json=graph(outputs,finish).json();json.add("ingredient",JsonParser.parseString("{\"item\":\"graph_assembly:trim_native_fixture\"}"));
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("base",JsonParser.parseString("{\"type\":\"graph_assembly:trim_native_fixture\",\"ingredient\":{\"item\":\"graph_assembly:trim_native_fixture\"}}"));
        return new GraphRecipe(json);
    }
    static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final BlockPos pos;
        final net.neoforged.neoforge.common.util.FakePlayer player;final SmithingMenu menu;
        Session(GameTestHelper helper,GraphRecipe graph,RecipeHolder<?>... extras) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());install(graph,extras);
            pos=helper.absolutePos(new BlockPos(1,1,1));helper.getLevel().setBlockAndUpdate(pos,Blocks.SMITHING_TABLE.defaultBlockState());
            player=new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"trim-contract"));
            player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);player.getInventory().clearContent();
            menu=new SmithingMenu(3,player.getInventory(),ContainerLevelAccess.create(helper.getLevel(),pos));player.containerMenu=menu;
        }
        void install(GraphRecipe graph,RecipeHolder<?>... extras){var holders=new ArrayList<RecipeHolder<?>>();holders.add(new RecipeHolder<>(ID,graph));holders.addAll(List.of(extras));helper.getLevel().getRecipeManager().replaceRecipes(holders);GraphIndex.invalidate();}
        void fill(ItemStack base,int count){menu.getSlot(0).set(new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,count));menu.getSlot(1).set(base.copyWithCount(count));menu.getSlot(2).set(new ItemStack(Items.AMETHYST_SHARD,count));}
        void click(ClickType type,int button){menu.clicked(3,button,type,player);}
        boolean counts(int count){for(int slot=0;slot<3;slot++)if(menu.getSlot(slot).getItem().getCount()!=count)return false;return true;}
        void reset(){for(int slot=0;slot<3;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();}
        public void close(){TrimNativeFixture.reset();FurnaceCodecFixture.decode=()->{};for(int slot=0;slot<4;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;helper.getLevel().removeBlock(pos,false);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void trimThenOrdinaryNodePreviewNeverInventsGraphDefaultCarrier(GameTestHelper helper) {
        var json=graph("[{\"item\":\"minecraft:diamond\"}]",false).json();
        json.getAsJsonArray("nodes").get(1).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"create:pressing\"}"));
        try(var s=new Session(helper,new GraphRecipe(json))) {
            var definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            var first=(SmithingRecipe)GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("trim")).value();
            check(first.isBaseIngredient(new ItemStack(Items.DIAMOND_CHESTPLATE))&&!first.isBaseIngredient(new ItemStack(Items.IRON_NUGGET)),"Trim native viewer keeps its real armor candidates");
            boolean valid=false;
            try {
                var next=GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("finish")).value();
                valid=!next.getIngredients().isEmpty()&&next.getIngredients().getFirst().test(new ItemStack(Items.DIAMOND_CHESTPLATE))
                    &&!next.getIngredients().getFirst().test(new ItemStack(Items.IRON_NUGGET));
            }catch(PreviewRecipeData.UnknownDynamicCarrier unknown){valid=true;}
            check(valid,"A trim-to-ordinary viewer must keep native armor candidates or explicitly mark dynamic carrier unknown, never fabricate graph-default iron nuggets");
            var mixed=json.deepCopy();mixed.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"raw\",\"start\":true,\"ingredient\":{\"item\":\"minecraft:quartz\"},\"recipe\":{\"type\":\"create:cutting\"}}"));
            mixed.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"raw\",\"to\":\"finish\"}"));
            var mixedGraph=new GraphRecipe(mixed);s.install(mixedGraph);definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());boolean unknownMerge=false;
            try{GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("finish"));}catch(PreviewRecipeData.UnknownDynamicCarrier unknown){unknownMerge=true;}
            check(unknownMerge,"A mixed known/unknown incoming set cannot be presented as a complete known graph-default set");
            var restored=json.deepCopy();restored.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("transitional_item","minecraft:gold_ingot");
            restored.getAsJsonArray("nodes").get(1).getAsJsonObject().remove("finish");restored.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"after\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}"));
            restored.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"finish\",\"to\":\"after\"}"));s.install(new GraphRecipe(restored));definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            var after=GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("after")).value();
            check(after.getIngredients().getFirst().test(new ItemStack(Items.GOLD_INGOT))&&!after.getIngredients().getFirst().test(new ItemStack(Items.IRON_NUGGET)),"An explicit carrier override after trim restores known identity for its successors");
            var nativeCandidate=json.deepCopy();nativeCandidate.getAsJsonArray("nodes").get(1).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:diamond_chestplate\"},\"result\":{\"id\":\"minecraft:stone\"},\"experience\":0,\"cookingtime\":20}"));
            s.install(new GraphRecipe(nativeCandidate));definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());var candidate=GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("finish")).value();
            check(candidate.getIngredients().getFirst().test(new ItemStack(Items.DIAMOND_CHESTPLATE))&&!candidate.getIngredients().getFirst().test(new ItemStack(Items.IRON_NUGGET)),"When a full native viewer definition is available its original candidates remain usable despite unknown dynamic input");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void invalidOrRepeatedNativeTrimCannotAdvanceEvenAtFinish(GameTestHelper helper) {
        for(String invalid:List.of("same-trim","wrong-base","wrong-template","unregistered-material","unregistered-pattern")) {
            var json=graph("[{\"item\":\"minecraft:diamond\"}]",true).json();var recipe=json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe");
            ItemStack base=new ItemStack(Items.DIAMOND_CHESTPLATE);Item template=Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,addition=Items.AMETHYST_SHARD;
            if(invalid.equals("same-trim")) {
                var nativeRecipe=(SmithingRecipe)Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),recipe).getOrThrow();
                base=nativeRecipe.assemble(new SmithingRecipeInput(new ItemStack(template),base,new ItemStack(addition)),helper.getLevel().registryAccess());
            }else if(invalid.equals("wrong-base")){base=new ItemStack(Items.STONE);json.add("ingredient",JsonParser.parseString("{\"item\":\"minecraft:stone\"}"));}
            else if(invalid.equals("wrong-template"))template=Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
            else if(invalid.equals("unregistered-material")){addition=Items.DIRT;recipe.add("addition",JsonParser.parseString("{\"item\":\"minecraft:dirt\"}"));}
            else {template=Items.STICK;recipe.add("template",JsonParser.parseString("{\"item\":\"minecraft:stick\"}"));}
            var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:trim_noop_collision"),new SmithingTransformRecipe(Ingredient.of(template),Ingredient.of(base.getItem()),Ingredient.of(addition),new ItemStack(Items.GOLD_INGOT)));
            // Only a fully native-matched assembly failure is a protected collision. Plain
            // wrong-input cases use graph-only definitions rather than suppressing valid ordinary recipes.
            try(var s=invalid.equals("same-trim")||invalid.startsWith("unregistered")?new Session(helper,new GraphRecipe(json),ordinary):new Session(helper,new GraphRecipe(json))) {
                s.fill(base,1);s.menu.getSlot(0).set(new ItemStack(template));s.menu.getSlot(2).set(new ItemStack(addition));
                var random=helper.getLevel().random;random.setSeed(31901);long expected=random.nextLong();random.setSeed(31901);s.click(ClickType.PICKUP,0);
                check(s.counts(1)&&s.menu.getCarried().isEmpty()&&!s.menu.getSlot(3).hasItem()&&random.nextLong()==expected,"Native "+invalid+" must not pay, draw RNG, finish or ordinary-fallback");
            }
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void trimAuxiliaryTemplateAndMaterialValidateIndependentlyAndNeverMergeHistory(GameTestHelper helper) {
        for(int auxiliary:List.of(1,2)) {
            var json=graph("[{\"item\":\"minecraft:diamond\"}]",false).json();var nodes=json.getAsJsonArray("nodes");
            var trim=nodes.get(0).getAsJsonObject();trim.remove("start");trim.add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":0,\"current_nodes\":[\"body\"]},{\"item_input\":"+auxiliary+",\"current_nodes\":[\"insert\"],\"requirements\":{\"mark_ranges\":{\"quality\":{\"min\":90}}}}]"));
            nodes.add(JsonParser.parseString("{\"id\":\"body\",\"start\":true,\"transitional_item\":\"minecraft:diamond_chestplate\",\"mark_amounts\":{\"quality\":3},\"recipe\":{\"type\":\"create:pressing\"}}"));
            String auxiliaryItem=auxiliary==1?"minecraft:coast_armor_trim_smithing_template":"minecraft:amethyst_shard";
            nodes.add(JsonParser.parseString("{\"id\":\"insert\",\"start\":true,\"ingredient\":{\"item\":\"minecraft:quartz\"},\"transitional_item\":\""+auxiliaryItem+"\",\"checkpoint\":true,\"mark_amounts\":{\"quality\":99},\"recipe\":{\"type\":\"create:cutting\"}}"));
            json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"body\",\"to\":\"trim\"}"));json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"insert\",\"to\":\"trim\"}"));
            var graph=new GraphRecipe(json);var definition=graph.definition(ID.toString());int physical=auxiliary==1?0:2;
            var main=Workpiece.write(new ItemStack(Items.DIAMOND_CHESTPLATE),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "body", "minecraft:diamond_chestplate"), ()->0)).progress());
            var extra=Workpiece.write(dev.graphassembly.integration.NativeSteps.stack(auxiliaryItem),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "insert", "minecraft:quartz"), ()->0)).progress());
            try(var s=new Session(helper,graph)) {
                s.fill(main,1);s.menu.getSlot(physical).set(extra.copy());s.click(ClickType.PICKUP,0);var result=s.menu.getCarried();var state=Workpiece.read(helper.getLevel(),result);
                check(result.is(Items.DIAMOND_CHESTPLATE)&&result.has(DataComponents.TRIM)&&state!=null&&state.current().equals("trim")&&state.marks().values().get("quality")==3
                    &&state.recentHistory().equals(List.of("body","trim"))&&!state.checkpoints().contains("insert")&&s.counts(0),"Native trim role"+auxiliary+" validates a second workpiece but only the armor's history/marks advance");
                for(boolean foreign:List.of(false,true)) {
                    s.reset();s.fill(main,1);var bad=foreign?extra.copy():dev.graphassembly.integration.NativeSteps.stack(auxiliaryItem);
                    if(foreign)bad.set(GraphAssembly.PROGRESS.get(),bad.get(GraphAssembly.PROGRESS.get()).replace(ID.toString(),"test:foreign_trim"));
                    s.menu.getSlot(physical).set(bad);s.click(ClickType.PICKUP,0);check(s.counts(1)&&s.menu.getCarried().isEmpty(),"Raw/foreign auxiliary cannot fill declared native trim role"+auxiliary);
                }
            }
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void trimFinishBundlesEmptySettlementAndEveryRandomCarrierCapacityAreAtomic(GameTestHelper helper) {
        try(var s=new Session(helper,graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":2}]}]",true))) {
            s.fill(new ItemStack(Items.DIAMOND_CHESTPLATE),1);check(s.menu.getSlot(3).remove(64).isEmpty(),"A dynamic finish preview is not directly extractable");
            s.menu.getSlot(3).onTake(s.player,new ItemStack(Items.DIAMOND));s.click(ClickType.THROW,0);s.click(ClickType.CLONE,2);check(s.counts(1)&&s.menu.getCarried().isEmpty(),"Direct onTake/Q/clone cannot bypass native trim or graph payment");
            for(int slot=0;slot<36;slot++)s.player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,63));
            var random=helper.getLevel().random;random.setSeed(31903);long expected=random.nextLong();random.setSeed(31903);s.click(ClickType.PICKUP,0);
            check(s.counts(1)&&s.menu.getCarried().isEmpty()&&random.nextLong()==expected,"Trim finish checks every graph bundle stack before RNG and native payment");
            s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,62));s.click(ClickType.PICKUP,1);
            check(s.counts(0)&&s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&s.player.getInventory().getItem(0).getCount()==64&&s.player.getInventory().countItem(Items.DIAMOND_CHESTPLATE)==0,"A valid trim finish delivers its complete graph bundle, not an extra native armor result");
        }
        try(var s=new Session(helper,graph("[]",true))) {
            s.fill(new ItemStack(Items.DIAMOND_CHESTPLATE),1);s.menu.quickMoveStack(s.player,3);
            check(s.counts(0)&&s.menu.getCarried().isEmpty()&&s.player.getInventory().isEmpty(),"A valid native trim still pays one round for STRICT empty settlement without granting armor");
        }
        var json=fixtureGraph("[{\"item\":\"minecraft:diamond\"}]",false).json();var node=json.getAsJsonArray("nodes").get(0).getAsJsonObject();node.addProperty("branch_mode","random");
        json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("id","a");json.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"b\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}"));
        json.add("edges",JsonParser.parseString("[{\"from\":\"trim\",\"to\":\"a\"},{\"from\":\"trim\",\"to\":\"b\"}]"));
        try(var s=new Session(helper,new GraphRecipe(json))) {
            s.fill(new ItemStack(TrimNativeFixture.ITEM),1);int[] branches={0};TrimNativeFixture.limit=stack->{
                var state=Workpiece.read(helper.getLevel(),stack);if(state!=null&&"b".equals(state.routing().lockedNext())){branches[0]++;return 0;}return 64;
            };
            var random=helper.getLevel().random;random.setSeed(31905);long expected=random.nextLong();random.setSeed(31905);s.click(ClickType.PICKUP,0);
            check(branches[0]>0&&s.counts(1)&&s.menu.getCarried().isEmpty()&&random.nextLong()==expected,"Every random locked-next carrier uses native item/components for preflight, including an unplaceable alternate branch");
            TrimNativeFixture.reset();s.click(ClickType.PICKUP,0);var result=s.menu.getCarried();var state=Workpiece.read(helper.getLevel(),result);
            check(s.counts(0)&&result.is(TrimNativeFixture.ITEM)&&result.has(DataComponents.TRIM)&&state!=null&&List.of("a","b").contains(state.routing().lockedNext()),"Restored capacity commits one native dynamic carrier with one legal random successor");
        }finally{TrimNativeFixture.reset();}
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void nativeAssembleAndPreparedCarrierCallbacksCannotCommitStaleTrim(GameTestHelper helper) {
        var failures=new ArrayList<String>();
        for(String action:List.of("input","reload","reenter","throw","carrier-change","assemble-reentry")) {
            var graph=fixtureGraph("[{\"item\":\"minecraft:diamond\"}]",false);
            try(var s=new Session(helper,graph)) {
                s.fill(new ItemStack(TrimNativeFixture.ITEM),1);boolean[] fired={false};
                if(action.equals("assemble-reentry")) {
                    TrimNativeFixture.predicate=stack->{
                        // Only arm the actual native assemble call, not slot admission or matches.
                        boolean assembling=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame->frame.getClassName().equals(SmithingTrimRecipe.class.getName())&&frame.getMethodName().equals("assemble"));
                        if(assembling&&!fired[0]){fired[0]=true;s.menu.quickMoveStack(s.player,3);}
                    };
                } else TrimNativeFixture.capacity=stack->{
                    // Graph progress is added only to prepared nonterminal output alternatives.
                    if(!Workpiece.has(stack)||fired[0])return;fired[0]=true;
                    switch(action) {
                        case "input" -> {var replacement=s.menu.getSlot(1).getItem().copy();replacement.set(DataComponents.CUSTOM_NAME,Component.literal("external replacement"));s.menu.getSlot(1).set(replacement);}
                        case "reload" -> s.install(new GraphRecipe(graph.json()));
                        case "reenter" -> s.menu.quickMoveStack(s.player,3);
                        case "throw" -> throw new IllegalStateException("trim prepared carrier capacity fixture");
                        case "carrier-change" -> TrimNativeFixture.predicate=input->input.set(DataComponents.CUSTOM_NAME,Component.literal("fresh native result"));
                    }
                };
                var random=helper.getLevel().random;random.setSeed(31907);long expected=random.nextLong();random.setSeed(31907);
                try{s.click(ClickType.PICKUP,0);}finally{TrimNativeFixture.reset();}
                boolean noDraw=random.nextLong()==expected;
                if(!fired[0]||!s.counts(1)||!s.menu.getCarried().isEmpty()||!noDraw)failures.add(action+"(fired="+fired[0]+",counts="+s.counts(1)+",cursor="+s.menu.getCarried()+",noDraw="+noDraw+")");
                s.reset();s.install(graph);s.fill(new ItemStack(TrimNativeFixture.ITEM),1);s.click(ClickType.PICKUP,0);
                check(s.counts(0)&&Workpiece.has(s.menu.getCarried())&&s.menu.getCarried().has(DataComponents.TRIM),"Native trim scope recovers after external preparation "+action);
            }finally{TrimNativeFixture.reset();}
        }
        check(failures.isEmpty(),"Native assemble reentry and prepared input/definition/capacity/dynamic result changes must abort before RNG/payment: "+failures);helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void trimShiftStopsWhenRefreshAfterFirstNativePaymentFails(GameTestHelper helper) {
        var failures=new ArrayList<String>();
        for(String action:List.of("reload","reenter","throw")) {
            var graph=fixtureGraph("[{\"item\":\"minecraft:diamond\",\"count\":2,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"trim-shift\"}}]",true);
            try(var s=new Session(helper,graph)) {
                s.fill(new ItemStack(TrimNativeFixture.ITEM),2);check(s.counts(2),"Native trim batch fixture explicitly admits a stackable base, not an illegal stack of vanilla armor");
                boolean[] fired={false};long[] expected={0};var random=helper.getLevel().random;
                FurnaceCodecFixture.decode=()->{
                    if(!s.counts(1)||fired[0])return;fired[0]=true;random.setSeed(31909);expected[0]=random.nextLong();random.setSeed(31909);
                    switch(action) {
                        case "reload" -> s.install(fixtureGraph("[{\"item\":\"minecraft:emerald\"}]",true));
                        case "reenter" -> s.menu.quickMoveStack(s.player,3);
                        case "throw" -> throw new IllegalStateException("trim paid-round refresh fixture");
                    }
                };
                try{s.menu.quickMoveStack(s.player,3);}finally{FurnaceCodecFixture.decode=()->{};}
                boolean noDraw=random.nextLong()==expected[0];boolean stopped=fired[0]&&s.counts(1)&&s.player.getInventory().countItem(Items.DIAMOND)==2&&s.player.getInventory().countItem(Items.EMERALD)==0&&noDraw;
                if(!stopped)failures.add(action+"(fired="+fired[0]+",remaining="+s.menu.getSlot(1).getItem().getCount()+",diamond="+s.player.getInventory().countItem(Items.DIAMOND)+",noDraw="+noDraw+")");
                if(stopped){s.install(fixtureGraph("[{\"item\":\"minecraft:diamond\",\"count\":2}]",true));s.menu.quickMoveStack(s.player,3);check(s.counts(0)&&s.player.getInventory().countItem(Items.DIAMOND)==4,"A separate healthy Shift resumes remaining trim round after "+action);}
            }finally{FurnaceCodecFixture.decode=()->{};TrimNativeFixture.reset();}
        }
        check(failures.isEmpty(),"Trim Shift delivers only its first native-paid bundle when the following refresh fails: "+failures);helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void trimReferenceSubclassesAndCloseKeepNativeConditionsAndProtectedProgress(GameTestHelper helper) {
        var reference=ResourceLocation.parse("test:trim_reference");var json=graph("[{\"item\":\"minecraft:diamond\"}]",false).json();
        var nativeData=json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").deepCopy();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smithing_trim\",\"recipe_id\":\"test:trim_reference\"}"));
        var graph=new GraphRecipe(json);var recipe=(SmithingTrimRecipe)Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),nativeData).getOrThrow();
        var holder=new RecipeHolder<>(reference,recipe);
        try(var s=new Session(helper,graph,holder)) {
            s.fill(new ItemStack(Items.DIAMOND_CHESTPLATE),1);var definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            check(GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("trim"))==holder&&dev.graphassembly.client.StepDescription.materials(recipe).size()==2,"Trim reference viewer preserves the original holder and native template/material hints");
            s.click(ClickType.PICKUP,0);var carrier=s.menu.getCarried().copy();check(carrier.is(Items.DIAMOND_CHESTPLATE)&&carrier.has(DataComponents.TRIM)&&Workpiece.has(carrier),"Exact native trim reference creates a native armor graph carrier");
            s.menu.setCarried(ItemStack.EMPTY);s.fill(carrier,1);s.menu.getSlot(2).set(new ItemStack(Items.QUARTZ));
            s.install(graph,new RecipeHolder<>(reference,new SmithingTrimRecipe(Ingredient.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),Ingredient.of(Items.DIAMOND_CHESTPLATE),Ingredient.of(Items.AMETHYST_SHARD)){}));
            s.menu.createResult();s.click(ClickType.PICKUP,0);
            check(GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null&&s.counts(1)&&s.menu.getCarried().isEmpty(),"Reference subclass cannot gain graph execution through codec copying or consume existing protected armor");
            s.install(graph,holder);s.menu.createResult();check(s.menu.getSlot(3).hasItem(),"Restored exact reference permits the valid next quartz trim finish preview");
            s.menu.removed(s.player);
            check(s.counts(0)&&s.player.getInventory().countItem(Items.DIAMOND_CHESTPLATE)==1&&s.player.getInventory().countItem(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE)==1&&s.player.getInventory().countItem(Items.QUARTZ)==1&&s.player.getInventory().countItem(Items.DIAMOND)==0,"Native close returns armor/template/material, never the dynamic/finish preview");
            s.click(ClickType.PICKUP,0);check(s.menu.getCarried().isEmpty(),"A closed native trim menu cannot authorize its old preview");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void featureDisabledNativeTrimResultCannotBeDiscardedToAuthorizeEnabledFinish(GameTestHelper helper) {
        var disabled=new ItemStack(CampfireFeatureFixture.ITEM);var level=helper.getLevel();
        var nativeRecipe=new SmithingTrimRecipe(Ingredient.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),Ingredient.of(CampfireFeatureFixture.ITEM),Ingredient.of(Items.AMETHYST_SHARD));
        var nativeInput=new SmithingRecipeInput(new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),disabled.copy(),new ItemStack(Items.AMETHYST_SHARD));
        var nativeResult=nativeRecipe.assemble(nativeInput,level.registryAccess());
        check(nativeRecipe.matches(nativeInput,level)&&nativeResult.is(CampfireFeatureFixture.ITEM)&&nativeResult.has(DataComponents.TRIM)
            &&!nativeResult.isItemEnabled(level.enabledFeatures()),"Native fixture genuinely assembles a feature-disabled trim result, not invalid pattern/material data");
        var json=graph("[{\"item\":\"minecraft:diamond\"}]",true).json();
        json.add("ingredient",JsonParser.parseString("{\"item\":\"graph_assembly:campfire_disabled_fixture\"}"));
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("base",JsonParser.parseString("{\"item\":\"graph_assembly:campfire_disabled_fixture\"}"));
        var graph=new GraphRecipe(json);var nativeHolder=new RecipeHolder<>(ResourceLocation.parse("test:disabled_native_trim"),nativeRecipe);
        var ordinary=new SmithingTransformRecipe(Ingredient.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),Ingredient.of(CampfireFeatureFixture.ITEM),Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.GOLD_INGOT));
        check(ordinary.matches(nativeInput,level)&&ordinary.assemble(nativeInput,level.registryAccess()).isItemEnabled(level.enabledFeatures()),"Enabled ordinary transform collision must be a real viable native recipe");
        try(var s=new Session(helper,graph,nativeHolder)) {
            // First establish the actual vanilla menu gate with no graph recipe installed.
            level.getRecipeManager().replaceRecipes(List.of(nativeHolder));GraphIndex.invalidate();s.fill(disabled,1);s.click(ClickType.PICKUP,0);
            check(s.counts(1)&&!s.menu.getSlot(3).hasItem()&&s.menu.getCarried().isEmpty(),"Native SmithingMenu refuses its feature-disabled assembled result without consuming input");
            s.reset();s.install(graph,new RecipeHolder<>(ResourceLocation.parse("test:disabled_trim_ordinary_collision"),ordinary));s.fill(disabled,1);
            var random=level.random;random.setSeed(31911);long expected=random.nextLong();random.setSeed(31911);s.click(ClickType.PICKUP,0);
            check(s.counts(1)&&s.menu.getCarried().isEmpty()&&!s.menu.getSlot(3).hasItem()&&s.player.getInventory().isEmpty()&&random.nextLong()==expected,
                "A graph finish must honor the native result feature gate before discarding its carrier: no enabled diamond, ordinary fallback, RNG or input payment");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_trim")
    public static void graphOnlyNativeTrimKeepsArmorComponentsAndAdvancesOnce(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var id=ResourceLocation.parse("test:native_trim_tracer");var pos=helper.absolutePos(new BlockPos(1,1,1));
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"trim-native"));
        SmithingMenu menu=null;
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:diamond_chestplate"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"trim","start":true,"recipe":{"type":"minecraft:smithing_trim",
                   "template":{"item":"minecraft:coast_armor_trim_smithing_template"},"base":{"item":"minecraft:diamond_chestplate"},
                   "addition":{"item":"minecraft:amethyst_shard"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"minecraft:smithing_trim",
                    "template":{"item":"minecraft:coast_armor_trim_smithing_template"},"base":{"item":"minecraft:diamond_chestplate"},
                    "addition":{"item":"minecraft:quartz"}}}],
                 "edges":[{"from":"trim","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond","count":2}]}]}
                """).getAsJsonObject());
            var base=new ItemStack(Items.DIAMOND_CHESTPLATE);base.setDamageValue(37);
            base.set(DataComponents.CUSTOM_NAME,Component.literal("Native trim carrier"));
            base.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),3);
            var template=new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE);var addition=new ItemStack(Items.AMETHYST_SHARD);
            // Validate the actual patched native recipe/registries before expecting an adapter RED.
            var nativeData=graph.json().getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe");
            var nativeRecipe=(SmithingTrimRecipe)Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),nativeData).getOrThrow();
            var nativeInput=new SmithingRecipeInput(template.copy(),base.copy(),addition.copy());
            var expected=nativeRecipe.assemble(nativeInput,level.registryAccess());
            check(nativeRecipe.matches(nativeInput,level)&&expected.is(Items.DIAMOND_CHESTPLATE)&&expected.has(DataComponents.TRIM),
                "Tracer fixture must be a genuinely valid native Coast/amethyst armor trim");
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,Blocks.SMITHING_TABLE.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();menu=new SmithingMenu(1,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            var inputs=List.of(template,base,addition);
            for(int physical=0;physical<3;physical++) {menu.setCarried(inputs.get(physical).copy());menu.clicked(physical,0,ClickType.PICKUP,player);}
            menu.setCarried(ItemStack.EMPTY);var preview=menu.getSlot(3).getItem().copy();
            check(!Workpiece.has(base)&&base.getDamageValue()==37,"Preview must not add progress to or damage the original armor");
            menu.clicked(3,0,ClickType.PICKUP,player);var result=menu.getCarried();
            check(result.is(Items.DIAMOND_CHESTPLATE)&&result.getCount()==1,
                "Native trim result must retain armor as graph carrier despite the iron-nugget graph default");
            check(result.getDamageValue()==37&&Objects.equals(result.get(DataComponents.CUSTOM_NAME),base.get(DataComponents.CUSTOM_NAME))
                &&Objects.equals(result.get(DataComponents.ENCHANTMENTS),base.get(DataComponents.ENCHANTMENTS))
                &&Objects.equals(result.get(DataComponents.TRIM),expected.get(DataComponents.TRIM)),
                "Native armor damage, custom name, enchantments and actual assembled trim survive graph progression");
            var progress=Workpiece.read(level,result);
            check(progress!=null&&progress.graphId().equals(id.toString())&&progress.current().equals("trim")&&progress.recentHistory().equals(List.of("trim")),
                "The native base owns exactly one new graph step, not an auxiliary or finish settlement");
            check(preview.is(Items.DIAMOND_CHESTPLATE)&&preview.has(DataComponents.TRIM)&&Workpiece.has(preview),
                "The nonauthoritative preview uses the same native armor carrier and trim");
            for(int physical=0;physical<3;physical++)check(menu.getSlot(physical).getItem().isEmpty(),"Native trim pays one unit from physical input "+physical);
            check(player.getInventory().isEmpty(),"Nonterminal trim grants no extra native armor or graph finish reward");
            helper.succeed();
        } finally {
            if(menu!=null){for(int slot=0;slot<4;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
