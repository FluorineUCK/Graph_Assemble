package dev.graphassembly.gametest;

import dev.graphassembly.recipe.RecipeMode;

import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("graph_assembly_examples")
@PrefixGameTestTemplate(false)
public final class KubeNativeRecipesGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void upstreamRecipeObjectsKeepSugarAndStayNested(GameTestHelper helper) {
        var level = helper.getLevel();
        var graph = GraphIndex.get(level).graphs().get("test:kube_native_heat");
        helper.assertTrue(graph != null, "Kube native heat graph must load: " + GraphIndex.get(level).disabledReasons());
        var recipe = (AbstractCookingRecipe) NativeSteps.template(level, graph, graph.nodes().get("heat"), new ItemStack(Items.COPPER_INGOT));
        helper.assertTrue(recipe.getCookingTime() == 60 && Math.abs(recipe.getExperience() - 0.7F) < 0.001F,
            "Upstream cookingTime/xp must survive");
        helper.assertTrue(graph.outputRules().getFirst().leaves().getFirst().count() == 2, "Output count sugar must survive");
        helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.parse("test:graph_nested_heat")).isEmpty(),
            "Embedded recipe must not be independently registered");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void upstreamSnapshotsComponentsAndVanillaSugar(GameTestHelper helper) {
        var index = GraphIndex.get(helper.getLevel());
        for (var id : java.util.List.of("snapshot", "reuse", "shaped", "shapeless", "stonecutting", "campfire", "smithing", "trim", "raw", "checked")) {
            var graph = index.graphs().get("test:kube_native_" + id);
            helper.assertTrue(graph != null, "Missing/disabled sugar graph " + id + ": " + index.disabledReasons());
            helper.assertTrue((graph.nodes().get("s").operation().recipeMode() == RecipeMode.NATIVE), "KJS recipe is explicitly complete");
        }
        var before = index.graphs().get("test:kube_native_snapshot").nodes().get("s").operation().recipe();
        var after = index.graphs().get("test:kube_native_reuse").nodes().get("s").operation().recipe();
        helper.assertTrue(before.get("cookingtime").getAsInt() == 31 && after.get("cookingtime").getAsInt() == 99,
            "Reused recipe snapshots each capture their own settings");
        for (var id : java.util.List.of("shaped", "shapeless"))
            helper.assertTrue(index.graphs().get("test:kube_native_"+id).nodes().get("s").operation().recipe().get("type").getAsString().equals("minecraft:crafting_"+id),
                "Normal upstream crafting selects its final vanilla serializer");
        var output = index.graphs().get("test:kube_native_snapshot").outputRules().getFirst().leaves().getFirst();
        helper.assertTrue(NativeSteps.output(helper.getLevel(), output).getDamageValue() == 5, "Item.of component survives graph output");
        var rawNode = index.graphs().get("test:kube_native_raw").nodes().get("s");
        helper.assertTrue(rawNode.settlement().outputRules().getFirst().leaves().getFirst().count() == 2 && rawNode.settlement().scrapLeaves().getFirst().item().equals("minecraft:flint"),
            "NodeDefinition output and scrap also accept typed/count sugar");
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipes().stream().noneMatch(r -> r.id().getPath().startsWith("graph_nested_")),
            "No graph-only native recipes leak into the recipe manager");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("minecraft:iron_ingot_from_smelting_raw_iron")).isPresent(),
            "Rejecting an existing recipe does not remove it");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void upstreamCreateBuildersPreserveMachineParameters(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("kubejs_create")) { helper.succeed(); return; }
        var level = helper.getLevel();
        var index = GraphIndex.get(level);
        for (var id : java.util.List.of("deploy", "cut", "mix", "compact", "fill", "mill")) {
            var graph = index.graphs().get("test:kube_native_" + id);
            helper.assertTrue(graph != null, "Create sugar graph disabled " + id + ": " + index.disabledReasons());
            var recipe = NativeSteps.create(level, graph, graph.nodes().get("s"), new ItemStack(Items.IRON_NUGGET));
            helper.assertTrue(recipe.getRollableResults().getFirst().getStack().is(Items.IRON_NUGGET), "Graph primary result is replaced");
            switch (id) {
                case "deploy" -> helper.assertTrue(recipe.getIngredients().size() == 2 && ((com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe) recipe).shouldKeepHeldItem(), "keepHeldItem and exact input count");
                case "cut" -> helper.assertTrue(recipe.getProcessingDuration() == 37 && recipe.getRollableResults().size() == 2 && recipe.getRollableResults().get(1).getChance() == 0.25F, "processingTime and CreateItem chance");
                case "mix" -> helper.assertTrue(recipe.getIngredients().size() == 3 && recipe.getFluidIngredients().getFirst().amount() == 250 && recipe.getRequiredHeat().name().equals("HEATED"), "Sized input spread, fluid-first and heated");
                case "compact" -> helper.assertTrue(recipe.getRequiredHeat().name().equals("SUPERHEATED"), "superheated");
                case "fill" -> helper.assertTrue(recipe.getIngredients().size() == 1 && recipe.getFluidIngredients().getFirst().amount() == 250, "filling fluid first, no duplicated primary");
                case "mill" -> helper.assertTrue(recipe.getProcessingDuration() == 51, "milling processingTime");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "kube_native_reload", timeoutTicks = 600)
    public static void upstreamTemplatesRebuildCleanlyOnLiveReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var id = ResourceLocation.parse("test:kube_native_heat");
        var before = level.getRecipeManager().byKey(id).orElseThrow().value();
        var server = level.getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, error) -> server.execute(() -> {
            if (error != null) { helper.fail("KubeJS live reload failed: " + error); return; }
            try {
                helper.assertTrue(level.getRecipeManager().byKey(id).orElseThrow().value() != before, "Reload must replace graph snapshot");
                var graph = GraphIndex.get(level).graphs().get(id.toString());
                helper.assertTrue(graph != null && (graph.nodes().get("heat").operation().recipeMode() == RecipeMode.NATIVE), "Reload rebuilds executable native nodes");
                helper.assertTrue(level.getRecipeManager().getRecipes().stream().noneMatch(r -> r.id().getPath().startsWith("graph_nested_")),
                    "Reload must not leak embedded native recipes");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("KubeJS reload assertion: " + failure); }
        }));
    }
}
