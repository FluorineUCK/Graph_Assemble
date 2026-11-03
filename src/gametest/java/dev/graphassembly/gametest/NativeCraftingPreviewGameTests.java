package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.GraphPreviewRecipes;
import dev.graphassembly.recipe.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCraftingPreviewGameTests {
    private static final String ID="test:craft_preview";
    private static final String SINGLE="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:stick"}],"result":{"id":"minecraft:diamond"}}
        """;
    private static GraphRecipe graph(String nativeRecipe) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:copper_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":%s}],"edges":[]}
            """.formatted(nativeRecipe)).getAsJsonObject());
    }
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeReferencesKeepOriginalHolderAndUnknownSubclassPreview(GameTestHelper helper) {
        var manager=helper.getLevel().getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var ops=helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var base=(ShapelessRecipe)Recipe.CODEC.parse(ops,JsonParser.parseString(SINGLE)).getOrThrow();
            var ingredients=net.minecraft.core.NonNullList.<Ingredient>create();ingredients.addAll(base.getIngredients());
            var unknown=new ShapelessRecipe(base.getGroup(),base.category(),base.getResultItem(helper.getLevel().registryAccess()),ingredients){};
            for(var nativeRecipe:List.of(base,unknown)) {
                var reference=new RecipeHolder<>(ResourceLocation.parse("test:preview_reference"),nativeRecipe);
                var graph=graph("{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\"test:preview_reference\"}");
                manager.replaceRecipes(List.of(reference,new RecipeHolder<>(ResourceLocation.parse(ID),graph)));GraphIndex.invalidate();
                var definition=graph.definition(ID);var before=Recipe.CODEC.encodeStart(ops,nativeRecipe).getOrThrow();
                var preview=GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("s"));
                check(preview==reference && preview.value()==nativeRecipe,"recipe_id preview must preserve the exact native holder and subclass identity");
                check(before.equals(Recipe.CODEC.encodeStart(ops,nativeRecipe).getOrThrow()),"Reference display must not rewrite primary/native recipe materials");
                check(preview.value().getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT))
                    && !preview.value().getIngredients().getFirst().test(new ItemStack(Items.COPPER_INGOT)),"Reference primary stays native, not graph entrance replacement");
                if(nativeRecipe==unknown) {
                    check(!GraphIndex.get(helper.getLevel()).graphs().containsKey(ID),"Unknown native subclass remains execution-rejected");
                    check(GraphPreviewRecipes.graphs(helper.getLevel()).stream().anyMatch(g->g.id().equals(ID)),"Execution-rejected native reference still remains viewable");
                }
            }
            helper.succeed();
        } finally {manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void inlineCraftingPreviewKeepsFullEntranceIngredientAndShape(GameTestHelper helper) {
        var ops=helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var named=new ItemStack(Items.IRON_INGOT);named.set(DataComponents.CUSTOM_NAME,Component.literal("preview predicate"));
        var entrances=List.of(Ingredient.of(ItemTags.PLANKS),Ingredient.of(Items.IRON_INGOT,Items.COPPER_INGOT),
            DataComponentIngredient.of(false,DataComponents.CUSTOM_NAME,Component.literal("preview predicate"),Items.IRON_INGOT));
        var shaped="""
            {"type":"minecraft:crafting_shaped","pattern":[" A","B "],"key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:stick"}},"result":{"id":"minecraft:diamond"}}
            """;
        var samples=List.of(new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.COPPER_INGOT),named,new ItemStack(Items.STICK));
        for(String nativeRecipe:List.of(SINGLE,shaped))for(var entrance:entrances) {
            var data=graph(nativeRecipe).json();data.add("ingredient",Ingredient.CODEC.encodeStart(ops,entrance).getOrThrow());
            var definition=new GraphRecipe(data).definition(ID);
            var preview=GraphPreviewRecipes.resolve(helper.getLevel(),definition,definition.nodes().get("s")).value();
            var logical=preview.getIngredients().stream().filter(i->!i.isEmpty()).toList();
            check(Ingredient.CODEC.encodeStart(ops,logical.getFirst()).getOrThrow().equals(Ingredient.CODEC.encodeStart(ops,entrance).getOrThrow()),
                "Inline crafting preview must retain complete tag/alternative/component Ingredient definition");
            for(var sample:samples)check(logical.getFirst().test(sample)==entrance.test(sample),"Inline preview changed entrance predicate for "+sample);
            check(logical.size()==2 && logical.get(1).test(new ItemStack(Items.STICK)),"Auxiliary native material must remain intact");
            if(preview instanceof ShapedRecipe shape)check(shape.getWidth()==2 && shape.getHeight()==2 && shape.getIngredients().getFirst().isEmpty(),"Primary replacement preserves dimensions and empty logical spaces");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void randomNodePreviewHasLegalRepresentativeWithoutWorldRng(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"random-preview"));
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":%s},
                  {"id":"a","finish":true,"recipe":{"type":"create:pressing"}},
                  {"id":"b","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """.formatted(SINGLE)).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID),graph)));GraphIndex.invalidate();
            var menu=player.inventoryMenu;player.containerMenu=menu;
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));menu.getSlot(2).set(new ItemStack(Items.STICK));
            level.random.setSeed(1567);long expected=level.random.nextLong();level.random.setSeed(1567);
            for(int i=0;i<4;i++)menu.slotsChanged(menu.getSlot(1).container);
            check(level.random.nextLong()==expected,"Random branch preview cannot choose using world RNG");
            var preview=menu.getSlot(0).getItem();var state=Workpiece.read(level,preview);
            check(state!=null && state.lockedNext()!=null && dev.graphassembly.core.GraphEngine.incompatibility(graph.definition(ID),state)==null,
                "Random branch preview must carry a legal representative locked target, not a graph-changed tooltip state");
            helper.succeed();
        } finally {player.getInventory().clearContent();player.inventoryMenu.clearCraftingContent();manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
}
