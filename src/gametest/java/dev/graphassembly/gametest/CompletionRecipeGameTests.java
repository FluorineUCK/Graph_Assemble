package dev.graphassembly.gametest;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.GraphSerializer;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.util.List;
import java.util.Map;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class CompletionRecipeGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void markAmountsSurviveRecipeJsonAndNetworkRoundTrips(GameTestHelper helper) {
        var serializer = new GraphSerializer();
        check(serializer.codec().keys(JsonOps.INSTANCE).map(key -> key.getAsString()).toList()
                .equals(List.of("ingredient", "transitional_item", "nodes", "edges", "outputs")),
            "Serializer must advertise the graph fields");
        var json = JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"finish":true,"mark_amounts":{"x":3},"recipe":{"type":"create:pressing"},
                   "outputs":[{"requirements":{"mode":"partial","marks":{"x":2}},"results":[{"item":"minecraft:diamond"}]}]}],"edges":[],
                 "outputs":[{"requirements":{"checkpoints":["s"]},"results":[{"item":"minecraft:gold_ingot"}]}]}
                """).getAsJsonObject();
        var original = new GraphRecipe(json);
        var encoded = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        check(encoded.getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("mark_amounts").get("x").getAsInt() == 3, "JSON encoder must preserve mark amounts");
        var decoded = serializer.codec().codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(json.equals(decoded.json()), "JSON must preserve the author representation and graph/node requirements");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            serializer.streamCodec().encode(buffer, decoded);
            var received = serializer.streamCodec().decode(buffer);
            check(json.equals(received.json()) && buffer.readableBytes() == 0, "Network must preserve the entire author representation");
            var graph = received.definition("test:roundtrip");
            check(original.definition("test:roundtrip").equals(graph), "Sync must preserve graph semantics");
            check(((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "minecraft:iron_ingot"), () -> .75)).outputs().getFirst().item().equals("minecraft:diamond"),
                "Graph must settle from the merged graph/local pool after sync");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unknownGraphFieldsAreRejectedByRecipeJsonAndNetwork(GameTestHelper helper) {
        var serializer = new GraphSerializer();
        for (String value : new String[] {"1", "2", "3", "null", "\"2\""}) {
            var json = JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[]}
                """).getAsJsonObject();
            json.add("unexpected_field", JsonParser.parseString(value));
            rejectsUnknownField(() -> new GraphRecipe(json));
            var decoded = serializer.codec().codec().parse(JsonOps.INSTANCE, json);
            check(decoded.error().isPresent(), "JSON decoder must reject unknown field value " + value);
            checkFieldDiagnostic(decoded.error().orElseThrow().message());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            try {
                buffer.writeUtf(json.toString());
                rejectsUnknownField(() -> serializer.streamCodec().decode(buffer));
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void optionalKubeScriptsGenerateUnifiedRecipes(GameTestHelper helper) {
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            for (String id : new String[] {"kube_fixture", "kube_requirements_fixture", "kube_invalid_cases_checked"}) {
                var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:" + id)).orElseThrow();
                check(holder.value() instanceof GraphRecipe, "Script must generate a real graph recipe: " + id);
            }
            var recipe = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_requirements_fixture")).orElseThrow().value();
            var jsonEquivalent = new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:brick"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","recipe":{"type":"create:pressing"},"start":true,"finish":true,"mark_amounts":{"x":3}}],
                 "edges":[],"outputs":[{"requirements":{"mode":"partial","marks":{"x":2}},
                   "results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var scripted = recipe.definition("test:kube_requirements_fixture");
            var fromJson = jsonEquivalent.definition("test:kube_requirements_fixture");
            check(scripted.equals(fromJson), "KubeJS and JSON inputs must define the same graph");
            check(GraphPipeline.resolve(GraphPipeline.plan(scripted, null, "s", "minecraft:brick"), () -> 0)
                    .equals(GraphPipeline.resolve(GraphPipeline.plan(fromJson, null, "s", "minecraft:brick"), () -> 0)),
                "KubeJS and JSON inputs must settle equally with a fixed RNG");
            check((GraphPipeline.resolve(GraphPipeline.plan(scripted, null, "s", "minecraft:brick"), () -> 0) instanceof TransitionResult.Settled),
                "Partial condition must accept excess marks in actual script graph");
            var plain = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_fixture")).orElseThrow().value();
            check((GraphPipeline.resolve(GraphPipeline.plan(plain.definition("test:kube_fixture"), null, "s", "minecraft:nether_brick"), () -> 0) instanceof TransitionResult.Settled),
                "Default script graph must settle");
            var checked = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_invalid_cases_checked")).orElseThrow().value();
            check((GraphPipeline.resolve(GraphPipeline.plan(checked.definition("test:kube_invalid_cases_checked"), null, "s", "minecraft:netherite_scrap"), () -> 0) instanceof TransitionResult.Settled),
                "Negative-case sentinel must be a usable graph");
            var basicExample = example(helper, "example:script_graph");
            check(basicExample.defaults().ingredient().equals(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
                    && basicExample.defaults().transitionalItem().equals("minecraft:iron_nugget")
                    && basicExample.nodes().keySet().equals(java.util.Set.of("start", "finish"))
                    && basicExample.nodes().get("start").roles().start()
                    && basicExample.nodes().get("start").operation().recipe().get("type").getAsString().equals("create:pressing")
                    && basicExample.nodes().get("finish").roles().finish()
                    && basicExample.nodes().get("finish").operation().recipe().get("type").getAsString().equals("create:cutting")
                    && basicExample.nodes().get("finish").operation().recipe().get("processing_time").getAsInt() == 40
                    && basicExample.topology().edges().size() == 1
                    && basicExample.topology().edges().getFirst().from().equals("start")
                    && basicExample.topology().edges().getFirst().to().equals("finish")
                    && basicExample.outputRules().size() == 1,
                "Staged graph_assembly.js must register its authored carrier, steps, path and output rule");
            var basicRule = basicExample.outputRules().getFirst();
            check(basicRule.requirements().marks().match().values().isEmpty()
                    && basicRule.requirements().checkpoints().match().nodes().equals(List.of("start", "finish"))
                    && !ProgressMatcher.matches(basicRule.requirements(), new ProgressFacts(Map.of("unexpected", 1), List.of("start", "finish"))),
                "Staged graph_assembly.js must keep exact-empty marks and both checkpoints");
            var basicStart = GraphPipeline.resolve(GraphPipeline.plan(basicExample, null, "start", "minecraft:iron_ingot"), () -> 0);
            check(!(basicStart instanceof TransitionResult.Settled), "Basic example must retain its workpiece until finish");
            check(((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(basicExample, ((TransitionResult.Continued) basicStart).progress(), "finish", ((TransitionResult.Continued) basicStart).carrierItem()), () -> 0)).outputs().getFirst().item().equals("minecraft:gold_ingot"),
                "Staged graph_assembly.js must settle gold");

            var itemExample = example(helper, "example:item_first");
            check(itemExample.defaults().ingredient().equals(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"))
                    && itemExample.defaults().transitionalItem().equals("minecraft:iron_nugget")
                    && itemExample.nodes().keySet().equals(java.util.Set.of("start", "finish"))
                    && itemExample.nodes().get("start").roles().start()
                    && itemExample.nodes().get("start").operation().recipe().get("type").getAsString().equals("create:pressing")
                    && itemExample.nodes().get("start").effects().markAmounts().equals(Map.of("precision", 2))
                    && itemExample.nodes().get("finish").roles().finish()
                    && itemExample.nodes().get("finish").operation().recipe().get("type").getAsString().equals("create:cutting")
                    && itemExample.nodes().get("finish").operation().recipe().get("processing_time").getAsInt() == 40
                    && itemExample.topology().edges().size() == 1
                    && itemExample.topology().edges().getFirst().from().equals("start")
                    && itemExample.topology().edges().getFirst().to().equals("finish")
                    && itemExample.outputRules().size() == 1,
                "Staged item_first.js must keep its authored carrier, steps, precision mark grant, path and output rule");
            var itemRule = itemExample.outputRules().getFirst();
            check(itemRule.requirements().marks().match().values().equals(Map.of("precision", 1))
                    && itemRule.requirements().checkpoints().match().mode() == CheckpointMode.SUBSEQUENCE
                    && itemRule.requirements().checkpoints().match().nodes().equals(List.of("start", "finish")),
                "Staged item_first.js must keep partial mark/checkpoint conditions");
            var itemStart = GraphPipeline.resolve(GraphPipeline.plan(itemExample, null, "start", "minecraft:copper_ingot"), () -> 0);
            check(!(itemStart instanceof TransitionResult.Settled), "Item-first example must retain its workpiece until finish");
            check(((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(itemExample, ((TransitionResult.Continued) itemStart).progress(), "finish", ((TransitionResult.Continued) itemStart).carrierItem()), () -> 0)).outputs().getFirst().item().equals("minecraft:diamond"),
                "Staged item_first.js must settle diamond");
        }
        helper.succeed();
    }

    private static GraphDefinition<OperationSpec> example(GameTestHelper helper, String id) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();
        check(holder.value() instanceof GraphRecipe, "Staged example script must register a graph recipe: " + id);
        var recipe = (GraphRecipe) holder.value();
        return recipe.definition(id);
    }

    private static void rejectsUnknownField(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Unknown field was accepted");
        } catch (IllegalArgumentException expected) {
            checkFieldDiagnostic(expected.getMessage());
        }
    }

    private static void checkFieldDiagnostic(String message) {
        check(message.contains("graph.unexpected_field") && message.contains("unknown field"), "Unknown field needs an actionable diagnostic: " + message);
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
