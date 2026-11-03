package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** External Item remainder callbacks must not invalidate or reenter a pending native-menu transaction. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCraftingCallbackGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:craft_callback");
    private static final ResourceLocation REFERENCE=ResourceLocation.parse("test:craft_callback_reference");
    private static final String NATIVE="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"graph_assembly:crafting_remainder_fixture"}],"result":{"id":"minecraft:diamond"}}
        """;
    private static GraphRecipe graph(boolean reference) {
        String recipe=reference?"{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:craft_callback_reference\"}":NATIVE;
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":true,"recipe":%s}],"edges":[],
             "outputs":[{"results":[{"item":"minecraft:diamond","chance":0.5}]}]}
            """.formatted(recipe)).getAsJsonObject());
    }
    private static FakePlayer player(GameTestHelper helper,String name){return new FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));}
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;final FakePlayer player;final InventoryMenu menu;final List<RecipeHolder<?>> saved;
        RecipeHolder<GraphRecipe> holder;RecipeHolder<?> nativeHolder;
        Session(GameTestHelper helper,boolean reference) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            player=player(helper,"callback-test");menu=player.inventoryMenu;player.containerMenu=menu;
            holder=new RecipeHolder<>(ID,graph(reference));
            if(reference)nativeHolder=new RecipeHolder<>(REFERENCE,Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(NATIVE)).getOrThrow());
            var recipes=new ArrayList<RecipeHolder<?>>();recipes.add(holder);if(nativeHolder!=null)recipes.add(nativeHolder);
            helper.getLevel().getRecipeManager().replaceRecipes(recipes);GraphIndex.invalidate();
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,2));menu.getSlot(2).set(new ItemStack(CraftingRemainderFixture.ITEM,2));
            menu.slotsChanged(menu.getSlot(1).container);
            check(!menu.getSlot(0).getItem().isEmpty(),"Callback fixture must have a native menu preview");
        }
        void click(){menu.clicked(0,0,ClickType.PICKUP,player);}
        void untouched(){check(menu.getCarried().isEmpty() && menu.getSlot(1).getItem().getCount()==2 && menu.getSlot(2).getItem().getCount()==2,"Invalidated callback must not deliver output or consume either material");}
        @Override public void close(){CraftingRemainderFixture.callback=stack->{};player.getInventory().clearContent();menu.clearCraftingContent();menu.setCarried(ItemStack.EMPTY);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderLiveGridMutationAbortsBeforeRngAndConsumption(GameTestHelper helper) {
        try(var s=new Session(helper,false)) {
            int[] calls={0};
            CraftingRemainderFixture.callback=copy->{calls[0]++;s.menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT,2));};
            var random=helper.getLevel().random;random.setSeed(1301);long expected=random.nextLong();random.setSeed(1301);
            s.click();
            check(calls[0]==1,"Actual native Item remainder callback must execute once");
            check(random.nextLong()==expected,"Live-grid invalidation must abort before RNG");
            s.untouched();check(s.menu.getSlot(1).getItem().is(Items.COPPER_INGOT),"Callback replacement remains untouched, never consumed as old ingredient");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderDefinitionAndReferenceReloadAbortOldPlan(GameTestHelper helper) {
        for(String action:List.of("delete","replace-graph","replace-reference"))try(var s=new Session(helper,true)) {
            CraftingRemainderFixture.callback=copy->{
                var recipes=new ArrayList<RecipeHolder<?>>();
                if(action.equals("replace-graph"))recipes.add(new RecipeHolder<>(ID,new GraphRecipe(s.holder.value().json())));
                else if(!action.equals("delete"))recipes.add(s.holder);
                if(action.equals("replace-reference"))recipes.add(new RecipeHolder<>(REFERENCE,
                    dev.graphassembly.integration.NativeRecipeTemplates.copy(helper.getLevel(),s.nativeHolder.value())));
                else if(!action.equals("delete"))recipes.add(s.nativeHolder);
                helper.getLevel().getRecipeManager().replaceRecipes(recipes);
            };
            var random=helper.getLevel().random;random.setSeed(1403);long expected=random.nextLong();random.setSeed(1403);
            s.click();
            check(random.nextLong()==expected,"Remainder "+action+" must abort before RNG");s.untouched();
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderMenuCursorInventoryAndComponentMutationAbort(GameTestHelper helper) {
        for(String action:List.of("menu","cursor","inventory","component","count"))try(var s=new Session(helper,false)) {
            CraftingRemainderFixture.callback=copy->{
                switch(action) {
                    case "menu" -> s.player.containerMenu=new CraftingMenu(2,s.player.getInventory());
                    case "cursor" -> s.menu.setCarried(new ItemStack(Items.COPPER_INGOT));
                    case "inventory" -> s.player.getInventory().setItem(0,new ItemStack(Items.STONE));
                    case "component" -> s.menu.getSlot(1).getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("callback changed"));
                    case "count" -> s.menu.getSlot(1).getItem().setCount(1);
                    default -> throw new AssertionError(action);
                }
            };
            var random=helper.getLevel().random;random.setSeed(1451);long expected=random.nextLong();random.setSeed(1451);s.click();
            check(random.nextLong()==expected,"Callback "+action+" must invalidate before RNG");
            check(s.menu.getSlot(1).getItem().getCount()==(action.equals("count")?1:2)
                && s.menu.getSlot(2).getItem().getCount()==2,"No extra consumption after callback "+action);
            check(action.equals("cursor")?s.menu.getCarried().is(Items.COPPER_INGOT):s.menu.getCarried().isEmpty(),"Callback cursor change must remain intact without old output");
            if(action.equals("inventory"))check(s.player.getInventory().getItem(0).is(Items.STONE),"Callback inventory change must not be overwritten");
            if(action.equals("menu"))check(s.player.containerMenu!=s.menu,"Transaction must not switch player back to stale menu");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderReentrantResultClicksCannotCommitSecondRound(GameTestHelper helper) {
        try(var s=new Session(helper,false)) {
            int[] calls={0};
            CraftingRemainderFixture.callback=copy->{if(calls[0]++==0){s.click();s.menu.quickMoveStack(s.player,0);}};
            s.click();
            check(calls[0]==1,"Busy remainder callback must swallow reentrant clicked/direct quickMove without a second callback");
            check(s.menu.getSlot(1).getItem().getCount()==1 && s.menu.getSlot(2).getItem().getCount()==1,"Outer transaction commits exactly one round despite reentrant result extraction");
            helper.succeed();
        }
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderCraftingPlayerIsCurrentAndParentRestored(GameTestHelper helper) {
        var original=CommonHooks.getCraftingPlayer();var parent=player(helper,"parent-context");
        try(var s=new Session(helper,false)) {
            var observed=new ArrayList<net.minecraft.world.entity.player.Player>();
            CraftingRemainderFixture.callback=copy->observed.add(CommonHooks.getCraftingPlayer());
            CommonHooks.setCraftingPlayer(parent);s.click();
            check(observed.size()==1 && observed.getFirst()==s.player,"Native remainder callback must see the current transaction player, not parent/null");
            check(CommonHooks.getCraftingPlayer()==parent,"Successful callback must restore parent craftingPlayer");
            helper.succeed();
        } finally {CommonHooks.setCraftingPlayer(original);}
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void remainderExceptionRestoresParentContextAndReleasesBusy(GameTestHelper helper) {
        var original=CommonHooks.getCraftingPlayer();var parent=player(helper,"parent-throw");
        try(var s=new Session(helper,false)) {
            var observed=new ArrayList<net.minecraft.world.entity.player.Player>();
            CraftingRemainderFixture.callback=copy->{observed.add(CommonHooks.getCraftingPlayer());throw new IllegalStateException("fixture remainder exception");};
            CommonHooks.setCraftingPlayer(parent);
            boolean threw=false;try{s.click();}catch(RuntimeException expected){threw=true;}
            check(threw,"Fixture exception must actually exercise callback exceptional exit");
            check(observed.size()==1 && observed.getFirst()==s.player,"Throwing callback must also run with the current crafting player");
            check(CommonHooks.getCraftingPlayer()==parent,"Exception must restore parent craftingPlayer, not clear or leak transaction player");s.untouched();
            CraftingRemainderFixture.callback=copy->{};s.click();
            check(s.menu.getSlot(1).getItem().getCount()==1,"Exceptional callback must release busy so a later valid operation can commit");
            helper.succeed();
        } finally {CommonHooks.setCraftingPlayer(original);}
    }
}
