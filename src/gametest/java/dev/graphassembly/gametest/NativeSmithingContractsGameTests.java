package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.*;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Bounded contracts on the actual three-slot menu and inherited native input/quick-move paths. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSmithingContractsGameTests {
    static final ResourceLocation ID=ResourceLocation.parse("test:smithing_contract");
    static final String RECIPE="""
        {"type":"minecraft:smithing_transform","template":{"item":"minecraft:netherite_upgrade_smithing_template"},
         "base":{"item":"minecraft:diamond_sword"},"addition":{"item":"minecraft:copper_ingot"},"result":{"id":"minecraft:netherite_sword"}}
        """;
    static GraphRecipe graph(String outputs,boolean ongoing) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,%s"recipe":%s}%s],"edges":%s,"outputs":%s}
            """.formatted(ongoing?"":"\"finish\":true,\"finish_mode\":\"strict\",",RECIPE,
                ongoing?",{\"id\":\"f\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}":"",
                ongoing?"[{\"from\":\"s\",\"to\":\"f\"}]":"[]",outputs.equals("[]")?"[]":"[{\"results\":"+outputs+"}]")).getAsJsonObject());
    }
    static RecipeHolder<GraphRecipe> holder(GraphRecipe graph){return new RecipeHolder<>(ID,graph);}
    static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final BlockPos pos;
        final net.neoforged.neoforge.common.util.FakePlayer player;final SmithingMenu menu;
        Session(GameTestHelper helper,RecipeHolder<?>... recipes) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            install(recipes);pos=helper.absolutePos(new BlockPos(1,1,1));helper.getLevel().setBlockAndUpdate(pos,Blocks.SMITHING_TABLE.defaultBlockState());
            player=new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"smith-contract"));
            player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);player.getInventory().clearContent();
            menu=new SmithingMenu(2,player.getInventory(),ContainerLevelAccess.create(helper.getLevel(),pos));player.containerMenu=menu;
        }
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        void fill(int count){put(0,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,count);put(1,Items.AMETHYST_SHARD,count);put(2,Items.COPPER_INGOT,count);}
        void put(int physical,Item item,int count){menu.getSlot(physical).set(new ItemStack(item,count));}
        void reset(){for(int slot=0;slot<3;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();}
        boolean counts(int n){for(int slot=0;slot<3;slot++)if(menu.getSlot(slot).getItem().getCount()!=n)return false;return true;}
        void click(ClickType type,int button){menu.clicked(3,button,type,player);}
        public void close(){FurnaceCodecFixture.decode=()->{};for(int slot=0;slot<4;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;helper.getLevel().removeBlock(pos,false);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    static void check(boolean value,String message){if(!value)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void graphOnlyShiftRoutesAllThreeRolesAndRepeatsFullBundles(GameTestHelper helper) {
        try(var s=new Session(helper,holder(graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\"}]}]",false)))) {
            s.player.getInventory().setItem(9,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,3));
            s.player.getInventory().setItem(10,new ItemStack(Items.AMETHYST_SHARD,3));s.player.getInventory().setItem(11,new ItemStack(Items.COPPER_INGOT,3));
            for(int slot=4;slot<7;slot++)s.menu.clicked(slot,0,ClickType.QUICK_MOVE,s.player);
            check(s.counts(3)&&s.menu.getSlot(0).getItem().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)&&s.menu.getSlot(1).getItem().is(Items.AMETHYST_SHARD),"Graph-only native inventory Shift must route physical template0/base1/addition2");
            s.menu.quickMoveStack(s.player,3);
            check(s.counts(0)&&s.player.getInventory().countItem(Items.DIAMOND)==6&&s.player.getInventory().countItem(Items.EMERALD)==3,"Inherited direct result Shift consumes three complete rounds and delivers every graph output");
            check(s.player.getInventory().countItem(Items.NETHERITE_SWORD)==0,"Native upgrade template is not an additional reward");
        }helper.succeed();
    }

    static GraphRecipe joined(int auxiliary) {
        var recipe=JsonParser.parseString(RECIPE).getAsJsonObject();
        recipe.add(auxiliary==1?"template":"addition",JsonParser.parseString("{\"item\":\"minecraft:iron_nugget\"}"));
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"body","start":true,"mark_amounts":{"quality":3},"recipe":{"type":"create:pressing"}},
              {"id":"insert","start":true,"ingredient":{"item":"minecraft:quartz"},"checkpoint":true,"mark_amounts":{"quality":99},"recipe":{"type":"create:cutting"}},
              {"id":"join","recipe":%s,"workpiece_inputs":[{"item_input":0,"current_nodes":["body"]},{"item_input":%s,"current_nodes":["insert"],"requirements":{"mark_ranges":{"quality":{"min":90}}}}]},
              {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"body","to":"join"},{"from":"insert","to":"join"},{"from":"join","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """.formatted(recipe,auxiliary)).getAsJsonObject());
    }
    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void templateAndAdditionWorkpieceRolesUseBaseHistoryAndRejectBadAuxiliaries(GameTestHelper helper) {
        for(int logical:List.of(1,2)) {
            var graph=joined(logical);var definition=graph.definition(ID.toString());int physical=logical==1?0:2;
            var main=Workpiece.write(new ItemStack(Items.IRON_NUGGET),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "body", "minecraft:amethyst_shard"), ()->0)).progress());
            var auxiliary=Workpiece.write(new ItemStack(Items.IRON_NUGGET),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "insert", "minecraft:quartz"), ()->0)).progress());
            try(var s=new Session(helper,holder(graph))) {
                s.fill(1);s.menu.getSlot(1).set(main.copy());s.menu.getSlot(physical).set(auxiliary.copy());
                s.click(ClickType.PICKUP,0);var progress=Workpiece.read(helper.getLevel(),s.menu.getCarried());
                check(progress!=null&&progress.current().equals("join")&&progress.marks().values().get("quality")==3&&progress.recentHistory().equals(List.of("body","join"))&&!progress.checkpoints().contains("insert"),"Logical base0 (physical1) alone owns history/marks; auxiliary role"+logical+" is consumed, not merged");
                check(s.counts(0),"Both declared workpieces and remaining raw role are consumed once");
                for(String invalid:List.of("raw","wrong-route","wrong-graph","malformed","wrong-item")) {
                    s.reset();s.fill(1);s.menu.getSlot(1).set(main.copy());var bad=auxiliary.copy();
                    switch(invalid) {
                        case "raw" -> bad=new ItemStack(Items.IRON_NUGGET);
                        case "wrong-route" -> bad=main.copy();
                        case "wrong-graph" -> bad.set(GraphAssembly.PROGRESS.get(),bad.get(GraphAssembly.PROGRESS.get()).replace(ID.toString(),"test:other_graph"));
                        case "malformed" -> bad.set(GraphAssembly.PROGRESS.get(),"{}");
                        case "wrong-item" -> bad=Workpiece.write(new ItemStack(Items.GOLD_NUGGET),Workpiece.read(helper.getLevel(),auxiliary));
                    }
                    s.menu.getSlot(physical).set(bad);s.click(ClickType.PICKUP,0);
                    check(s.counts(1)&&s.menu.getCarried().isEmpty(),"Declared auxiliary role"+logical+" must reject "+invalid+" without consuming the base or ordinary fallback");
                }
            }
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void wrongTemplateInputIdentityAndReloadNeverAuthorizeOldPreview(GameTestHelper helper) {
        try(var s=new Session(helper,holder(graph("[{\"item\":\"minecraft:diamond\"}]",false)))) {
            s.fill(2);check(s.menu.getSlot(3).hasItem(),"Fixture begins with a graph preview");
            s.put(0,Items.DIRT,2);s.click(ClickType.PICKUP,0);check(s.counts(2)&&s.menu.getCarried().isEmpty()&&!s.menu.getSlot(3).hasItem(),"Wrong native template must invalidate preview without interpreting slot0 as primary");
            s.put(0,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,2);s.put(1,Items.COPPER_INGOT,2);s.click(ClickType.PICKUP,0);
            check(s.counts(2)&&s.menu.getCarried().isEmpty(),"External base replacement cannot borrow an old smithing result");
            s.put(1,Items.AMETHYST_SHARD,2);s.install(holder(graph("[{\"item\":\"minecraft:emerald\"}]",false)));s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().is(Items.EMERALD)&&s.counts(1),"Same-ID reload must bind the new result, never old diamond preview");
        }
        var ongoing=graph("[{\"item\":\"minecraft:diamond\"}]",true);
        var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:smithing_collision"),new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.IRON_NUGGET),Ingredient.of(Items.COPPER_INGOT),new ItemStack(Items.NETHERITE_SWORD)));
        try(var s=new Session(helper,holder(ongoing),ordinary)) {
            s.fill(1);s.click(ClickType.PICKUP,0);var carrier=s.menu.getCarried().copy();s.menu.setCarried(ItemStack.EMPTY);
            s.fill(1);s.menu.getSlot(1).set(carrier);s.install(ordinary);s.menu.createResult();s.click(ClickType.PICKUP,0);
            check(s.counts(1)&&Workpiece.has(s.menu.getSlot(1).getItem())&&s.menu.getCarried().isEmpty(),"Removed graph keeps progressing base protected from ordinary upgrade collision");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void ordinaryTransformAndTrimStayNativeWhileValidGraphWinsCollision(GameTestHelper helper) {
        var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:smithing_ordinary"),new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.AMETHYST_SHARD),Ingredient.of(Items.COPPER_INGOT),new ItemStack(Items.NETHERITE_SWORD)));
        try(var s=new Session(helper,ordinary)) {
            s.fill(2);var named=s.menu.getSlot(1).getItem().copy();named.set(DataComponents.CUSTOM_NAME,Component.literal("native base identity"));s.menu.getSlot(1).set(named);
            s.click(ClickType.PICKUP,0);check(s.menu.getCarried().is(Items.NETHERITE_SWORD)&&s.menu.getCarried().getHoverName().getString().equals("native base identity")&&!Workpiece.has(s.menu.getCarried())&&s.counts(1),"Ordinary native transform retains base components and native one-per-slot consumption");
        }
        try(var s=new Session(helper,ordinary,holder(graph("[{\"item\":\"minecraft:diamond\"}]",false)))) {
            s.fill(1);s.click(ClickType.PICKUP,0);check(s.menu.getCarried().is(Items.DIAMOND)&&s.counts(0),"Valid graph entrance wins the ordinary smithing transform collision");
        }
        var trim=new RecipeHolder<>(ResourceLocation.parse("test:smithing_trim"),new SmithingTrimRecipe(Ingredient.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),Ingredient.of(Items.IRON_CHESTPLATE),Ingredient.of(Items.AMETHYST_SHARD)));
        try(var s=new Session(helper,trim)) {
            s.put(0,Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,1);s.put(1,Items.IRON_CHESTPLATE,1);s.put(2,Items.AMETHYST_SHARD,1);s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().is(Items.IRON_CHESTPLATE)&&s.menu.getCarried().has(DataComponents.TRIM)&&!Workpiece.has(s.menu.getCarried())&&s.counts(0),"Ordinary dynamic smithing trim remains fully native, not graph-authorized");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void previewGuardsAndCompleteBundleCapacityCoverPickupSwapOffhandAndEmptySettlement(GameTestHelper helper) {
        try(var s=new Session(helper,holder(graph("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":2}]}]",false)))) {
            s.fill(3);var random=helper.getLevel().random;random.setSeed(21807);long expected=random.nextLong();random.setSeed(21807);
            for(int i=0;i<6;i++)s.menu.createResult();
            check(s.menu.getSlot(3).remove(64).isEmpty()&&s.menu.getSlot(3).container.removeItemNoUpdate(0).isEmpty(),"Direct native result extraction cannot remove a representative preview");
            s.menu.getSlot(3).onTake(s.player,new ItemStack(Items.DIAMOND));s.click(ClickType.THROW,0);s.click(ClickType.CLONE,2);
            check(s.counts(3)&&s.menu.getCarried().isEmpty()&&random.nextLong()==expected,"Repeated previews, Q, middle-click and direct onTake neither pay inputs nor draw RNG");
            for(int i=0;i<36;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,63));
            random.setSeed(21809);expected=random.nextLong();random.setSeed(21809);s.click(ClickType.PICKUP,0);
            check(s.counts(3)&&s.menu.getCarried().isEmpty()&&random.nextLong()==expected,"All extras require capacity before first output or graph RNG");
            s.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,62));s.click(ClickType.PICKUP,1);
            check(s.counts(2)&&s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&s.player.getInventory().getItem(0).getCount()==64,"Right pickup delivers the full first stack and all extras");
            s.menu.setCarried(new ItemStack(Items.DIAMOND,63));s.click(ClickType.PICKUP,0);check(s.counts(2)&&s.menu.getCarried().getCount()==63,"Partial cursor capacity cannot truncate a graph round");
            s.menu.setCarried(ItemStack.EMPTY);s.player.getInventory().clearContent();s.player.getInventory().setItem(0,new ItemStack(Items.STONE));s.click(ClickType.SWAP,0);check(s.counts(2),"Occupied hotbar target blocks SWAP");
            s.player.getInventory().setItem(0,ItemStack.EMPTY);s.click(ClickType.SWAP,0);s.click(ClickType.SWAP,40);
            check(s.counts(0)&&s.player.getInventory().getItem(0).is(Items.DIAMOND)&&s.player.getInventory().getItem(0).getCount()==2&&s.player.getOffhandItem().is(Items.DIAMOND)&&s.player.getOffhandItem().getCount()==2&&s.player.getInventory().countItem(Items.EMERALD)==4,"Hotbar and offhand swaps each deliver one whole bundle exactly once");
        }
        try(var s=new Session(helper,holder(graph("[]",false)))) {
            s.fill(2);s.menu.quickMoveStack(s.player,3);check(s.counts(0)&&s.menu.getCarried().isEmpty()&&s.player.getInventory().isEmpty(),"STRICT empty settlement still pays complete native rounds, never a placeholder result");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void codecInputReloadReentryAndThrowAbortBeforeRngAndReleaseMenuScope(GameTestHelper helper) {
        var failures=new ArrayList<String>();
        for(String action:List.of("input","reload","reenter","throw"))try(var s=new Session(helper,holder(graph("[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"smithing\"}}]",false)))) {
            s.fill(2);int[] calls={0};FurnaceCodecFixture.decode=()->{if(calls[0]++!=0)return;switch(action) {
                case "input" -> s.put(1,Items.COPPER_INGOT,2);
                case "reload" -> s.install(holder(graph("[{\"item\":\"minecraft:emerald\"}]",false)));
                case "reenter" -> {s.click(ClickType.PICKUP,0);s.menu.quickMoveStack(s.player,3);}
                case "throw" -> throw new IllegalStateException("smithing codec fixture");
            }};
            var random=helper.getLevel().random;random.setSeed(21811);long expected=random.nextLong();random.setSeed(21811);
            try{s.click(ClickType.PICKUP,0);}finally{FurnaceCodecFixture.decode=()->{};}
            boolean noDraw=random.nextLong()==expected;
            if(calls[0]==0||!noDraw||!s.counts(2)||!s.menu.getCarried().isEmpty())failures.add(action+"(calls="+calls[0]+",noDraw="+noDraw+",counts="+s.counts(2)+",cursor="+s.menu.getCarried()+")");
            s.reset();s.install(holder(graph("[{\"item\":\"minecraft:diamond\"}]",false)));s.fill(1);s.click(ClickType.PICKUP,0);
            check(s.counts(0)&&s.menu.getCarried().is(Items.DIAMOND),"Failed preparation must release the native menu busy scope: "+action);
        }finally{FurnaceCodecFixture.decode=()->{};}
        check(failures.isEmpty(),"Every real output codec mutation/reload/reentry/throw must abort its extraction before RNG and input payment: "+failures);helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void shiftStopsAfterFirstPaidRoundWhenItsRefreshReloadsReentersOrThrows(GameTestHelper helper) {
        var failures=new ArrayList<String>();
        for(String action:List.of("reload","reenter","throw"))try(var s=new Session(helper,holder(graph("[{\"item\":\"minecraft:diamond\",\"count\":2,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"shift-refresh\"}}]",false)))) {
            s.fill(2);boolean[] fired={false};long[] expected={0};var random=helper.getLevel().random;
            FurnaceCodecFixture.decode=()->{
                // Locate the boundary by native input payment, not a particular decode count.
                if(!s.counts(1)||fired[0])return;fired[0]=true;
                // Anchor the RNG at this refresh boundary, independently of the legal first roll.
                random.setSeed(21817);expected[0]=random.nextLong();random.setSeed(21817);
                switch(action) {
                    case "reload" -> s.install(holder(graph("[{\"item\":\"minecraft:emerald\"}]",false)));
                    case "reenter" -> s.menu.quickMoveStack(s.player,3);
                    case "throw" -> throw new IllegalStateException("smithing paid-round refresh fixture");
                }
            };
            try{s.menu.quickMoveStack(s.player,3);}finally{FurnaceCodecFixture.decode=()->{};}
            boolean noDraw=random.nextLong()==expected[0];
            boolean stopped=fired[0]&&s.counts(1)&&s.player.getInventory().countItem(Items.DIAMOND)==2&&s.player.getInventory().countItem(Items.EMERALD)==0&&s.menu.getCarried().isEmpty()&&noDraw;
            if(!stopped)failures.add(action+"(fired="+fired[0]+",remaining="+s.menu.getSlot(1).getItem().getCount()+",diamonds="+s.player.getInventory().countItem(Items.DIAMOND)+",emeralds="+s.player.getInventory().countItem(Items.EMERALD)+",noDraw="+noDraw+")");
            if(stopped) {
                s.install(holder(graph("[{\"item\":\"minecraft:diamond\",\"count\":2}]",false)));s.menu.quickMoveStack(s.player,3);
                check(s.counts(0)&&s.player.getInventory().countItem(Items.DIAMOND)==4,"A separate healthy click recovers after paid-round refresh "+action);
            }
        }finally{FurnaceCodecFixture.decode=()->{};}
        check(failures.isEmpty(),"Shift must keep only its first paid bundle when the following refresh fails; no second RNG/payment until a new click: "+failures);helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void referencesExactAdmissionAndNativeCloseReturnOnlyInputs(GameTestHelper helper) {
        var reference=ResourceLocation.parse("test:smithing_reference");var json=graph("[{\"item\":\"minecraft:diamond\"}]",false).json();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smithing_transform\",\"recipe_id\":\"test:smithing_reference\"}"));
        var graph=new GraphRecipe(json);var nativeRecipe=new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.DIAMOND_SWORD),Ingredient.of(Items.COPPER_INGOT),new ItemStack(Items.NETHERITE_SWORD));
        var nativeHolder=new RecipeHolder<>(reference,nativeRecipe);
        try(var s=new Session(helper,holder(graph),nativeHolder)) {
            s.fill(2);check(s.menu.getSlot(3).hasItem(),"Exact native reference supports graph primary replacement");
            var definition=GraphIndex.get(helper.getLevel()).graphs().get(ID.toString());
            check(dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("s"))==nativeHolder,"Reference preview preserves original native holder");
            check(dev.graphassembly.client.StepDescription.machine(nativeRecipe).is(Items.SMITHING_TABLE)&&!dev.graphassembly.client.StepDescription.describe(nativeRecipe).getString().contains("unavailable"),"Supported smithing has a native table icon/description");
            s.install(holder(graph),new RecipeHolder<>(reference,new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.DIAMOND_SWORD),Ingredient.of(Items.COPPER_INGOT),new ItemStack(Items.NETHERITE_SWORD)){}));
            s.menu.createResult();s.click(ClickType.PICKUP,0);check(s.counts(2)&&s.menu.getCarried().isEmpty()&&GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null,"A native subclass cannot become supported through a reference codec roundtrip");
            var trim=graph.json();trim.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smithing_trim\",\"template\":{\"item\":\"minecraft:coast_armor_trim_smithing_template\"},\"base\":{\"item\":\"minecraft:iron_chestplate\"},\"addition\":{\"item\":\"minecraft:amethyst_shard\"}}"));
            s.install(holder(new GraphRecipe(trim)));s.menu.createResult();
            check(GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())==null&&!s.menu.getSlot(3).hasItem()&&s.counts(2),"Admitted native trim still requires its original armor base/template/material, never transform-style graph base replacement");
            trim.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("transitional_item","minecraft:iron_nugget");
            s.install(holder(new GraphRecipe(trim)));s.menu.createResult();
            check(GraphIndex.get(helper.getLevel()).disabledReason(ID.toString())!=null&&GraphIndex.get(helper.getLevel()).disabledReason(ID.toString()).contains("remove node transitional_item"),"Trim rejects a node carrier override instead of silently discarding its native result");
            s.install(holder(graph),nativeHolder);s.menu.createResult();check(s.menu.getSlot(3).hasItem(),"Restoring exact reference restores nonconsuming preview");
            s.menu.removed(s.player);
            check(s.counts(0)&&s.player.getInventory().countItem(Items.AMETHYST_SHARD)==2&&s.player.getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)==2&&s.player.getInventory().countItem(Items.COPPER_INGOT)==2&&s.player.getInventory().countItem(Items.DIAMOND)==0,"Native close returns all three inputs, never graph preview");
            s.click(ClickType.PICKUP,0);check(s.menu.getCarried().isEmpty(),"Closed native menu cannot authorize stale extraction");
            var custom=new SmithingMenu(3,s.player.getInventory()){};s.player.containerMenu=custom;
            check(!custom.getSlot(1).mayPlace(new ItemStack(Items.AMETHYST_SHARD)),"Unknown menu subclass cannot acquire graph admission");
        }
        var materials=dev.graphassembly.client.StepDescription.materials(nativeRecipe);
        check(materials.size()==2&&materials.get(0).getString().contains(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE).getHoverName().getString())
            &&materials.get(1).getString().contains(new ItemStack(Items.COPPER_INGOT).getHoverName().getString()),"Real native transform presentation lists template and addition auxiliaries, never an empty getIngredients default or logical base");
        check(dev.graphassembly.client.StepDescription.player(nativeRecipe).equals(dev.graphassembly.client.StepDescription.describe(nativeRecipe)),
            "Standard native smithing player description is the operation itself, not a repeated machine:machine summary");
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void matchedRawGraphNativeIngredientAndResultCodecExceptionsCannotFallBackToOrdinaryUpgrade(GameTestHelper helper) {
        var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:exception_collision"),new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.AMETHYST_SHARD),Ingredient.of(Items.COPPER_INGOT),new ItemStack(Items.NETHERITE_SWORD)));
        var failures=new ArrayList<String>();
        for(String source:List.of("ingredient","native-result-codec")) {
            var json=graph("[{\"item\":\"minecraft:diamond\"}]",false).json();
            var nativeData=json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe");
            if(source.equals("ingredient"))nativeData.add("template",JsonParser.parseString("{\"type\":\"graph_assembly:smithing_ingredient_fixture\"}"));
            else nativeData.getAsJsonObject("result").add("components",JsonParser.parseString("{\"graph_assembly:furnace_codec_fixture\":\"native-template\"}"));
            try(var s=new Session(helper,holder(new GraphRecipe(json)),ordinary)) {
                GraphIndex.get(helper.getLevel());s.fill(2);check(s.menu.getSlot(3).hasItem(),"Native "+source+" initially admits the matched raw graph candidate");
                int[] calls={0};Runnable throwing=()->{calls[0]++;throw new IllegalStateException("smithing native "+source+" fixture");};
                if(source.equals("ingredient"))SmithingIngredientFixture.query=throwing;else FurnaceCodecFixture.decode=throwing;
                var random=helper.getLevel().random;random.setSeed(21813);long expected=random.nextLong();random.setSeed(21813);
                try{s.menu.createResult();s.click(ClickType.PICKUP,0);}finally{SmithingIngredientFixture.query=()->{};FurnaceCodecFixture.decode=()->{};}
                boolean noDraw=random.nextLong()==expected;
                if(calls[0]==0||!s.counts(2)||!s.menu.getCarried().isEmpty()||!noDraw)failures.add(source+"(calls="+calls[0]+",counts="+s.counts(2)+",cursor="+s.menu.getCarried()+",noDraw="+noDraw+")");
            }finally{SmithingIngredientFixture.query=()->{};FurnaceCodecFixture.decode=()->{};}
        }
        check(failures.isEmpty(),"A matched raw graph's real native ingredient/decoded-result exception must hold all inputs, not ordinary-upgrade fallback: "+failures);
        helper.succeed();
    }
}
