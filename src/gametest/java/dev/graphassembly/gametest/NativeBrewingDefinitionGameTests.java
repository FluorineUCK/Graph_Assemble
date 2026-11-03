package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.Progress;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.crafting.Recipe;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.item.crafting.Ingredient;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.brewing.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@EventBusSubscriber(modid = GraphAssembly.ID)
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeBrewingDefinitionGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("test:brewing_definition");

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void brewingDescriptorEnablesGraph(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var graph = graph("nether_wart", """
                {"type":"graph_assembly:brewing","primary":"ingredient","addition":{"item":"minecraft:potion"}}
                """);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, graph)));
            var index = GraphIndex.get(helper.getLevel());
            check(index.graphs().containsKey(ID.toString()), index.disabledReason(ID.toString()));
        } finally {
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void nativeResultAndLogicalRoles(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            for (var primary : BrewingRecipe.Primary.values()) {
                boolean bottle = primary == BrewingRecipe.Primary.BOTTLE;
                var graph = graph(bottle ? "potion" : "nether_wart", """
                    {"type":"graph_assembly:brewing"%s,"addition":{"item":"minecraft:%s"}}
                    """.formatted(bottle ? "" : ",\"primary\":\"ingredient\"", bottle ? "nether_wart" : "potion"));
                manager.replaceRecipes(List.of(new RecipeHolder<>(ID, graph)));
                var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                var wart = new ItemStack(Items.NETHER_WART, 4);
                var slots = List.of(water, ItemStack.EMPTY, ItemStack.EMPTY, wart, ItemStack.EMPTY);
                var before = slots.stream().map(ItemStack::copy).toList();
                var eventCalls = new AtomicInteger();
                Consumer<PotionBrewEvent.Pre> pre = ignored -> eventCalls.incrementAndGet();
                Consumer<PotionBrewEvent.Post> post = ignored -> eventCalls.incrementAndGet();
                NeoForge.EVENT_BUS.addListener(pre);
                NeoForge.EVENT_BUS.addListener(post);
                BrewingLookup.Lookup found;
                try {
                    found = BrewingLookup.find(level, slots);
                } finally {
                    NeoForge.EVENT_BUS.unregister(pre);
                    NeoForge.EVENT_BUS.unregister(post);
                }
                check(eventCalls.get() == 0, "Lookup never posts native brewing events");
                check(found.handled() && found.primary() == primary && found.bottles().size() == 1, "Expected one graph bottle in " + primary);
                var result = found.bottles().getFirst();
                check(result.slot() == 0 && result.operation() != null, "Graph operation at physical slot zero");
                check(result.operation().primary().is(bottle ? Items.POTION : Items.NETHER_WART), "Logical primary");
                check(result.operation().auxiliary().getFirst().is(bottle ? Items.NETHER_WART : Items.POTION), "Logical auxiliary");
                check(result.nativeResult().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD),
                    "Native awkward potion, not the graph-level iron carrier");
                result.nativeResult().setCount(0);
                check(result.nativeResult().getCount() == 1 && ItemStack.listMatches(before, slots), "Defensive copies");
            }
        } finally {
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void descriptorValidation(GameTestHelper helper) {
        var level = helper.getLevel();
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var valid = JsonParser.parseString("{\"type\":\"graph_assembly:brewing\",\"addition\":{\"item\":\"minecraft:nether_wart\"}}");
        var descriptor = (BrewingRecipe) Recipe.CODEC.parse(ops, valid).getOrThrow();
        check(descriptor.primary() == BrewingRecipe.Primary.BOTTLE, "Missing primary defaults to bottle");
        check(!descriptor.matches(null, level) && descriptor.getResultItem(level.registryAccess()).isEmpty(), "No standalone recipe");
        for (var invalid : List.of("\"primary\":false", "\"primary\":\"fuel\"", "\"recipe_id\":\"test:bad\"",
            "\"output\":{}", "\"cost\":1", "\"time\":1", "\"addition\":false")) {
            var json = JsonParser.parseString("{\"type\":\"graph_assembly:brewing\"," + invalid + "}");
            check(Recipe.CODEC.parse(ops, json).error().isPresent(), "Should reject " + invalid);
        }
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var graph = graph("potion", "{\"type\":\"graph_assembly:brewing\"}");
            var data = graph.json();
            data.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("transitional_item", "minecraft:iron_nugget");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, new GraphRecipe(data))));
            var reason = GraphIndex.get(level).disabledReason(ID.toString());
            check(reason != null && reason.contains("node transitional_item"), "Node carrier must be rejected");
        } finally {
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void roundSelectionAndProtection(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var bottleGraph = new RecipeHolder<>(ID, graph("potion", "{\"type\":\"graph_assembly:brewing\"}"));
            var materialGraph = new RecipeHolder<>(ResourceLocation.parse("test:material_primary"), graph("nether_wart", """
                {"type":"graph_assembly:brewing","primary":"ingredient","addition":{"item":"minecraft:potion"}}
                """));
            var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            var splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER);
            var wart = new ItemStack(Items.NETHER_WART, 4);
            var empty = ItemStack.EMPTY;
            manager.replaceRecipes(List.of());
            check(!BrewingLookup.find(level, List.of(water, empty, empty, wart, empty)).handled(), "Ordinary native fallback");
            manager.replaceRecipes(List.of(materialGraph));
            for (int count = 1; count <= 3; count++) {
                var found = BrewingLookup.find(level, List.of(water, count >= 2 ? water : empty, count == 3 ? water : empty, wart, empty));
                check(found.bottles().size() == count && found.bottles().stream().allMatch(b -> b.operation().primary().is(Items.NETHER_WART)),
                    "Every eligible bottle receives its own operation");
            }
            var material = BrewingLookup.find(level, List.of(water, splash, empty, wart, empty));
            check(material.bottles().size() == 1 && material.bottles().getFirst().slot() == 0, "Unclaimed ordinary bottle is not brewed in material mode");
            manager.replaceRecipes(List.of(bottleGraph));
            var bottle = BrewingLookup.find(level, List.of(splash, water, empty, wart, empty));
            check(bottle.bottles().size() == 2 && bottle.bottles().getFirst().slot() == 0
                && bottle.bottles().getFirst().operation() == null && bottle.bottles().get(1).operation() != null, "Ordinary bottle participates only in bottle mode");
            var missing = new Progress("test:missing", "brew", "brew", Map.of(), List.of(), Map.of(), null, List.of("brew"));
            var badBottle = Workpiece.write(water.copy(), missing);
            var badMaterial = Workpiece.write(wart.copyWithCount(1), missing);
            var badFuel = Workpiece.write(new ItemStack(Items.BLAZE_POWDER), missing);
            reject(level, List.of(water, badBottle, empty, wart, empty));
            reject(level, List.of(water, empty, empty, badMaterial, empty));
            reject(level, List.of(water, empty, empty, wart, badFuel));
            reject(level, List.of(water.copyWithCount(2), empty, empty, wart, empty));
            manager.replaceRecipes(List.of(bottleGraph, materialGraph));
            reject(level, List.of(water, empty, empty, wart, empty));
        } finally {
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    @SubscribeEvent
    public static void registerEqualOutputRecipe(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(Ingredient.of(Items.POTION), Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
            PotionContents.createItemStack(Items.POTION, Potions.WATER));
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void unchangedNativeFallbackIsNotAnExplicitConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var definition = new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:potion"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"seed","start":true,"recipe":{"type":"graph_assembly:anvil"}},
                          {"id":"brew","finish":true,"recipe":{"type":"graph_assembly:brewing"}}],
                 "edges":[{"from":"seed","to":"brew"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, definition)));
            var malformed = Workpiece.write(new ItemStack(Items.POTION), progress(ID.toString(), "seed"));
            var gunpowder = new ItemStack(Items.GUNPOWDER);
            var empty = ItemStack.EMPTY;
            check(level.potionBrewing().hasMix(malformed.copy(), gunpowder.copy()), "Fixture hits vanilla hasMix true");
            reject(level, List.of(malformed, empty, empty, gunpowder, empty));

            var custom = graph("potion", """
                {"type":"graph_assembly:brewing","addition":{"type":"graph_assembly:smithing_ingredient_fixture"}}
                """);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, custom)));
            var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            var template = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            var queries = new AtomicInteger();
            SmithingIngredientFixture.query = queries::incrementAndGet;
            var found = BrewingLookup.find(level, List.of(water, empty, empty, template, empty));
            check(found.bottles().size() == 1 && ItemStack.matches(found.bottles().getFirst().nativeResult(), water),
                "Explicit registered equal-value output must be accepted");
            check(queries.get() > 0, "Custom addition retains its own runtime predicate");
        } finally {
            SmithingIngredientFixture.query = () -> {};
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void previewAdditionDoesNotShareMutableStacks(GameTestHelper helper) {
        var level = helper.getLevel();
        var json = JsonParser.parseString("""
            {"type":"graph_assembly:brewing","addition":{"item":"minecraft:nether_wart"}}
            """);
        var recipe = (BrewingRecipe) Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        var first = recipe.previewAddition();
        first.getFirst().setCount(0);
        check(recipe.previewAddition().getFirst().is(Items.NETHER_WART) && recipe.previewAddition().getFirst().getCount() == 1, "Mutating a preview must not contaminate later previews");
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void graphViewerResolvesBothBrewingRolesWithoutSyntheticResult(GameTestHelper helper) {
        var level = helper.getLevel();
        var graph = dev.graphassembly.core.GraphCodec.parse("test:brewing_display", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:nether_wart"},"transitional_item":"minecraft:nether_wart",
             "nodes":[{"id":"first","start":true,"recipe":{"type":"graph_assembly:brewing","primary":"ingredient","addition":{"item":"minecraft:potion"}}},
               {"id":"second","recipe":{"type":"graph_assembly:brewing","addition":{"item":"minecraft:redstone"}}}],
             "edges":[{"from":"first","to":"second"}],"outputs":[]}
            """).getAsJsonObject());
        var first = (BrewingRecipe) dev.graphassembly.integration.GraphPreviewRecipes.resolve(level, graph, graph.nodes().get("first")).value();
        var second = (BrewingRecipe) dev.graphassembly.integration.GraphPreviewRecipes.resolve(level, graph, graph.nodes().get("second")).value();
        check(first.primary() == BrewingRecipe.Primary.INGREDIENT && first.previewAddition().getFirst().is(Items.POTION), "Ingredient primary previews only the declared bottle auxiliary");
        check(second.primary() == BrewingRecipe.Primary.BOTTLE && second.previewAddition().getFirst().is(Items.REDSTONE), "Bottle primary previews only the declared material auxiliary");
        check(first.getResultItem(level.registryAccess()).isEmpty() && second.getResultItem(level.registryAccess()).isEmpty(), "Neither mode invents a native potion result");
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void tooltipCandidatesRespectActualBrewingRoles(GameTestHelper helper) {
        var level = helper.getLevel();
        var graph = dev.graphassembly.core.GraphCodec.parse("test:brew_tooltip", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:sugar"},"transitional_item":"minecraft:nether_wart",
             "nodes":[{"id":"seed","start":true,"recipe":{"type":"graph_assembly:anvil"}},
              {"id":"bottle","recipe":{"type":"graph_assembly:brewing","addition":{"item":"minecraft:sugar"}},"workpiece_inputs":[{"item_input":1}]},
              {"id":"material","recipe":{"type":"graph_assembly:brewing","primary":"ingredient"},"workpiece_inputs":[{"item_input":1}]},
              {"id":"extension","recipe":{"type":"graph_assembly:brewing","addition":{"type":"graph_assembly:smithing_ingredient_fixture"}},"workpiece_inputs":[{"item_input":1}]}],
             "edges":[{"from":"seed","to":"bottle"},{"from":"seed","to":"material"},{"from":"seed","to":"extension"}],"outputs":[]}
            """).getAsJsonObject());
        var state = progress(graph.id(), "seed");
        var preflight = dev.graphassembly.core.GraphEngine.preflight(graph, state);
        java.util.function.Function<ItemStack, dev.graphassembly.client.view.WorkpieceTooltipRoles> show = stack ->
            dev.graphassembly.client.view.WorkpieceTooltipRoles.select(graph, state, preflight, level.potionBrewing(),
                Workpiece.write(stack, state), id -> dev.graphassembly.integration.GraphPreviewRecipes.resolve(level, graph, graph.nodes().get(id)).value());

        var sugar = show.apply(new ItemStack(Items.SUGAR));
        check(sugar.primary().equals(java.util.Set.of("material")) && sugar.auxiliary().keySet().equals(java.util.Set.of("bottle")),
            "Sugar is only the material role in either mode");
        check(sugar.allowed().equals(java.util.Set.of("bottle", "material")), "Non-matching addition must not leave a next step");

        var potion = show.apply(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        check(potion.primary().equals(java.util.Set.of("bottle", "extension")) && potion.auxiliary().keySet().equals(java.util.Set.of("material")),
            "Potion is only the bottle role, including an unrestricted auxiliary");

        var queries = new AtomicInteger();
        SmithingIngredientFixture.query = queries::incrementAndGet;
        try {
            var template = show.apply(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            check(template.primary().equals(java.util.Set.of("material")) && template.auxiliary().keySet().equals(java.util.Set.of("extension")),
                "Native extension ingredient and custom auxiliary predicate are recognized");
            check(queries.get() > 0, "Tooltip must call the custom Ingredient predicate, not display candidates");
        } finally {
            SmithingIngredientFixture.query = () -> {};
        }

        check(show.apply(new ItemStack(Items.DIAMOND)).allowed().isEmpty(), "No compatible role means no next step");
        var descriptor = (BrewingRecipe) dev.graphassembly.integration.GraphPreviewRecipes.resolve(level, graph, graph.nodes().get("bottle")).value();
        check(!descriptor.matchesInput(level.potionBrewing(), 0, ItemStack.EMPTY)
                && !descriptor.matchesInput(level.potionBrewing(), -1, new ItemStack(Items.SUGAR))
                && !descriptor.matchesInput(level.potionBrewing(), 2, new ItemStack(Items.SUGAR)),
            "Empty stack and invalid logical slots never match");
        helper.succeed();
    }

    private static void reject(net.minecraft.world.level.Level level, List<ItemStack> slots) {
        var found = BrewingLookup.find(level, slots);
        check(found.handled() && found.bottles().isEmpty(), "Invalid or ambiguous round cannot expose partial authorization");
    }

    @GameTest(template = "test_empty", batch = "native_brewing")
    public static void trackedAuxiliaryRequiresSameGraphAndPath(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var definition = new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:nether_wart"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[
                   {"id":"seed","start":true,"recipe":{"type":"graph_assembly:anvil"}},
                   {"id":"brew","finish":true,"recipe":{"type":"graph_assembly:brewing","primary":"ingredient"},
                    "workpiece_inputs":[{"item_input":0},{"item_input":1,"current_nodes":["seed"]}]}],
                 "edges":[{"from":"seed","to":"brew"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, definition)));
            var index = GraphIndex.get(level);
            check(index.graphs().containsKey(ID.toString()), index.disabledReason(ID.toString()));
            var progress = progress(ID.toString(), "seed");
            var material = Workpiece.write(new ItemStack(Items.NETHER_WART), progress);
            var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            var validBottle = Workpiece.write(water, progress);
            var empty = ItemStack.EMPTY;
            var found = BrewingLookup.find(level, List.of(validBottle, validBottle, validBottle, material, empty));
            check(found.bottles().size() == 3, "All tracked bottles may use the same tracked material independently");
            for (var bottle : found.bottles()) {
                check(Workpiece.read(level, bottle.operation().primary()).equals(progress), "Unchanged material state in each operation");
                check(Workpiece.read(level, bottle.operation().auxiliary().getFirst()).equals(progress), "Unchanged bottle state in auxiliary role");
            }
            reject(level, List.of(validBottle, Workpiece.write(water, progress("test:other", "seed")), empty, material, empty));
            reject(level, List.of(validBottle, Workpiece.write(water, progress(ID.toString(), "brew")), empty, material, empty));
            reject(level, List.of(validBottle, empty, empty, material.copyWithCount(2), empty));
            var noMix = Workpiece.write(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD), progress);
            reject(level, List.of(validBottle, noMix, empty, material, empty));
            var withoutRole = definition.json();
            withoutRole.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("workpiece_inputs").remove(1);
            manager.replaceRecipes(List.of(new RecipeHolder<>(ID, new GraphRecipe(withoutRole))));
            reject(level, List.of(validBottle, empty, empty, material, empty));
        } finally {
            manager.replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        helper.succeed();
    }

    private static Progress progress(String graph, String current) {
        return new Progress(graph, "seed", current, Map.of(), List.of(), Map.of(), null, List.of(current));
    }


    private static GraphRecipe graph(String item, String descriptor) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:%s"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"brew","start":true,"finish":true,"recipe":%s}],
             "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """.formatted(item, descriptor)).getAsJsonObject());
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new GameTestAssertException(message);
    }
}
