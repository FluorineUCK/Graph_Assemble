package dev.graphassembly.gametest;

import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.vanilla.anvil.AnvilRecipe;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Final bounded native-menu contracts; no adapter commit shortcuts. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeAnvilContractsGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:anvil_contract");
    private static GraphRecipe graph(boolean finish) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"repair","start":true,"finish":%s,"recipe":{"type":"graph_assembly:anvil","action":"repair","addition":{"item":"minecraft:iron_ingot"}}}%s],
             "edges":%s,"outputs":[{"results":[{"mode":"all","results":[{"item":"minecraft:diamond","count":2},{"item":"minecraft:emerald","count":3}]}]}]}
            """.formatted(finish,finish?"":",{\"id\":\"finish\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}",
                finish?"[]":"[{\"from\":\"repair\",\"to\":\"finish\"}]")).getAsJsonObject());
    }
    private static ItemStack sword(){var item=new ItemStack(Items.IRON_SWORD);item.setDamageValue(100);return item;}
    private static final class Session implements AutoCloseable {
        final GameTestHelper h;final List<RecipeHolder<?>> saved;final BlockPos pos;
        final net.neoforged.neoforge.common.util.FakePlayer player;final AnvilMenu menu;
        Session(GameTestHelper h,GraphRecipe graph){
            this.h=h;saved=new ArrayList<>(h.getLevel().getRecipeManager().getRecipes());pos=h.absolutePos(new BlockPos(1,1,1));
            player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-contract"));
            h.getLevel().setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);player.experienceLevel=30;player.getAbilities().instabuild=false;
            menu=new AnvilMenu(8,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));player.containerMenu=menu;install(graph);
        }
        void install(GraphRecipe graph){h.getLevel().getRecipeManager().replaceRecipes(graph==null?List.of():List.of(new RecipeHolder<>(ID,graph)));GraphIndex.invalidate();}
        void fill(){menu.getSlot(0).set(sword());menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,3));menu.slotsChanged(menu.getSlot(0).container);}
        void click(ClickType type,int button){menu.clicked(2,button,type,player);}
        boolean unchanged(){return menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getDamageValue()==100&&menu.getSlot(1).getItem().getCount()==3;}
        long count(Item item){long count=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(item))count+=player.getInventory().getItem(i).getCount();return count;}
        public void close(){for(int i=0;i<3;i++)menu.getSlot(i).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;h.getLevel().removeBlock(pos,false);h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilRenameSummaryDistinguishesExactRemovalAndUnspecifiedName(GameTestHelper h) {
        var expected=Map.of(
            "", "rename_remove",
            "  Forge %s  ", "rename_to");
        for(var example:expected.entrySet()) {
            var data=new JsonObject();data.addProperty("action","rename");data.addProperty("name",example.getKey());
            var summary=dev.graphassembly.client.StepDescription.describe(new AnvilRecipe(data)).getContents();
            check(summary instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                &&translated.getKey().equals("graph_assembly.operation.anvil."+example.getValue()),
                "Rename summary uses "+example.getValue());
            var args=((net.minecraft.network.chat.contents.TranslatableContents)summary).getArgs();
            if(example.getKey().isEmpty())check(args.length==0,"Removal summary has no name argument");
            else check(args.length==1&&args[0] instanceof net.minecraft.network.chat.Component name&&name.getString().equals(example.getKey()),
                "Exact rename displays the literal target, including whitespace and percent signs");
        }
        var unspecified=dev.graphassembly.client.StepDescription.describe(new AnvilRecipe(
            JsonParser.parseString("{\"action\":\"rename\"}").getAsJsonObject())).getContents();
        check(unspecified instanceof net.minecraft.network.chat.contents.TranslatableContents translated
            &&translated.getKey().equals("graph_assembly.operation.anvil.rename")&&translated.getArgs().length==0,
            "Unspecified rename retains the generic summary");
        h.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilLateEntrancePredicateReentryAbortsWholeClick(GameTestHelper h) {
        try(var s=new Session(h,null)) {
            s.fill();
            var ordinary=s.menu.getSlot(2).getItem().copy();
            check(ordinary.is(Items.IRON_SWORD)&&ordinary.getDamageValue()<100&&!Workpiece.has(ordinary)&&s.menu.getCost()>0,
                "Fixture starts with a real ordinary repair preview");
            var lateGraph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"type":"graph_assembly:anvil_entrance_ingredient_fixture"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"repair","start":true,"recipe":{"type":"graph_assembly:anvil","action":"repair","addition":{"item":"minecraft:iron_ingot"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"repair","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            s.install(lateGraph);
            check(ItemStack.matches(ordinary,s.menu.getSlot(2).getItem()),"Reload leaves the menu's ordinary preview unchanged");
            boolean[] attempted={false};
            AnvilEntranceIngredientFixture.query=()->{
                if(!attempted[0]&&s.player.containerMenu==s.menu){
                    attempted[0]=true;
                    s.click(ClickType.PICKUP,0);
                }
            };
            try {
                var random=h.getLevel().random;
                random.setSeed(48082);long expected=random.nextLong();random.setSeed(48082);
                s.click(ClickType.PICKUP,0);
                check(attempted[0],"Click invoked the real registered entrance predicate");
                check(s.unchanged()&&s.player.experienceLevel==30&&s.player.getInventory().isEmpty()&&random.nextLong()==expected,
                    "Reentry from the initial entrance predicate aborts the whole click before XP, inputs, graph delivery or RNG");
            } finally {AnvilEntranceIngredientFixture.query=()->{};}
            s.click(ClickType.PICKUP,0);
            var result=Workpiece.read(h.getLevel(),s.menu.getCarried());
            check(result!=null&&result.recentHistory().equals(List.of("repair"))&&s.player.experienceLevel<30,
                "The next independent click can use the newly installed graph");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilDescriptorResolvesWithoutFabricatedResults(GameTestHelper h) {
        var definition=graph(false).definition(ID.toString());
        var preview=dev.graphassembly.integration.GraphPreviewRecipes.resolve(h.getLevel(),definition,definition.nodes().get("repair"));
        h.assertTrue(preview.value() instanceof AnvilRecipe,"Preview retains the native descriptor type");
        var recipe=(AnvilRecipe)preview.value();
        h.assertTrue(recipe.json().getAsJsonObject("addition").get("item").getAsString().equals("minecraft:iron_ingot"),
            "Preview retains the declared addition");
        h.assertTrue(recipe.getResultItem(h.getLevel().registryAccess()).isEmpty(),"Preview does not fabricate a static result");
        h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilStepDescriptionShowsActionAndDeclaredAddition(GameTestHelper h) {
        var recipe=new AnvilRecipe(JsonParser.parseString("""
            {"type":"graph_assembly:anvil","action":"combine","addition":{"item":"minecraft:gold_ingot"}}
            """).getAsJsonObject());
        var description=dev.graphassembly.client.StepDescription.describe(recipe).getContents();
        h.assertTrue(description instanceof net.minecraft.network.chat.contents.TranslatableContents translated
            && translated.getKey().equals("graph_assembly.operation.anvil.combine"),"Description identifies the combine action");
        h.assertTrue(dev.graphassembly.client.StepDescription.machine(recipe).is(Items.ANVIL),"Machine is an anvil");
        var materials=dev.graphassembly.client.StepDescription.materials(
            dev.graphassembly.integration.GraphPreviewRecipes.resolve(h.getLevel(),graph(false).definition(ID.toString()),
                graph(false).definition(ID.toString()).nodes().get("repair")).value());
        h.assertTrue(materials.equals(List.of(new ItemStack(Items.IRON_INGOT).getHoverName()))
            || materials.size()==1&&materials.getFirst().getString().equals(new ItemStack(Items.IRON_INGOT).getHoverName().getString()),
            "Materials show the declared addition, not a graph carrier");
        h.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void markedAnvilRolesValidateIndependentlyAndOnlyPhysicalLeftHistorySurvives(GameTestHelper h) {
        var data=JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"left","start":true,"transitional_item":"minecraft:iron_sword","mark_amounts":{"quality":3},"recipe":{"type":"create:pressing"}},
              {"id":"right","start":true,"ingredient":{"item":"minecraft:quartz"},"transitional_item":"minecraft:iron_sword","mark_amounts":{"quality":99},"recipe":{"type":"create:cutting"}},
              {"id":"repair","workpiece_inputs":[{"item_input":0,"current_nodes":["left","right"]},{"item_input":1,"current_nodes":["left","right"]}],"recipe":{"type":"graph_assembly:anvil","action":"repair"}},
              {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"left","to":"repair"},{"from":"right","to":"repair"},{"from":"repair","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """).getAsJsonObject();var graph=new GraphRecipe(data);var definition=graph.definition(ID.toString());
        var left=Workpiece.write(sword(),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "left", "minecraft:iron_sword"), ()->0)).progress());
        var right=Workpiece.write(sword(),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "right", "minecraft:quartz"), ()->0)).progress());
        for(boolean reversed:List.of(false,true))try(var s=new Session(h,graph)) {
            var first=reversed?right:left;var second=reversed?left:right;s.menu.getSlot(0).set(first.copy());s.menu.getSlot(1).set(second.copy());s.click(ClickType.PICKUP,0);
            var progress=Workpiece.read(h.getLevel(),s.menu.getCarried());var inherited=Workpiece.read(h.getLevel(),first);
            check(progress!=null&&progress.current().equals("repair")&&progress.recentHistory().equals(List.of(reversed?"right":"left","repair"))&&progress.marks().values().equals(inherited.marks().values())
                &&progress.checkpoints().equals(inherited.checkpoints())&&s.menu.getSlot(0).getItem().isEmpty()&&s.menu.getSlot(1).getItem().isEmpty(),"Physical left, not a preferred graph mark or swapped auxiliary, owns anvil history");
        }
        for(String bad:List.of("raw","foreign","wrong-current","tracked-multiple"))try(var s=new Session(h,graph)) {
            var extra=right.copy();if(bad.equals("raw"))extra.remove(GraphAssembly.PROGRESS.get());
            if(bad.equals("foreign"))extra.set(GraphAssembly.PROGRESS.get(),extra.get(GraphAssembly.PROGRESS.get()).replace(ID.toString(),"test:foreign"));
            if(bad.equals("wrong-current"))extra=Workpiece.write(sword(),new Progress(ID.toString(), "right", "finish", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState(null, null), List.of("right","finish")));
            if(bad.equals("tracked-multiple"))extra=right.copyWithCount(2);
            s.menu.getSlot(0).set(left.copy());s.menu.getSlot(1).set(extra);
            // SimpleContainer.setItem clamps unstackable inputs. Exercise the defensive contract
            // with an actual in-place corrupted stack, not an imagined count already clamped to one.
            if(bad.equals("tracked-multiple"))s.menu.getSlot(1).getItem().setCount(2);
            s.menu.slotsChanged(s.menu.getSlot(1).container);
            check(s.menu.getSlot(0).getItem().getCount()==1&&s.menu.getSlot(1).getItem().getCount()==(bad.equals("tracked-multiple")?2:1),
                "Auxiliary fixture actual grid counts must match the requested case: "+bad);
            s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getCount()==1&&s.menu.getSlot(1).getItem().getCount()==(bad.equals("tracked-multiple")?2:1)&&s.player.experienceLevel==30,
                "Invalid "+bad+" auxiliary cannot pay or ordinary-fallback");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilFeesCapacityFinishBundlesAndSwapTargetsAreAtomic(GameTestHelper h) {
        try(var s=new Session(h,graph(true))) {
            s.fill();int nativeCost=s.menu.getCost(),nativeRemainder=3-s.menu.repairItemCountCost;s.player.experienceLevel=0;s.click(ClickType.QUICK_MOVE,0);check(s.unchanged()&&s.count(Items.DIAMOND)==0,"Shift with insufficient native fee cannot bypass finish eligibility");
            s.player.experienceLevel=30;for(int i=0;i<36;i++)s.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var random=h.getLevel().random;random.setSeed(99117);long next=random.nextLong();random.setSeed(99117);s.click(ClickType.PICKUP,0);
            check(s.unchanged()&&s.player.experienceLevel==30&&random.nextLong()==next,"Full capacity rejects entire multioutput finish before fee/material/RNG");
            s.player.getInventory().setItem(0,ItemStack.EMPTY);s.click(ClickType.PICKUP,1);
            check(s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&s.count(Items.EMERALD)==3&&s.menu.getSlot(0).getItem().isEmpty()
                &&s.menu.getSlot(1).getItem().getCount()==nativeRemainder&&s.player.experienceLevel==30-nativeCost,"Right pickup grants whole ALL bundle, pays actual repair once and gives no native sword");
        }
        for(int button:List.of(0,40))try(var s=new Session(h,graph(true))) {
            s.fill();s.player.getInventory().setItem(button,new ItemStack(Items.STONE));s.click(ClickType.SWAP,button);check(s.unchanged()&&s.player.experienceLevel==30,"Occupied hotbar/offhand blocks graph output before payment");
            s.player.getInventory().setItem(button,ItemStack.EMPTY);s.click(ClickType.SWAP,button);
            check(s.player.getInventory().getItem(button).is(Items.DIAMOND)&&s.player.getInventory().getItem(button).getCount()==2&&s.count(Items.EMERALD)==3&&s.menu.getSlot(0).getItem().isEmpty(),"Empty hotbar/offhand receives complete native-qualified graph settlement");
        }
        try(var s=new Session(h,graph(false))) {s.fill();int nativeCost=s.menu.getCost();s.click(ClickType.QUICK_MOVE,0);check(s.count(Items.IRON_SWORD)==1&&s.player.experienceLevel==30-nativeCost&&s.menu.getSlot(0).getItem().isEmpty(),"Shift finishes exactly its available one-single-primary native round and stops");}
        h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void anvilDescriptorCodecRoundtripAndInvalidDefinitionsStayStrict(GameTestHelper h) {
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(String declaration:List.of("{\"type\":\"graph_assembly:anvil\",\"action\":\"repair\",\"addition\":{\"item\":\"minecraft:iron_ingot\"}}","{\"type\":\"graph_assembly:anvil\",\"action\":\"rename\",\"name\":\"Exact target\"}")) {
            var decoded=(AnvilRecipe)Recipe.CODEC.parse(ops,JsonParser.parseString(declaration)).getOrThrow();
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess(),net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
            try {GraphAssembly.ANVIL_SERIALIZER.get().streamCodec().encode(buffer,decoded);var received=GraphAssembly.ANVIL_SERIALIZER.get().streamCodec().decode(buffer);
                check(decoded.action()==received.action()&&decoded.json().equals(received.json())&&!received.matches(new SingleRecipeInput(sword()),h.getLevel())
                    &&received.assemble(new SingleRecipeInput(sword()),h.getLevel().registryAccess()).isEmpty(),"Descriptor sync roundtrip never grants standalone execution");
            } finally {buffer.release();}
        }
        for(String field:List.of("result","experience","material_count","recipe_id","unknown")) {
            var invalid=graph(false).json();invalid.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").addProperty(field,"minecraft:diamond");
            try(var s=new Session(h,new GraphRecipe(invalid))){check(!GraphIndex.get(h.getLevel()).graphs().containsKey(ID.toString()),"Unknown/synthetic anvil field rejected: "+field);}
        }
        for(String bad:List.of("carrier","role-index","rename-addition","rename-aux")) {
            var invalid=graph(false).json();var node=invalid.getAsJsonArray("nodes").get(0).getAsJsonObject();
            if(bad.equals("carrier"))node.addProperty("transitional_item","minecraft:gold_ingot");
            if(bad.equals("role-index"))node.add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":2,\"current_nodes\":[\"repair\"]}]"));
            if(bad.startsWith("rename"))node.getAsJsonObject("recipe").addProperty("action","rename");
            if(bad.equals("rename-aux")){node.getAsJsonObject("recipe").remove("addition");node.add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":1,\"current_nodes\":[\"repair\"]}]"));}
            try(var s=new Session(h,new GraphRecipe(invalid))){check(!GraphIndex.get(h.getLevel()).graphs().containsKey(ID.toString()),"Invalid descriptor/node contract rejected: "+bad);}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeUpdateInputNameXpCreativeReloadCancelAndReentryAbortClick(GameTestHelper h) {
        for(String mutation:List.of("input","name","xp","creative","reload","cancel","reentry"))try(var s=new Session(h,graph(false))) {
            s.fill();boolean[] once={false};Consumer<AnvilUpdateEvent> listener=event->{
                if(event.getPlayer()!=s.player||s.player.containerMenu!=s.menu||once[0])return;once[0]=true;
                switch(mutation){
                    case "input" -> s.menu.getSlot(0).getItem().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Mutation"));
                    case "name" -> s.menu.setItemName("Callback changed name");
                    case "xp" -> s.player.experienceLevel=0;
                    case "creative" -> s.player.getAbilities().instabuild=true;
                    case "reload" -> s.install(null);
                    case "cancel" -> event.setCanceled(true);
                    case "reentry" -> s.menu.clicked(2,0,ClickType.PICKUP,s.player);
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {var random=h.getLevel().random;random.setSeed(99831);long next=random.nextLong();random.setSeed(99831);s.click(ClickType.PICKUP,0);
                check(once[0]&&s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getCount()==1&&s.menu.getSlot(1).getItem().getCount()==3&&random.nextLong()==next,
                    "Native update "+mutation+" invalidates this click before payment or graph RNG, with no retry/fallback");
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void rawMismatchAndInvalidNativeCountsNeverOrdinaryFallback(GameTestHelper h) {
        var mismatch=graph(false).json();mismatch.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("addition",JsonParser.parseString("{\"item\":\"minecraft:gold_ingot\"}"));
        try(var s=new Session(h,new GraphRecipe(mismatch))){s.fill();s.click(ClickType.PICKUP,0);check(s.unchanged()&&s.player.experienceLevel==30,"Raw matched primary but wrong addition cannot fall back ordinary repair");}
        try(var s=new Session(h,graph(false))){s.fill();s.menu.getSlot(0).getItem().setCount(2);s.menu.slotsChanged(s.menu.getSlot(0).container);check(s.menu.getSlot(0).getItem().getCount()==2,"Invalid-primary fixture must actually contain two items after native container handling");s.click(ClickType.PICKUP,0);check(s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getCount()==2&&s.menu.getSlot(1).getItem().getCount()==3,"Count-two primary cannot be compressed into a single graph workpiece");}
        try(var s=new Session(h,graph(false))) {
            s.fill();Consumer<AnvilUpdateEvent> listener=event->{if(event.getPlayer()==s.player&&s.player.containerMenu==s.menu){var output=sword().copyWithCount(2);output.setDamageValue(0);event.setOutput(output);event.setCost(1);event.setMaterialCost(1);}};
            NeoForge.EVENT_BUS.addListener(listener);try{s.click(ClickType.PICKUP,0);check(s.unchanged()&&s.player.experienceLevel==30,"Host count-two result cannot bypass single carrier contract or ordinary-fallback");}finally{NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void successfulNativeRepairListenerInventoryGiftSurvivesGraphDelivery(GameTestHelper h) {
        try(var s=new Session(h,null)) {
            int[] events={0};Consumer<AnvilRepairEvent> listener=event->{if(event.getEntity()==s.player&&s.player.containerMenu==s.menu){events[0]++;s.player.getInventory().setItem(17,new ItemStack(Items.APPLE,5));event.setBreakChance(0);}};
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                s.fill();s.click(ClickType.PICKUP,0);var nativeResult=s.menu.getCarried().copy();int nativeXp=s.player.experienceLevel;var remainder=s.menu.getSlot(1).getItem().copy();
                check(events[0]==1&&s.player.getInventory().getItem(17).is(Items.APPLE)&&s.player.getInventory().getItem(17).getCount()==5,"Ordinary native successful repair listener legitimately gifts an independent inventory slot");
                s.player.getInventory().clearContent();s.menu.setCarried(ItemStack.EMPTY);s.menu.getSlot(1).set(ItemStack.EMPTY);s.player.experienceLevel=30;s.install(graph(false));s.fill();s.click(ClickType.PICKUP,0);
                var stripped=s.menu.getCarried().copy();stripped.remove(GraphAssembly.PROGRESS.get());
                check(events[0]==2&&ItemStack.matches(stripped,nativeResult)&&Workpiece.has(s.menu.getCarried())&&s.player.experienceLevel==nativeXp&&ItemStack.matches(s.menu.getSlot(1).getItem(),remainder),"Graph listener path still settles native result/payment and exactly one success event");
                check(s.player.getInventory().getItem(17).is(Items.APPLE)&&s.player.getInventory().getItem(17).getCount()==5,
                    "Graph delivery must preserve independent inventory gift made by the actual successful AnvilRepairEvent listener");
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }
}
