package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllRecipeTypes;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeTypeRoutingGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void typedProgressRoutingAndSameIdGraphReloadKeepBucketsCurrent(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            String id="test:typed_progress";
            var json=JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                 {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],"edges":[{"from":"s","to":"f"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject();
            var graph=new GraphRecipe(json);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(id),graph))); GraphIndex.invalidate();
            var index=GraphIndex.get(level);
            check(index.entries(AllRecipeTypes.PRESSING.getType()).size()==1 && index.entries(AllRecipeTypes.CUTTING.getType()).isEmpty(),
                "Only start nodes enter typed entrance buckets");
            check(index.nativeType(id,"f")==AllRecipeTypes.CUTTING.getType(),"Non-start node also captures native type");
            var state=((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph.definition(id), null, "s", "minecraft:iron_ingot"), ()->0)).progress();
            var piece=dev.graphassembly.recipe.Workpiece.write(new ItemStack(Items.IRON_NUGGET),state);
            check(GraphRuntime.lookup(level,piece,AllRecipeTypes.CUTTING.getType(),
                com.simibubi.create.content.kinetics.saw.CuttingRecipe.class,r->true).recipes().size()==1,"Progress routes by captured next-node native type");
            var wrong=GraphRuntime.lookup(level,piece,AllRecipeTypes.PRESSING.getType(),
                com.simibubi.create.content.kinetics.press.PressingRecipe.class,r->true);
            check(wrong.handled() && wrong.recipes().isEmpty(),"Wrong machine keeps progressing input protected");
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").addProperty("type","create:cutting");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(id),new GraphRecipe(json))));
            var reloaded=GraphIndex.get(level);
            check(reloaded!=index && reloaded.entries(AllRecipeTypes.PRESSING.getType()).isEmpty()
                && reloaded.entries(AllRecipeTypes.CUTTING.getType()).size()==1,"Same-ID graph reload replaces typed buckets");
            helper.succeed();
        } finally {manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void typedEntrancesAndCapturedNodeTypesRebuildOnReferenceReload(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var id=ResourceLocation.parse("test:typed_reference");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing","recipe_id":"test:native_reference"}}],"edges":[]}
                """).getAsJsonObject());
            var holder=new RecipeHolder<>(id,graph);
            var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var nativeRecipe=Recipe.CODEC.parse(ops,JsonParser.parseString("""
                {"type":"create:pressing","ingredients":[{"item":"minecraft:iron_ingot"}],"results":[{"id":"minecraft:iron_nugget"}]}
                """)).getOrThrow();
            var referenceId=ResourceLocation.parse("test:native_reference");
            manager.replaceRecipes(List.of(holder,new RecipeHolder<>(referenceId,nativeRecipe))); GraphIndex.invalidate();
            var first=GraphIndex.get(level);
            check(first.nativeType(id.toString(),"s")==nativeRecipe.getType(),"Capture decoded RecipeType identity");
            check(first.entries(nativeRecipe.getType()).size()==1,"Entrance bucket uses actual RecipeType");
            check(first.entries(AllRecipeTypes.CUTTING.getType()).isEmpty(),"Unrelated typed bucket stays empty");
            var pressing=GraphRuntime.lookup(level,new ItemStack(Items.IRON_INGOT),AllRecipeTypes.PRESSING.getType(),
                com.simibubi.create.content.kinetics.press.PressingRecipe.class,r->true);
            check(pressing.recipes().size()==1,"Native typed entrance routes to press");
            var replacement=dev.graphassembly.integration.NativeRecipeTemplates.copy(level,nativeRecipe);
            manager.replaceRecipes(List.of(holder,new RecipeHolder<>(referenceId,replacement)));
            var second=GraphIndex.get(level);
            check(second!=first && second.nativeType(id.toString(),"s")==replacement.getType(),"Reference-holder reload rebuilds captured type without explicit invalidate");
            check(second.entries(replacement.getType()).size()==1,"Reload preserves typed entrance");
            manager.replaceRecipes(List.of(holder));
            var disabled=GraphIndex.get(level);
            check(disabled.graphs().isEmpty() && disabled.entries(nativeRecipe.getType()).isEmpty()
                && disabled.nativeType(id.toString(),"s")==null,"Missing reference leaves neither captured type nor entrance");
            helper.succeed();
        } finally {manager.replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
