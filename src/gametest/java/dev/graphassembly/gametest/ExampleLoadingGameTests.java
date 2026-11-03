package dev.graphassembly.gametest;

import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate suite for independently enabled examples, not a substitute for the runtime suite. */
@GameTestHolder("graph_assembly_examples")
@PrefixGameTestTemplate(false)
public final class ExampleLoadingGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void selectedRecipesLoadAndPassNativeValidation(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("kubejs"), "Example suite requires KubeJS");
        var expected = System.getProperty("graphAssembly.expectedRecipes", "");
        helper.assertTrue(!expected.isBlank(), "Configure expectedRecipes; an empty suite is not a pass");
        var index = GraphIndex.get(helper.getLevel());
        for (var id : expected.split(",")) {
            var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();
            helper.assertTrue(holder.value() instanceof GraphRecipe, "Not a graph recipe: " + id);
            helper.assertTrue(index.graphs().containsKey(id), "Disabled graph " + id + ": " + index.disabledReason(id));
        }
        helper.succeed();
    }
}
