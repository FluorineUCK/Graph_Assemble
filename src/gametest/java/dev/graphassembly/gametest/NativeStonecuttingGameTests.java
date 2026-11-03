package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

/** Exercises the real native selection and pickup path, never a direct runtime completion. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeStonecuttingGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void graphOnlyInputCanBeSelectedAndPickedUpInNativeMenu(GameTestHelper helper) {
        String id="test:stonecutting_start";
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"stonecutting-test"));
        var menu=new StonecutterMenu(1,player.getInventory());
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:stonecutting",
                    "ingredient":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:iron_nugget"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"start","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(java.util.List.of(new RecipeHolder<>(ResourceLocation.parse(id),graph)));
            GraphIndex.invalidate();
            player.containerMenu=menu;
            player.getInventory().clearContent();
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT,2));
            menu.slotsChanged(menu.container);
            check(menu.getRecipes().size()==1 && menu.hasInputItem(),
                "Graph-only input must expose one choice in the native stonecutter recipe grid");
            check(menu.clickMenuButton(player,0) && menu.getSelectedRecipeIndex()==0,
                "The native menu button must select the graph operation");
            check(!menu.getSlot(1).getItem().isEmpty() && menu.getSlot(0).getItem().getCount()==2,
                "Selection must preview without consuming input");
            menu.clicked(1,0,ClickType.PICKUP,player);
            var result=menu.getCarried();
            check(result.is(Items.IRON_NUGGET) && result.getCount()==1,
                "Native result pickup must deliver exactly one graph carrier");
            var state=Workpiece.read(level,result);
            check(state!=null && state.graphId().equals(id) && state.current().equals("start"),
                "Native pickup must advance the selected graph node once");
            check(menu.getSlot(0).getItem().is(Items.IRON_INGOT) && menu.getSlot(0).getItem().getCount()==1,
                "One graph round must consume exactly one physical input");
            helper.succeed();
        } finally {
            menu.container.clearContent();
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().clearContent();
            player.containerMenu=player.inventoryMenu;
            manager.replaceRecipes(saved); GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
