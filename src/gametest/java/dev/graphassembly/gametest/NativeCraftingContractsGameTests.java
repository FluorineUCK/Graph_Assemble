package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Transactions exercised only through real vanilla menu, slot and click methods. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCraftingContractsGameTests {
    private static final String ID="test:native_crafting_contract";
    private static final String SINGLE="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"}],"result":{"id":"minecraft:iron_nugget"}}
        """;
    private static GraphRecipe graph(String recipe,boolean finish,String carrier,String outputs) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"%s",
             "nodes":[{"id":"start","start":true,"finish":%s,"recipe":%s}%s],
             "edges":%s,"outputs":[{"results":%s}]}
            """.formatted(carrier,finish,recipe,finish?"":",{\"id\":\"finish\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}",
                finish?"[]":"[{\"from\":\"start\",\"to\":\"finish\"}]",outputs)).getAsJsonObject());
    }
    private static GraphRecipe graph(String recipe){return graph(recipe,false,"minecraft:iron_nugget","[{\"item\":\"minecraft:diamond\"}]");}
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper; final FakePlayer player; final List<RecipeHolder<?>> saved;
        AbstractContainerMenu menu;
        Session(GameTestHelper helper) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            player=new FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"craft-contract"));
            inventory();
        }
        void install(GraphRecipe graph,RecipeHolder<?>... extra) {
            var recipes=new ArrayList<RecipeHolder<?>>();recipes.add(new RecipeHolder<>(ResourceLocation.parse(ID),graph));recipes.addAll(List.of(extra));
            helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        }
        void inventory(){menu=player.inventoryMenu;player.containerMenu=menu;reset();}
        void workbench(){menu=new CraftingMenu(1,player.getInventory(),ContainerLevelAccess.create(helper.getLevel(),helper.absolutePos(BlockPos.ZERO)));player.containerMenu=menu;reset();}
        void reset(){player.getInventory().clearContent();menu.setCarried(ItemStack.EMPTY);for(int i=1;i<=cells();i++)menu.getSlot(i).set(ItemStack.EMPTY);refresh();}
        int cells(){return menu instanceof CraftingMenu?9:4;}
        void put(int slot,Item item,int count){menu.getSlot(slot).set(new ItemStack(item,count));}
        void refresh(){menu.slotsChanged(menu.getSlot(1).container);}
        void click(ClickType type,int button){menu.clicked(0,button,type,player);}
        void fullInventory(){for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
        long count(Item item){long total=0;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(item))total+=player.getInventory().getItem(i).getCount();return total;}
        @Override public void close(){player.getInventory().clearContent();player.inventoryMenu.clearCraftingContent();menu.setCarried(ItemStack.EMPTY);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void shapedMenusHonorMirrorOffsetHolesAndRepeatedCells(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(graph("""
                {"type":"minecraft:crafting_shaped","pattern":["A "," B"],"key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:stick"}},"result":{"id":"minecraft:iron_nugget"}}
                """));
            s.workbench();
            for(var cells:List.of(new int[]{5,9},new int[]{6,8},new int[]{2,6})) {
                s.reset();s.put(cells[0],Items.IRON_INGOT,2);s.put(cells[1],Items.STICK,2);s.refresh();
                check(!s.menu.getSlot(0).getItem().isEmpty(),"Workbench shaped mirror/offset preview missing");
                s.click(ClickType.PICKUP,0);
                check(Workpiece.has(s.menu.getCarried()),"Workbench pickup must produce graph workpiece");
                check(s.menu.getSlot(cells[0]).getItem().getCount()==1 && s.menu.getSlot(cells[1]).getItem().getCount()==1,"Consume each mapped physical cell once");
            }
            s.inventory();s.put(2,Items.IRON_INGOT,1);s.put(3,Items.STICK,1);s.refresh();s.click(ClickType.PICKUP,1);
            check(Workpiece.has(s.menu.getCarried()),"Backpack accepts mirrored 2x2 shaped recipe with right-click full round");
            s.install(graph("""
                {"type":"minecraft:crafting_shaped","pattern":["ABA"],"key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:stick"}},"result":{"id":"minecraft:iron_nugget"}}
                """));
            s.inventory();s.put(1,Items.IRON_INGOT,2);s.put(2,Items.STICK,1);s.refresh();
            check(s.menu.getSlot(0).getItem().isEmpty(),"Count two cannot replace repeated cells or fit 3-wide recipe in 2x2");
            s.workbench();s.put(4,Items.IRON_INGOT,1);s.put(5,Items.STICK,1);s.put(6,Items.IRON_INGOT,1);s.refresh();s.click(ClickType.PICKUP,0);
            check(Workpiece.has(s.menu.getCarried()) && s.menu.getSlot(4).getItem().isEmpty() && s.menu.getSlot(6).getItem().isEmpty(),"Repeated symbols are separate consumed logical roles");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void directShiftAndClickPathsNeverExtractPreview(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(graph(SINGLE));s.put(1,Items.IRON_INGOT,3);s.refresh();
            var definition=GraphIndex.get(helper.getLevel()).graphs().get(ID);
            check(dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("start")).value() instanceof CraftingRecipe,
                "Shared display resolver must resolve native crafting without Create materialization/casts");
            var before=s.menu.getSlot(1).getItem().copy();var preview=s.menu.getSlot(0).getItem().copy();
            s.click(ClickType.CLONE,2);s.click(ClickType.THROW,0);
            check(s.menu.getCarried().isEmpty() && ItemStack.matches(before,s.menu.getSlot(1).getItem()),"CLONE and THROW cannot grant preview or consume inputs");
            check(s.menu.getSlot(0).remove(1).isEmpty(),"Direct ResultSlot.remove must not grant preview");
            s.menu.getSlot(0).onTake(s.player,preview);
            check(ItemStack.matches(before,s.menu.getSlot(1).getItem()),"Direct onTake cannot trigger native double consumption");
            check(s.menu.getSlot(0).container.removeItem(0,1).isEmpty() && s.menu.getSlot(0).container.removeItemNoUpdate(0).isEmpty(),"Direct result container removal cannot grant preview");
            s.menu.quickMoveStack(s.player,0);
            check(s.menu.getSlot(1).getItem().isEmpty() && s.count(Items.IRON_NUGGET)==3,"Direct quickMove must continuously revalidate and commit one workpiece per input");
            for(int i=0;i<36;i++)if(!s.player.getInventory().getItem(i).isEmpty())check(Workpiece.has(s.player.getInventory().getItem(i)),"Shift output must be actual progress, not copied preview");
            s.reset();s.put(1,Items.IRON_INGOT,2);s.refresh();s.click(ClickType.QUICK_MOVE,0);
            check(s.count(Items.IRON_NUGGET)==2 && s.menu.getSlot(1).getItem().isEmpty(),"clicked QUICK_MOVE must also repeat full transactions");
            s.reset();s.put(1,Items.IRON_INGOT,2);s.refresh();s.click(ClickType.SWAP,0);
            check(Workpiece.has(s.player.getInventory().getItem(0)) && s.menu.getSlot(1).getItem().getCount()==1,"Empty hotbar SWAP commits one round");
            s.click(ClickType.SWAP,0);check(s.menu.getSlot(1).getItem().getCount()==1,"Occupied SWAP target blocks without consuming");
            s.click(ClickType.SWAP,40);check(Workpiece.has(s.player.getInventory().getItem(40)) && s.menu.getSlot(1).getItem().isEmpty(),"Empty offhand SWAP commits one round");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void staleGridAndSameIdReloadCannotTakeOldResult(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var graph=graph(SINGLE);s.install(graph);s.put(1,Items.IRON_INGOT,2);s.refresh();
            check(!s.menu.getSlot(0).getItem().isEmpty(),"Old preview fixture");
            // getItem mutation bypasses slotsChanged, exercising transaction rather than preview refresh.
            s.menu.getSlot(1).getItem().setCount(0);s.click(ClickType.PICKUP,0);s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty(),"Silent input removal must not allow first or second click to take stale preview");
            s.put(1,Items.IRON_INGOT,2);s.refresh();
            var changed=graph.json();changed.add("ingredient",JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"));
            s.install(new GraphRecipe(changed));s.click(ClickType.PICKUP,0);s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && s.menu.getSlot(1).getItem().getCount()==2,"Same-ID graph reload must reject stale definition without explicit invalidate");
            var nativeRecipe=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(SINGLE)).getOrThrow();
            var reference=graph("{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:craft_reference\"}");
            s.install(reference,new RecipeHolder<>(ResourceLocation.parse("test:craft_reference"),nativeRecipe));s.refresh();
            check(!s.menu.getSlot(0).getItem().isEmpty(),"Reference graph preview");
            s.install(reference);s.click(ClickType.PICKUP,0);s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && s.menu.getSlot(1).getItem().getCount()==2,"Removed native reference invalidates cached result without consumption");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remaindersComponentsAndCapacityAreAtomic(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(graph(SINGLE,false,"minecraft:iron_ingot","[{\"item\":\"minecraft:diamond\"}]"));
            var named=new ItemStack(Items.IRON_INGOT,2);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("kept component"));
            s.menu.getSlot(1).set(named);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().getHoverName().getString().equals("kept component") && Workpiece.has(s.menu.getCarried()),"Same-item carrier retains primary components");
            s.install(graph("""
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:honey_bottle"}],"result":{"id":"minecraft:iron_nugget"}}
                """));
            s.reset();s.put(1,Items.IRON_INGOT,2);s.put(2,Items.HONEY_BOTTLE,2);s.fullInventory();s.refresh();
            s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && s.menu.getSlot(1).getItem().getCount()==2 && s.menu.getSlot(2).getItem().getCount()==2,"Full inventory must reject overflowing bottle remainder before consuming");
            s.player.getInventory().setItem(0,ItemStack.EMPTY);s.click(ClickType.PICKUP,0);
            check(Workpiece.has(s.menu.getCarried()) && s.count(Items.GLASS_BOTTLE)==1 && s.menu.getSlot(2).getItem().getCount()==1,"Remainder over retained ingredient goes to inventory once");
            s.reset();s.put(1,Items.IRON_INGOT,1);s.put(2,Items.HONEY_BOTTLE,1);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getSlot(2).getItem().is(Items.GLASS_BOTTLE) && s.count(Items.GLASS_BOTTLE)==0,"Empty original cell receives native remainder first");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void probabilisticBundlesReserveEveryPossibleFirstItemWithoutRng(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.install(graph(SINGLE,true,"minecraft:iron_nugget","""
                [{"mode":"all","results":[{"item":"minecraft:diamond","chance":0.5},{"item":"minecraft:emerald"}]}]
                """));
            s.put(1,Items.IRON_INGOT,2);
            var random=helper.getLevel().random;random.setSeed(719);long expected=random.nextLong();random.setSeed(719);
            for(int i=0;i<8;i++)s.refresh();
            check(random.nextLong()==expected,"Repeated crafting previews must not consume world RNG");
            s.menu.setCarried(new ItemStack(Items.DIAMOND));
            random.setSeed(811);expected=random.nextLong();random.setSeed(811);s.click(ClickType.PICKUP,0);
            check(random.nextLong()==expected && s.menu.getSlot(1).getItem().getCount()==2 && s.menu.getCarried().getCount()==1,
                "Chance may remove diamond first; incompatible emerald first must block before RNG");
            s.menu.setCarried(ItemStack.EMPTY);s.fullInventory();
            random.setSeed(913);expected=random.nextLong();random.setSeed(913);s.click(ClickType.PICKUP,0);
            check(random.nextLong()==expected && s.menu.getCarried().isEmpty() && s.menu.getSlot(1).getItem().getCount()==2,"All possible result bundles must fit before rolling");
            s.player.getInventory().setItem(0,ItemStack.EMPTY);s.click(ClickType.PICKUP,0);
            check(s.menu.getSlot(1).getItem().getCount()==1 && (s.menu.getCarried().is(Items.DIAMOND)||s.menu.getCarried().is(Items.EMERALD)),"Successful pickup commits exactly one random round");
            check(s.menu.getCarried().is(Items.EMERALD) ? s.count(Items.EMERALD)==0 : s.count(Items.EMERALD)==1,"Remaining bundle result must be delivered once to inventory");
            s.install(graph(SINGLE,true,"minecraft:iron_nugget","[{\"item\":\"minecraft:diamond\",\"chance\":0}]"));
            s.reset();s.put(1,Items.IRON_INGOT,1);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getSlot(1).getItem().isEmpty() && s.menu.getCarried().isEmpty(),"Empty rolled result still completes and consumes one round");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void ordinaryCraftingStaysNativeAndUnknownReferenceSubclassIsRejected(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var ordinary=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString("""
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:copper_ingot"}],"result":{"id":"minecraft:diamond"}}
                """)).getOrThrow();
            s.install(graph(SINGLE),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_craft"),ordinary));
            s.put(1,Items.COPPER_INGOT,2);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().is(Items.DIAMOND) && !Workpiece.has(s.menu.getCarried()) && s.menu.getSlot(1).getItem().getCount()==1,"Unrelated raw input retains vanilla recipe consumption and result");
            var collision=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(SINGLE)).getOrThrow();
            s.install(graph(SINGLE),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_collision"),collision));
            s.reset();s.put(1,Items.IRON_INGOT,1);s.refresh();s.click(ClickType.PICKUP,0);
            check(Workpiece.has(s.menu.getCarried()),"Matching raw graph entrance must win over ordinary crafting collision");
            var base=(ShapelessRecipe)Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(SINGLE)).getOrThrow();
            var ingredients=net.minecraft.core.NonNullList.<Ingredient>create();ingredients.addAll(base.getIngredients());
            var unknown=new ShapelessRecipe(base.getGroup(),base.category(),base.getResultItem(helper.getLevel().registryAccess()),ingredients) {};
            check(dev.graphassembly.integration.NativeSteps.supportsExecution(ResourceLocation.parse("minecraft:crafting_shapeless"),base)
                && !dev.graphassembly.integration.NativeSteps.supportsExecution(ResourceLocation.parse("minecraft:crafting_shapeless"),unknown),"Public execution admission must distinguish verified crafting classes from unknown subclasses");
            var reference=graph("{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:unknown_subclass\"}");
            s.install(reference,new RecipeHolder<>(ResourceLocation.parse("test:unknown_subclass"),unknown));
            check(!GraphIndex.get(helper.getLevel()).graphs().containsKey(ID) && GraphIndex.get(helper.getLevel()).disabledReason(ID).contains("exact standard"),"Unknown source subclass must be rejected before codec erases subclass semantics");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void twoWorkpieceRolesPreserveOnlyPrimaryAndRejectAmbiguity(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"body","start":true,"rewards":{"quality":3},"recipe":{"type":"create:pressing"}},
                  {"id":"insert","start":true,"ingredient":{"item":"minecraft:copper_ingot"},"rewards":{"quality":99},"recipe":{"type":"create:cutting"}},
                  {"id":"join","checkpoint":true,"recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_nugget"},{"item":"minecraft:iron_nugget"}],"result":{"id":"minecraft:iron_nugget"}},
                   "workpiece_inputs":[{"item_input":0,"current_nodes":["body"]},{"item_input":1,"current_nodes":["insert"]}]},
                  {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"body","to":"join"},{"from":"insert","to":"join"},{"from":"join","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            s.install(graph);
            var definition=graph.definition(ID);
            var main=Workpiece.write(new ItemStack(Items.IRON_NUGGET),GraphEngine.complete(definition,null,"body","minecraft:iron_ingot",()->0).state());
            var auxiliary=Workpiece.write(new ItemStack(Items.IRON_NUGGET),GraphEngine.complete(definition,null,"insert","minecraft:copper_ingot",()->0).state());
            for(boolean reverse:List.of(false,true)) {
                s.reset();s.menu.getSlot(reverse?1:4).set(main.copy());s.menu.getSlot(reverse?4:1).set(auxiliary.copy());s.refresh();s.click(ClickType.PICKUP,0);
                var progress=Workpiece.read(helper.getLevel(),s.menu.getCarried());
                check(progress!=null && progress.current().equals("join") && progress.marks().get("quality")==3,"Only logical primary progress advances, independent of physical slot");
                check(!progress.checkpoints().contains("insert") && s.menu.getSlot(1).getItem().isEmpty() && s.menu.getSlot(4).getItem().isEmpty(),"Auxiliary history is not merged and each workpiece consumed once");
            }
            s.reset();s.menu.getSlot(1).set(main.copy());s.put(4,Items.IRON_NUGGET,1);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && Workpiece.has(s.menu.getSlot(1).getItem()),"Ordinary material cannot fill declared workpiece role");
            var undeclared=graph.json();undeclared.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonArray("workpiece_inputs").remove(1);
            s.install(new GraphRecipe(undeclared));s.reset();s.menu.getSlot(1).set(main.copy());s.menu.getSlot(4).set(auxiliary.copy());s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && Workpiece.has(s.menu.getSlot(4).getItem()),"Undeclared progressing auxiliary remains protected");
            var ambiguous=graph.json();ambiguous.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonArray("workpiece_inputs").get(1).getAsJsonObject().add("current_nodes",JsonParser.parseString("[\"body\"]"));
            s.install(new GraphRecipe(ambiguous));s.reset();var other=main.copy();other.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("another legal primary"));
            s.menu.getSlot(1).set(main.copy());s.menu.getSlot(4).set(other);s.refresh();s.click(ClickType.PICKUP,0);
            check(s.menu.getCarried().isEmpty() && !s.menu.getSlot(1).getItem().isEmpty() && !s.menu.getSlot(4).getItem().isEmpty(),"Different legal primary component identities are ambiguous, never ordinary fallback");
            s.menu.getSlot(4).set(main.copy());s.refresh();s.click(ClickType.PICKUP,0);
            check(Workpiece.has(s.menu.getCarried()),"Equivalent components/progress in separate cells are interchangeable");
            helper.succeed();
        }
    }
}
