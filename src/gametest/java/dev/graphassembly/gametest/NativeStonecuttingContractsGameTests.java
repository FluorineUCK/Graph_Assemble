package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeStonecuttingContractsGameTests {
    static final ResourceLocation ID=ResourceLocation.parse("test:stonecutting_contract");
    static GraphRecipe graph(String outputs) {return new GraphRecipe(JsonParser.parseString("""
        {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
         "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"minecraft:stonecutting",
         "ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"}}}],
         "edges":[],"outputs":[{"results":%s}]}
        """.formatted(outputs)).getAsJsonObject());}
    static final class Session implements AutoCloseable {
        final GameTestHelper helper; final List<RecipeHolder<?>> saved;
        final net.neoforged.neoforge.common.util.FakePlayer player; final StonecutterMenu menu;
        Session(GameTestHelper helper) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            player=new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"stone-contract"));
            menu=new StonecutterMenu(2,player.getInventory());player.containerMenu=menu;player.getInventory().clearContent();
        }
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        void graph(GraphRecipe graph){install(new RecipeHolder<>(ID,graph));}
        void input(int count){menu.getSlot(0).set(new ItemStack(Items.AMETHYST_SHARD,count));}
        void select(){check(menu.clickMenuButton(player,0),"Graph choice must be selectable");}
        public void close(){menu.container.clearContent();menu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void inventoryShiftRoutesGraphOnlyInputAndRepeatsCompleteRounds(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"minecraft:diamond\",\"count\":2}]"));
            s.player.getInventory().setItem(9,new ItemStack(Items.AMETHYST_SHARD,3));
            s.menu.clicked(2,0,ClickType.QUICK_MOVE,s.player);
            check(s.menu.getSlot(0).getItem().getCount()==3,"Inventory shift-click must route graph-only raw material into native input");
            s.select();s.menu.clicked(1,0,ClickType.QUICK_MOVE,s.player);
            check(s.menu.getSlot(0).getItem().isEmpty(),"Result shift-click must repeat all three complete graph rounds");
            check(s.player.getInventory().countItem(Items.DIAMOND)==6,"Graph rounds deliver six diamonds and no native placeholder");
            check(s.player.getInventory().countItem(Items.STONE)==0,"Native result is never doubled");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void deterministicExplicitGraphChoicesPreserveCountButClearOnIdentityOrReload(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:z"),graph("[{\"item\":\"minecraft:emerald\"}]")),
                new RecipeHolder<>(ResourceLocation.parse("test:a"),graph("[{\"item\":\"minecraft:diamond\"}]")));
            s.input(3);
            check(s.menu.getRecipes().size()==2&&s.menu.getRecipes().getFirst().id().toString().equals("test:a"),"Legal graph choices must be sorted, not disabled as ambiguous");
            check(!s.menu.clickMenuButton(s.player,-1)&&!s.menu.clickMenuButton(s.player,2),"Invalid choice indices must be rejected");
            check(s.menu.clickMenuButton(s.player,1),"Second graph must be explicitly selectable");
            s.input(4);check(s.menu.getSelectedRecipeIndex()==1,"Count-only top-up must preserve explicit graph choice");
            s.menu.clicked(1,1,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.EMERALD)&&s.menu.getSlot(0).getItem().getCount()==3,"Right pickup commits whole selected graph round");
            s.menu.setCarried(ItemStack.EMPTY);
            var named=new ItemStack(Items.AMETHYST_SHARD,3);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("new identity"));
            s.menu.getSlot(0).set(named);
            check(s.menu.getSelectedRecipeIndex()==-1&&s.menu.getSlot(1).getItem().isEmpty(),"Same item with changed components clears old selection");
            s.select();s.graph(graph("[{\"item\":\"minecraft:gold_ingot\"}]"));
            check(s.menu.getRecipes().size()==1&&s.menu.getSelectedRecipeIndex()==-1,"Graph reload must refresh list and invalidate selected graph identity");
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getCount()==3,"Stale post-reload preview cannot authorize a round");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void sameItemProgressRebuildsExplicitNodeChoicesAndTransfersOneState(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:amethyst_shard",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},"result":{"id":"minecraft:stone"}}},
                 {"id":"a","finish":true,"recipe":{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},"result":{"id":"minecraft:stone"}},"outputs":[{"results":[{"item":"minecraft:diamond"}]}]},
                 {"id":"z","finish":true,"recipe":{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},"result":{"id":"minecraft:stone"}},"outputs":[{"results":[{"item":"minecraft:emerald"}]}]}],
                 "edges":[{"from":"start","to":"z"},{"from":"start","to":"a"}],"outputs":[]}
                """).getAsJsonObject()));
            s.input(2);s.select();s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            var carrier=s.menu.getCarried().copy();s.menu.setCarried(ItemStack.EMPTY);s.menu.getSlot(0).set(carrier);
            check(s.menu.getRecipes().size()==2&&s.menu.getSelectedRecipeIndex()==-1,"Same-item workpiece swap must expose two next-node choices, not stale entrance");
            s.menu.clickMenuButton(s.player,1);s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.EMERALD)&&s.menu.getSlot(0).getItem().isEmpty(),"Explicit second node completes exactly its own graph state");
            s.menu.setCarried(ItemStack.EMPTY);s.input(1);
            check(s.menu.getRecipes().size()==1&&s.menu.getSelectedRecipeIndex()==-1,"Swapping back to raw same-item input restores entrance list");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void ordinaryCollisionFallbackAndInvalidProgressStaySeparated(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:ordinary_stone"),new StonecutterRecipe("",Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE,2)));
            s.install(ordinary,new RecipeHolder<>(ID,graph("[{\"item\":\"minecraft:diamond\"}]")));s.input(2);s.select();s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.DIAMOND),"Graph entrance wins ordinary collision");
            s.menu.setCarried(ItemStack.EMPTY);s.install(ordinary);s.input(2);s.select();s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.STONE)&&s.menu.getCarried().getCount()==2&&s.menu.getSlot(0).getItem().getCount()==1,"Ordinary menu transfer remains unchanged");
            s.menu.setCarried(ItemStack.EMPTY);var invalid=new ItemStack(Items.AMETHYST_SHARD);invalid.set(GraphAssembly.PROGRESS.get(),"{}");s.menu.getSlot(0).set(invalid);
            check(s.menu.getRecipes().isEmpty(),"Malformed progress cannot ordinary-fallback");
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);check(s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).hasItem(),"Invalid progress must remain unconsumed");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void previewDirectCloneThrowAndSwapCannotBypassWholeRoundCapacity(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\"}]}]"));s.input(2);s.select();
            check(s.menu.getSlot(1).remove(64).isEmpty(),"Direct result removal must not extract representative preview");
            s.menu.getSlot(1).onTake(s.player,new ItemStack(Items.DIAMOND));
            s.menu.clicked(1,2,ClickType.CLONE,s.player);s.menu.clicked(1,0,ClickType.THROW,s.player);
            check(s.menu.getSlot(0).getItem().getCount()==2&&s.menu.getCarried().isEmpty(),"Unsupported extraction paths cannot consume or clone graph outputs");
            s.player.getInventory().setItem(0,new ItemStack(Items.STONE));s.menu.clicked(1,0,ClickType.SWAP,s.player);
            check(s.menu.getSlot(0).getItem().getCount()==2,"SWAP requires an empty valid target");
            s.player.getInventory().setItem(0,ItemStack.EMPTY);s.menu.clicked(1,0,ClickType.SWAP,s.player);
            check(s.player.getInventory().getItem(0).is(Items.DIAMOND)&&s.player.getInventory().getItem(0).getCount()==2&&s.player.getInventory().countItem(Items.EMERALD)==1,"SWAP delivers first whole stack and all extras");
            check(s.menu.getSlot(0).getItem().getCount()==1,"Successful SWAP consumes one round");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void allPossibleBundlesPreflightWithoutRngAndEmptyRoundsAreValid(GameTestHelper helper) {
        for(String outputs:List.of("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\",\"chance\":0.5}]}]","[{\"item\":\"minecraft:diamond\",\"count\":2}]"))try(var s=new Session(helper)) {
            s.graph(graph(outputs));s.input(2);for(int i=0;i<36;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var random=helper.getLevel().random;random.setSeed(7139);long expected=random.nextLong();random.setSeed(7139);
            s.select();s.menu.clicked(1,0,ClickType.QUICK_MOVE,s.player);
            check(random.nextLong()==expected&&s.menu.getSlot(0).getItem().getCount()==2,"Impossible possible bundle blocks before RNG/input consumption");
        }
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"minecraft:diamond\",\"chance\":0}]"));s.input(2);s.select();s.menu.quickMoveStack(s.player,1);
            check(s.menu.getSlot(0).getItem().isEmpty()&&s.player.getInventory().countItem(Items.DIAMOND)==0&&s.player.getInventory().countItem(Items.STONE)==0,"Direct shift handles two empty-output rounds without placeholder");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void exactNativeReferenceAdmissionAndPreviewPreserveFullIngredients(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var json=graph("[{\"item\":\"minecraft:diamond\"}]").json().deepCopy();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"recipe_id\":\"test:stone_reference\"}"));
            var ref=new RecipeHolder<>(ResourceLocation.parse("test:stone_reference"),new StonecutterRecipe("",Ingredient.of(Items.STONE),new ItemStack(Items.STONE)));
            var holder=new RecipeHolder<>(ID,new GraphRecipe(json));s.install(holder,ref);s.input(2);s.select();
            var definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            check(dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("s"))==ref,"Referenced viewer preview must retain exact holder identity");
            s.install(holder,new RecipeHolder<>(ref.id(),new StonecutterRecipe("",Ingredient.of(Items.STONE),new ItemStack(Items.STONE))));
            check(s.menu.getRecipes().size()==1&&s.menu.getSelectedRecipeIndex()==-1,"Same-ID native reference replacement clears selection");
            s.install(holder,new RecipeHolder<>(ref.id(),new StonecutterRecipe("",Ingredient.of(Items.STONE),new ItemStack(Items.STONE)){}));
            check(s.menu.getRecipes().isEmpty()&&GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null,"Reference subclass must not gain admission via codec roundtrip");
            var inline=graph("[{\"item\":\"minecraft:diamond\"}]").json().deepCopy();inline.add("ingredient",JsonParser.parseString("[{\"item\":\"minecraft:amethyst_shard\"},{\"item\":\"minecraft:iron_ingot\"}]"));
            s.graph(new GraphRecipe(inline));definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            var preview=dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("s"));
            check(preview.value().getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT))&&preview.value().getIngredients().getFirst().test(new ItemStack(Items.AMETHYST_SHARD)),"Inline viewer retains every entrance alternative");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void realComponentCodecMutationsAndReentryCannotCommitStaleRound(GameTestHelper helper) {
        for(String action:List.of("input","cursor","inventory","result","close","reload","reentry","throw"))try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"probe\"}}]"));s.input(2);s.select();
            int[] calls={0};FurnaceCodecFixture.decode=()->{
                if(calls[0]++!=0)return;
                switch(action) {
                    case "input" -> s.menu.getSlot(0).set(new ItemStack(Items.COPPER_INGOT,2));
                    case "cursor" -> s.menu.setCarried(new ItemStack(Items.COPPER_INGOT));
                    case "inventory" -> s.player.getInventory().setItem(0,new ItemStack(Items.STONE));
                    case "result" -> s.menu.getSlot(1).set(new ItemStack(Items.STONE));
                    case "close" -> s.menu.removed(s.player);
                    case "reload" -> s.install(new RecipeHolder<>(ID,graph("[{\"item\":\"minecraft:emerald\"}]")));
                    case "reentry" -> {s.menu.clicked(1,0,ClickType.PICKUP,s.player);s.menu.quickMoveStack(s.player,1);}
                    case "throw" -> throw new IllegalStateException("stonecutting fixture decode");
                }
            };
            var random=helper.getLevel().random;random.setSeed(7141);long expected=random.nextLong();random.setSeed(7141);
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);FurnaceCodecFixture.decode=()->{};
            check(calls[0]>0,"Registered output component codec must actually execute");
            if(!action.equals("reentry")) {
                check(random.nextLong()==expected,"Codec "+action+" must abort before RNG");
                check(s.menu.getSlot(0).getItem().getCount()==2,"Codec "+action+" cannot consume old/replaced input");
                check(action.equals("cursor")?s.menu.getCarried().is(Items.COPPER_INGOT):s.menu.getCarried().isEmpty(),"Codec mutation must not be overwritten by old output");
            } else check(s.menu.getSlot(0).getItem().getCount()==1,"Reentrant clicked and direct shift must not add an extra round");
            if(action.equals("throw")){s.select();s.menu.clicked(1,0,ClickType.PICKUP,s.player);check(s.menu.getSlot(0).getItem().getCount()==1,"Exception must release busy for later operation");}
        } finally {FurnaceCodecFixture.decode=()->{};}
        helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void pickupExtrasStackComponentsAndCursorCapacityAreAtomic(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":2}]}]"));s.input(3);s.select();
            for(int i=0;i<36;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,63));
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getCount()==3,"Extras need complete inventory capacity before first output reaches cursor");
            s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,62));
            s.menu.clicked(1,1,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&s.player.getInventory().getItem(0).getCount()==64,"Right pickup delivers whole first stack and complete extras");
            s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,62));
            var named=new ItemStack(Items.DIAMOND);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("different"));s.menu.setCarried(named);
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getSlot(0).getItem().getCount()==2&&s.menu.getCarried().getCount()==1,"Cursor with different components cannot merge graph first output");
            s.menu.setCarried(new ItemStack(Items.DIAMOND,63));s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getSlot(0).getItem().getCount()==2&&s.menu.getCarried().getCount()==63,"Cursor capacity is whole-round, never truncated");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void guaranteedFirstOutputKeepsCursorDestinationOnRepeatedPickup(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\"}]}]"));s.input(2);s.select();
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&s.menu.getSlot(0).getItem().isEmpty(),"Guaranteed first diamond must remain cursor destination across repeated pickups");
            check(s.player.getInventory().countItem(Items.EMERALD)==2,"Repeated whole rounds deliver both emerald extras");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeCloseReturnsOnlyInputAndRejectsSubclassesAndExtraRoles(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"minecraft:diamond\"}]"));
            var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
            var menu=new StonecutterMenu(3,s.player.getInventory(),ContainerLevelAccess.create(helper.getLevel(),pos));s.player.containerMenu=menu;
            menu.getSlot(0).set(new ItemStack(Items.AMETHYST_SHARD,3));menu.clickMenuButton(s.player,0);
            check(!menu.getSlot(1).getItem().isEmpty(),"Close fixture must have a representative preview");
            menu.removed(s.player);
            check(menu.container.getItem(0).isEmpty()&&s.player.getInventory().countItem(Items.AMETHYST_SHARD)==3&&s.player.getInventory().countItem(Items.DIAMOND)==0,"Native close returns input, never preview");
            menu.clicked(1,0,ClickType.PICKUP,s.player);check(menu.getCarried().isEmpty(),"Closed menu cannot authorize stale result click");
            var custom=new StonecutterMenu(4,s.player.getInventory()){};s.player.containerMenu=custom;custom.getSlot(0).set(new ItemStack(Items.AMETHYST_SHARD));
            check(custom.getRecipes().isEmpty(),"Unknown menu subclass is not automatically authorized");
            s.player.containerMenu=s.menu;
            var json=graph("[{\"item\":\"minecraft:diamond\"}]").json().deepCopy();
            // Use a full valid extra workpiece role, so native one-input admission is the rejection seam.
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":1}]"));
            s.graph(new GraphRecipe(json));s.input(1);
            check(s.menu.getRecipes().isEmpty()&&GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null,"Stonecutter rejects logical input roles beyond primary0");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void capacityCallbacksAtEveryPreparationBoundaryRejectBeforeRng(GameTestHelper helper) {
        String outputs="[{\"item\":\"graph_assembly:stonecutting_capacity_fixture\",\"count\":2}]";
        for(var mode:List.of(ClickType.PICKUP,ClickType.QUICK_MOVE,ClickType.SWAP)) {
            int[] observed={0};
            try(var s=new Session(helper)) {
                s.graph(graph(outputs));s.input(1);s.select();
                if(mode==ClickType.QUICK_MOVE)for(int i=0;i<35;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
                StonecuttingCapacityFixture.query=()->{if(s.menu.getSlot(0).hasItem())observed[0]++;};
                s.menu.clicked(1,0,mode,s.player);
            } finally {StonecuttingCapacityFixture.query=()->{};}
            check(observed[0]>0,"Capacity fixture must exercise real Item.getMaxStackSize");
            // Replay every observed pre-consumption query, including the final query. This locates
            // the runtime boundary behaviorally, without assuming a production call count or seam.
            for(int boundary=1;boundary<=observed[0];boundary++)for(String action:List.of("input","inventory","close","limit"))try(var s=new Session(helper)) {
                s.graph(graph(outputs));s.input(1);s.select();int target=boundary;int[] calls={0};boolean[] fired={false};
                if(mode==ClickType.QUICK_MOVE)for(int i=0;i<35;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
                StonecuttingCapacityFixture.query=()->{
                    if(!s.menu.getSlot(0).hasItem()||++calls[0]!=target)return;
                    fired[0]=true;
                    switch(action) {
                        case "input" -> s.menu.getSlot(0).set(new ItemStack(Items.COPPER_INGOT));
                        case "inventory" -> s.player.getInventory().setItem(8,new ItemStack(Items.COPPER_INGOT));
                        case "close" -> s.menu.removed(s.player);
                        case "limit" -> StonecuttingCapacityFixture.limit=1;
                    }
                };
                var random=helper.getLevel().random;random.setSeed(7151);long expected=random.nextLong();random.setSeed(7151);
                s.menu.clicked(1,0,mode,s.player);StonecuttingCapacityFixture.query=()->{};
                check(fired[0],"Every calibrated preparation boundary must be exercised");
                check(random.nextLong()==expected,"Capacity "+mode+" "+action+" at boundary "+boundary+" must reject before RNG");
                check(s.menu.getSlot(0).hasItem()&&s.menu.getCarried().isEmpty()&&s.player.getInventory().countItem(StonecuttingCapacityFixture.ITEM)==0,"External capacity changes cannot commit stale round");
            } finally {StonecuttingCapacityFixture.query=()->{};StonecuttingCapacityFixture.limit=64;}
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void chancePartialCountsUseTheirActualItemCapacityBeforeRng(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"graph_assembly:stonecutting_capacity_fixture\",\"count\":2,\"chance\":0.5}]"));s.input(1);s.select();
            // The maximum bundle fits; the one-item random alternative has a different, invalid
            // real extension limit. No possible outcome may fail placement after the draw.
            StonecuttingCapacityFixture.perCount=count->count==1?0:64;
            var random=helper.getLevel().random;random.setSeed(7153);long expected=random.nextLong();random.setSeed(7153);
            s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(random.nextLong()==expected&&s.menu.getSlot(0).hasItem()&&s.menu.getCarried().isEmpty(),"Count-sensitive partial chance outcome must reject before RNG, even when maximum output fits");
        } finally {StonecuttingCapacityFixture.perCount=count->StonecuttingCapacityFixture.limit;}
        helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void noMatchToReloadChoicesRefreshesNativeListenerWithoutInputChange(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install();s.input(1);boolean[] available={false};int[] updates={0};
            s.menu.registerUpdateListener(()->{updates[0]++;available[0]=s.menu.hasInputItem();});
            check(!s.menu.hasInputItem(),"Reload listener fixture starts without a native or graph choice");
            s.graph(graph("[{\"item\":\"minecraft:diamond\"}]"));
            check(s.menu.getRecipes().size()==1,"Reload getter must rebuild graph choices");
            check(updates[0]==1&&available[0],"Reload must notify native menu listener so screen displayRecipes becomes true");
            for(int i=0;i<8;i++){s.menu.getRecipes();s.menu.getNumRecipes();s.menu.hasInputItem();}
            check(updates[0]==1,"Unchanged rendering getters must not recursively fire native update listener");
            s.select();s.menu.clicked(1,0,ClickType.PICKUP,s.player);check(s.menu.getCarried().is(Items.DIAMOND),"Fresh reload choice is usable without swapping input");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void fiveVanillaChanceStacksUseBoundedPreflightAndComplete(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var outputs=new com.google.gson.JsonArray();
            for(String item:List.of("diamond","emerald","iron_ingot","gold_ingot","copper_ingot")) {
                var output=new com.google.gson.JsonObject();output.addProperty("item","minecraft:"+item);output.addProperty("count",64);output.addProperty("chance",0.5);
                output.add("components",JsonParser.parseString("{\"graph_assembly:furnace_codec_fixture\":\"bounded\"}"));outputs.add(output);
            }
            s.graph(graph("[{\"mode\":\"all\",\"results\":"+outputs+"}]"));s.input(1);s.select();
            int[] decodes={0};FurnaceCodecFixture.decode=()->{
                // Safely bound the failing implementation instead of executing 65^5 transfers.
                if(++decodes[0]>1024)throw new IllegalStateException("bounded preflight fixture budget exceeded");
            };
            s.menu.clicked(1,0,ClickType.QUICK_MOVE,s.player);FurnaceCodecFixture.decode=()->{};
            check(decodes[0]<=1024&&s.menu.getSlot(0).getItem().isEmpty(),"Five ordinary chance-64 outputs must complete with bounded preflight, not a quantity Cartesian product");
        } finally {FurnaceCodecFixture.decode=()->{};}
        helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void unrelatedIndexInvalidationCannotEatFirstSelectionOrStallPickup(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("[{\"item\":\"minecraft:diamond\"}]"));s.input(2);
            check(s.menu.getRecipes().size()==1,"Index invalidation fixture must have one projected choice");
            // Integrated-client recipe synchronization clears the shared cache without replacing
            // this server menu's holders. First selection must still bind a current operation.
            GraphIndex.invalidate();
            check(s.menu.clickMenuButton(s.player,0)&&s.menu.getSelectedRecipeIndex()==0&&!s.menu.getSlot(1).getItem().isEmpty(),"Unrelated index invalidation must not swallow first native menu selection");
            GraphIndex.invalidate();s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==1&&s.menu.getSlot(0).getItem().getCount()==1,"Already selected graph must rebind after unrelated invalidation and complete first pickup");
            GraphIndex.invalidate();s.menu.clicked(1,0,ClickType.PICKUP,s.player);
            check(s.menu.getCarried().getCount()==2&&s.menu.getSlot(0).getItem().isEmpty(),"Repeated invalidation cannot leave a permanently stale cached transaction");
        }helper.succeed();
    }
}
