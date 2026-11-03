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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

/** Native menu acceptance: no direct graph query/commit shortcuts. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCraftingGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void inventoryMenuPickupStartsGraphAndConsumesExactlyOneRound(GameTestHelper helper) {
        String id="test:inventory_crafting_start";
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        // Never log in a player: login sync invokes optional mod network channels before this test.
        // NeoForge supplies a no-op connection while retaining a real ServerPlayer/InventoryMenu.
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"crafting-test"));
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"start","start":true,"recipe":{"type":"minecraft:crafting_shapeless",
                    "ingredients":[{"item":"minecraft:iron_ingot"}],"result":{"id":"minecraft:iron_nugget"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"start","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(java.util.List.of(new RecipeHolder<>(ResourceLocation.parse(id),graph)));
            GraphIndex.invalidate();
            InventoryMenu menu=player.inventoryMenu;
            player.containerMenu=menu;
            player.getInventory().clearContent();
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,2));
            menu.slotsChanged(menu.getSlot(1).container);
            check(!menu.getSlot(0).getItem().isEmpty(),"Raw crafting graph must appear in the real InventoryMenu result slot");
            check(menu.getSlot(1).getItem().getCount()==2,"Preview must not consume ingredients");
            menu.clicked(0,0,ClickType.PICKUP,player);
            var result=menu.getCarried();
            check(result.is(Items.IRON_NUGGET) && result.getCount()==1,"PICKUP must deliver exactly one transitional workpiece");
            var state=Workpiece.read(level,result);
            check(state!=null && state.graphId().equals(id) && state.current().equals("start"),"Native result pickup must advance primary graph progress once");
            check(menu.getSlot(1).getItem().is(Items.IRON_INGOT) && menu.getSlot(1).getItem().getCount()==1,"One pickup consumes exactly one material unit");
            helper.succeed();
        } finally {
            player.getInventory().clearContent();
            player.inventoryMenu.clearCraftingContent();
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            manager.replaceRecipes(saved); GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
