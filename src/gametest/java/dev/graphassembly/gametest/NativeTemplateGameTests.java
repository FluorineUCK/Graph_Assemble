package dev.graphassembly.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.GraphPreviewRecipes;
import dev.graphassembly.integration.NativeSteps;
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
public final class NativeTemplateGameTests {
    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void nativeFluidFirstTemplateReplacesOnlyLogicalPrimaryAndRetainsByproducts(GameTestHelper helper) {
        var level = helper.getLevel();
        var nativeJson = json("""
            {"type":"create:mixing","ingredients":[
             {"type":"neoforge:single","fluid":"minecraft:water","amount":250},
             {"item":"minecraft:gold_ingot"},{"item":"minecraft:gold_ingot"}],
             "results":[{"id":"minecraft:diamond","count":2},{"id":"minecraft:gravel","chance":0.25}],
             "processing_time":77,"heat_requirement":"heated"}
            """);
        var graph = graph(nativeJson, true).definition("test:native_full_mix");
        var node = graph.nodes().get("s");
        var resolved = NativeSteps.create(level, graph, node, new ItemStack(Items.IRON_INGOT));
        check(resolved.getIngredients().size() == 2, "Full recipe does not gain an extra primary input");
        check(resolved.getIngredients().get(0).test(new ItemStack(Items.IRON_INGOT)), "Logical primary item 0 is replaced after fluid decoding");
        check(resolved.getIngredients().get(1).test(new ItemStack(Items.GOLD_INGOT)), "Duplicate native primary candidate in auxiliary slot must survive");
        check(resolved.getFluidIngredients().size() == 1 && resolved.getFluidIngredients().getFirst().amount() == 250,
            "Fluid-first input remains a fluid and preserves its amount");
        check(resolved.getRollableResults().size() == 2 && resolved.getRollableResults().getFirst().getStack().is(Items.IRON_NUGGET),
            "Full native primary result is replaced, not prepended");
        check(resolved.getRollableResults().getFirst().getStack().getCount() == 1, "Graph owns representative primary result count");
        check(resolved.getRollableResults().get(1).getStack().is(Items.GRAVEL)
            && resolved.getRollableResults().get(1).getChance() == 0.25f, "Native byproduct and chance survive");
        check(resolved.getProcessingDuration() == 77 && resolved.getRequiredHeat().name().equals("HEATED"), "Native timing and heat survive");
        var preview = (ProcessingRecipe<?, ?>) GraphPreviewRecipes.resolve(level, graph, node).value();
        check(preview.getIngredients().size() == 2 && preview.getIngredients().getFirst().test(new ItemStack(Items.GOLD_INGOT)),
            "Complete preview retains raw native primary candidates instead of graph entrance substitution");
        check(preview.getRollableResults().getFirst().getStack().is(Items.DIAMOND)
            && preview.getRollableResults().getFirst().getStack().getCount() == 2, "Complete preview retains raw native results/counts");
        check(node.recipe().equals(nativeJson), "Execution and preview never mutate the JSON snapshot");
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void fullDeployingPreservesHeldToolAndLegacyStepStillPrepends(GameTestHelper helper) {
        var level = helper.getLevel();
        var nativeRecipe = json("""
            {"type":"create:deploying","ingredients":[{"item":"minecraft:gold_ingot"},{"item":"minecraft:flint"}],
             "results":[{"id":"minecraft:diamond"}],"keep_held_item":true}
            """);
        var full = graph(nativeRecipe, true).definition("test:native_deploy");
        var deployed = (com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe)
            NativeSteps.create(level, full, full.nodes().get("s"), new ItemStack(Items.IRON_INGOT));
        check(deployed.getIngredients().size() == 2 && deployed.shouldKeepHeldItem(), "No third ingredient, held-tool flag survives");
        var stepData = nativeRecipe.deepCopy();
        stepData.getAsJsonArray("ingredients").remove(0);
        stepData.getAsJsonArray("results").remove(0);
        var legacy = graph(stepData, false).definition("test:legacy_deploy");
        var shorthand = NativeSteps.create(level, legacy, legacy.nodes().get("s"), new ItemStack(Items.IRON_INGOT));
        check(shorthand.getIngredients().size() == 2 && shorthand.getRollableResults().size() == 1, "Legacy step still prepends primary");
        check(shorthand.getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT))
            && shorthand.getIngredients().get(1).test(new ItemStack(Items.FLINT)), "Legacy auxiliary order remains unchanged");
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void vanillaNativePreviewAndGraphCodecRoundTripPreserveOriginalCandidates(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = graph(json("""
            {"type":"minecraft:smelting","ingredient":{"item":"minecraft:raw_copper"},
             "result":{"id":"minecraft:copper_ingot","count":2},"cookingtime":37,"experience":0.5}
            """), true);
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var roundTripped = (GraphRecipe) Recipe.CODEC.parse(ops, Recipe.CODEC.encodeStart(ops, original).getOrThrow()).getOrThrow();
        var definition = roundTripped.definition("test:native_smelting");
        check(original.definition(definition.id()).equals(definition), "Graph recipe codec preserves native mode without a format fork");
        var node = definition.nodes().get("s");
        check(node.nativeRecipe() && node.completeRecipe(), "Mode survives the real graph serializer");
        var preview = GraphPreviewRecipes.resolve(level, definition, node).value();
        check(preview.getIngredients().getFirst().test(new ItemStack(Items.RAW_COPPER))
            && !preview.getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT)), "Native preview is not rewritten to graph entrance");
        check(preview.getResultItem(level.registryAccess()).getCount() == 2, "Full result count remains visible");
        var executable = NativeSteps.template(level, definition, node, new ItemStack(Items.IRON_INGOT));
        check(executable.getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT)), "Execution still substitutes graph primary");
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void fullUnknownAdapterTemplateCanDisplayButCannotEnterExecutionIndexes(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = graph(json("""
            {"type":"graph_assembly:discovery_fixture","ingredients":[{"item":"minecraft:gold_ingot"}],
             "results":[{"id":"minecraft:diamond"}]}
            """), true);
        var definition = recipe.definition("test:native_unverified");
        var preview = GraphPreviewRecipes.resolve(level, definition, definition.nodes().get("s")).value();
        check(preview.getIngredients().size() == 1 && preview.getIngredients().getFirst().test(new ItemStack(Items.GOLD_INGOT)),
            "Unsupported serializer retains its complete raw display");
        check(!NativeSteps.supportsExecution(GraphAssembly.id("discovery_fixture"), preview), "Preview must not grant serializer admission");
        var manager = level.getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(definition.id()), recipe)));
            GraphIndex.invalidate();
            var index = GraphIndex.get(level);
            check(index.graphs().isEmpty() && index.entries(preview.getType()).isEmpty(), "No graph or same-type routing entry for unverified serializer");
            check(index.disabledReason(definition.id()).contains("no verified device adapter"), "Admission failure stays diagnostic");
            check(GraphPreviewRecipes.graphs(level).size() == 1, "Display-only graph remains available");
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void nativeTemplatesDoNotRelaxPrimaryOrFluidOutputRestrictions(GameTestHelper helper) {
        for (String[] scenario : new String[][] {
            {"[{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}]",
             "[{\"id\":\"minecraft:diamond\"}]", "primary item input and output"},
            {"[{\"item\":\"minecraft:gold_ingot\"}]",
             "[{\"id\":\"minecraft:water\",\"amount\":250}]", "primary item input and output"},
            {"[{\"item\":\"minecraft:gold_ingot\"}]",
             "[{\"id\":\"minecraft:diamond\"},{\"id\":\"minecraft:water\",\"amount\":250}]", "cannot carry fluid outputs"}
        }) {
            var data = json("{\"type\":\"create:mixing\",\"ingredients\":" + scenario[0] + ",\"results\":" + scenario[1] + "}");
            var definition = graph(data, true).definition("test:native_restrictions");
            var node = definition.nodes().get("s");
            check(GraphPreviewRecipes.resolve(helper.getLevel(), definition, node).value() instanceof ProcessingRecipe<?, ?>,
                "Complete fluid recipe remains displayable even when no execution adapter can carry it");
            try {
                NativeSteps.create(helper.getLevel(), definition, node, new ItemStack(Items.IRON_INGOT));
                throw new AssertionError("Full templates must preserve existing device restrictions");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains(scenario[2]), "Specific admission diagnostic: " + expected.getMessage());
            }
        }
        helper.succeed();
    }

    @GameTest(template="test_empty", batch="graph_assembly_native_templates", timeoutTicks=100)
    public static void nativeRepresentationDoesNotChangeOperationDescriptorPreviews(GameTestHelper helper) {
        var anvil = graph(json("{\"type\":\"graph_assembly:anvil\",\"action\":\"repair\",\"addition\":{\"item\":\"minecraft:iron_ingot\"}}"), true)
            .definition("test:native_anvil_descriptor");
        var preview = (dev.graphassembly.integration.anvil.AnvilRecipe) GraphPreviewRecipes.resolve(helper.getLevel(), anvil, anvil.nodes().get("s")).value();
        check(preview.previewAddition().test(new ItemStack(Items.IRON_INGOT)), "Node mode does not skip descriptor-owned preview enrichment");
        var brewing = graph(json("{\"type\":\"graph_assembly:brewing\",\"addition\":{\"item\":\"minecraft:nether_wart\"}}"), true)
            .definition("test:native_brewing_descriptor");
        var brewPreview = (dev.graphassembly.integration.brewing.BrewingRecipe) GraphPreviewRecipes.resolve(helper.getLevel(), brewing, brewing.nodes().get("s")).value();
        check(brewPreview.previewAddition().stream().anyMatch(stack -> stack.is(Items.NETHER_WART)), "Brewing descriptor addition remains visible");
        helper.succeed();
    }

    private static GraphRecipe graph(JsonObject recipe, boolean nativeMode) {
        var json = json("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true}],"edges":[]}
            """);
        var node = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.add("recipe", recipe.deepCopy());
        if (nativeMode) node.addProperty("recipe_mode", "native");
        return new GraphRecipe(json);
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
