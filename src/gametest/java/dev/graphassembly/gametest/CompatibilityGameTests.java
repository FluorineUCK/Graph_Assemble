package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteResolver;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.AssemblyDiscovery;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.List;

/** Real-addon opt-in probes. Failures are compatibility findings, not expected successes. */
@GameTestHolder(GraphAssembly.ID)
public final class CompatibilityGameTests {
    @GameTestGenerator
    public static List<TestFunction> tests() {
        if (!Boolean.getBoolean("graph_assembly.compatibilityVerification")) return List.of();
        return List.of(test("addon_matrix", CompatibilityGameTests::matrix), test("fan_workpiece_safety", CompatibilityGameTests::fans),
            test("connected_catalysts", CompatibilityGameTests::catalysts));
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> run) {
        // Separate batches: these probes replace recipes temporarily and restore them in finally.
        return new TestFunction("compatibility_" + name, "graph_assembly.compatibility_" + name,
            "graph_assembly:test_empty", 200, 0, true, run);
    }

    private static void matrix(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var observed = AssemblyDiscovery.scan(originals);
        var serializers = List.of("create_fantasizing:exposing", "create_fantasizing:shadow_plating",
            "createaddition:rolling", "createaddition:charging", "create_dragons_plus:freezing", "create_dragons_plus:ending",
            "create:splashing", "create:haunting", "minecraft:smelting", "minecraft:blasting", "minecraft:smoking");
        try {
            for (String serializer : serializers) {
                var id = ResourceLocation.parse(serializer);
                var sample = originals.stream().filter(h -> id.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(h.value().getSerializer())))
                    .sorted(java.util.Comparator.comparing(h -> h.id().toString())).findFirst().orElseThrow();
                var capability = AssemblyDiscovery.inspect(id, sample.value());
                boolean discovered = observed.stream().anyMatch(o -> o.capability().serializer().equals(serializer));
                // Use the actual registered codec/data, not a synthetic addon Recipe class.
                var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var nativeJson = Recipe.CODEC.encodeStart(ops, sample.value()).getOrThrow().getAsJsonObject();
                // A graph step stores auxiliary ingredients only; NativeSteps prepends the workpiece.
                if (nativeJson.has("ingredients") && !nativeJson.getAsJsonArray("ingredients").isEmpty())
                    nativeJson.getAsJsonArray("ingredients").remove(0);
                // Likewise, the first output is supplied by the graph transition; results stores byproducts.
                if (nativeJson.has("results") && !nativeJson.getAsJsonArray("results").isEmpty())
                    nativeJson.getAsJsonArray("results").remove(0);
                var json = JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}}],"edges":[]}
                    """).getAsJsonObject();
                json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe", nativeJson);
                var graphId = GraphAssembly.id("compatibility/" + id.getNamespace() + "/" + id.getPath());
                manager.replaceRecipes(List.of(new RecipeHolder<>(graphId, new GraphRecipe(json))));
                GraphIndex.invalidate();
                var index = GraphIndex.get(helper.getLevel());
                String reason = index.disabledReason(graphId.toString());
                check(!index.graphs().containsKey(graphId.toString()) && reason != null, "Unsupported graph unexpectedly enabled: " + serializer);
                check(reason.contains("no verified device adapter"), "Fixture failed before adapter diagnosis: " + reason);
                GraphAssembly.LOGGER.info("COMPAT_MATRIX serializer={} sample={} discovered={} capability={} graphDisabled={}",
                    serializer, sample.id(), discovered, capability, reason);
            }
            check(originals.stream().anyMatch(h -> h.id().getNamespace().equals("create_connected")), "Connected recipes not loaded");
            GraphAssembly.LOGGER.info("COMPAT_MATRIX fanRegistry={}", CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet());
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); }
        helper.succeed();
    }

    private record FanCase(String name, String type, String input, String output) {}
    private static void catalysts(GameTestHelper helper) {
        var candidates = java.util.Map.of(
            "fan_blasting_catalyst", "create:blasting", "fan_smoking_catalyst", "create:smoking",
            "fan_splashing_catalyst", "create:splashing", "fan_haunting_catalyst", "create:haunting",
            "fan_ending_catalyst_dragons_breath", "create_dragons_plus:ending",
            "fan_ending_catalyst_dragon_head", "create_dragons_plus:ending");
        var pos = new BlockPos(1, 1, 1);
        for (var candidate : candidates.entrySet()) {
            var id = ResourceLocation.fromNamespaceAndPath("create_connected", candidate.getKey());
            check(BuiltInRegistries.BLOCK.containsKey(id), "Missing Connected catalyst: " + id);
            var type = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(ResourceLocation.parse(candidate.getValue()));
            check(type != null, "Missing catalyst processing type");
            helper.setBlock(pos, BuiltInRegistries.BLOCK.get(id));
            check(type.isValidAt(helper.getLevel(), helper.absolutePos(pos)), "Connected catalyst not accepted: " + id);
            GraphAssembly.LOGGER.info("COMPAT_CATALYST PASS block={} fan={}", id, candidate.getValue());
        }
        helper.succeed();
    }

    private static void fans(GameTestHelper helper) {
        var cases = List.of(
            new FanCase("smelting", "create:blasting", "minecraft:cobblestone", "minecraft:stone"),
            new FanCase("blasting_only_fixture", "create:blasting", "minecraft:diamond_pickaxe", "minecraft:diamond"),
            new FanCase("smoking", "create:smoking", "minecraft:beef", "minecraft:cooked_beef"),
            new FanCase("haunting", "create:haunting", "minecraft:sand", "minecraft:soul_sand"),
            new FanCase("splashing", "create:splashing", "minecraft:red_wool", "minecraft:white_wool"),
            new FanCase("freezing", "create_dragons_plus:freezing", "minecraft:packed_ice", "minecraft:blue_ice"),
            new FanCase("ending", "create_dragons_plus:ending", "minecraft:apple", "minecraft:chorus_fruit"));
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var fixtures = new ArrayList<>(originals);
        var failures = new ArrayList<String>();
        try {
            // Forces the BLASTING fallback rather than accidentally re-testing SMELTING.
            var blast = Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                {"type":"minecraft:blasting","ingredient":{"item":"minecraft:diamond_pickaxe"},
                 "result":{"id":"minecraft:diamond"},"experience":0,"cookingtime":100}
                """)).getOrThrow();
            fixtures.add(new RecipeHolder<>(GraphAssembly.id("compatibility_blast_only"), blast));
            for (var c : cases) {
                var json = JsonParser.parseString("""
                    {"ingredient":{"item":"%s"},"transitional_item":"%s",
                     "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                              {"id":"next","recipe":{"type":"create:pressing"}}],
                     "edges":[{"from":"s","to":"next"}]}
                    """.formatted(c.input, c.input)).getAsJsonObject();
                var graphId = GraphAssembly.id("compatibility_fan/" + c.name);
                fixtures.add(new RecipeHolder<>(graphId, new GraphRecipe(json)));
            }
            manager.replaceRecipes(fixtures);
            GraphIndex.invalidate();
            for (var c : cases) {
                var type = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(ResourceLocation.parse(c.type));
                check(type != null, "Missing actual fan type " + c.type);
                var graph = GraphIndex.get(level).graphs().get("graph_assembly:compatibility_fan/" + c.name);
                check(graph != null, "Workpiece fixture graph must be valid");
                var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", c.input), () -> 0)).progress();
                var input = NativeSteps.stack(c.input);
                var workpiece = Workpiece.write(input, state);
                check(RouteResolver.incompatibility(graph, Workpiece.read(workpiece)) == null, "Invalid progress fixture");
                var expected = BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.output));
                for (boolean marked : List.of(false, true)) {
                    var stack = marked ? workpiece : input;
                    if (!marked) check(type.canProcess(stack, level), "Native fan cannot process " + c.name);
                    var transported = new TransportedItemStack(stack.copy());
                    var result = FanProcessing.applyProcessing(transported, level, type);
                    for (int i = 0; result.doesNothing() && i < 2000; i++) result = FanProcessing.applyProcessing(transported, level, type);
                    if (!marked) check(!result.doesNothing(), "Belt processing timed out: " + c.name);
                    var output = result.doesNothing() ? List.of(transported) : result.getOutputs();
                    if (!marked) check(output.size() == 1 && output.getFirst().stack.is(expected), "Unexpected belt output: " + c.name + " " + output);
                    boolean retained = output.size() == 1 && ItemStack.matches(output.getFirst().stack, workpiece);
                    GraphAssembly.LOGGER.info("COMPAT_FAN case={} route=belt marked={} result={} progressRetained={}", c.name, marked,
                        output.stream().map(o -> o.stack.toString()).toList(), retained);
                    if (marked && !retained) failures.add(c.name + "/belt");
                    var entity = new ItemEntity(level, 0, 100, 0, stack.copy());
                    boolean canProcess = FanProcessing.canProcess(entity, type);
                    if (!marked) check(canProcess, "Entity path cannot process " + c.name);
                    boolean applied = false;
                    for (int i = 0; canProcess && !applied && i < 2000; i++) applied = FanProcessing.applyProcessing(entity, type);
                    if (!marked) check(applied && entity.getItem().is(expected), "Unexpected entity output: " + c.name);
                    retained = !entity.isRemoved() && ItemStack.matches(entity.getItem(), workpiece);
                    GraphAssembly.LOGGER.info("COMPAT_FAN case={} route=entity marked={} result={} progressRetained={}", c.name, marked, entity.getItem(), retained);
                    if (marked && !retained) failures.add(c.name + "/entity");
                    entity.discard();
                }
            }
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); }
        check(failures.isEmpty(), "Live graph progress lost in unsupported fan processing: " + failures);
        helper.succeed();
    }

    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
