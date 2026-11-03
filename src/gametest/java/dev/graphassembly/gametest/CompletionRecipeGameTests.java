package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.core.CheckpointMode;
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
    public static void currentFormatSurvivesRecipeJsonAndNetworkRoundTrips(GameTestHelper helper) {
        var serializer = new GraphSerializer();
        check(serializer.codec().keys(JsonOps.INSTANCE).noneMatch(key -> key.getAsString().equals("format_version")),
            "Serializer must not advertise a removed version field");
        var json = JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"finish":true,"rewards":{"x":3},"recipe":{"type":"create:pressing"},
                   "outputs":[{"requirements":{"mode":"partial","marks":{"x":2}},"results":[{"item":"minecraft:diamond"}]}]}],"edges":[],
                 "outputs":[{"requirements":{"checkpoints":["s"]},"results":[{"item":"minecraft:gold_ingot"}]}]}
                """).getAsJsonObject();
        var original = new GraphRecipe(json);
        var encoded = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        check(!encoded.getAsJsonObject().has("format_version"), "JSON encoder must not reintroduce a version");
        var decoded = serializer.codec().codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(json.equals(decoded.json()), "JSON must preserve the author representation and graph/node requirements");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            serializer.streamCodec().encode(buffer, decoded);
            var received = serializer.streamCodec().decode(buffer);
            check(json.equals(received.json()) && buffer.readableBytes() == 0, "Network must preserve the entire author representation");
            var graph = received.definition("test:roundtrip");
            check(original.definition("test:roundtrip").equals(graph), "Sync must preserve graph semantics");
            check(GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> .75).output().item().equals("minecraft:diamond"),
                "Current format must settle from the merged graph/local pool after sync");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void removedVersionIsRejectedByRecipeJsonAndNetwork(GameTestHelper helper) {
        var serializer = new GraphSerializer();
        for (String value : new String[] {"1", "2", "3", "null", "\"2\""}) {
            var json = JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[]}
                """).getAsJsonObject();
            json.add("format_version", JsonParser.parseString(value));
            rejectsRemovedVersion(() -> new GraphRecipe(json));
            var decoded = serializer.codec().codec().parse(JsonOps.INSTANCE, json);
            check(decoded.error().isPresent(), "JSON decoder must reject removed version " + value);
            checkVersionDiagnostic(decoded.error().orElseThrow().message());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            try {
                buffer.writeUtf(json.toString());
                rejectsRemovedVersion(() -> serializer.streamCodec().decode(buffer));
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
                check(!((GraphRecipe) holder.value()).json().has("format_version"),
                    "Script graph must not declare a version: " + id);
            }
            var recipe = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_requirements_fixture")).orElseThrow().value();
            var jsonEquivalent = new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:brick"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","recipe":{"type":"create:pressing"},"start":true,"finish":true,"rewards":{"x":3}}],
                 "edges":[],"outputs":[{"requirements":{"mode":"partial","marks":{"x":2}},
                   "results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var scripted = recipe.definition("test:kube_requirements_fixture");
            var fromJson = jsonEquivalent.definition("test:kube_requirements_fixture");
            check(scripted.equals(fromJson), "KubeJS and JSON inputs must define the same graph");
            check(GraphEngine.complete(scripted, null, "s", "minecraft:brick", () -> 0)
                    .equals(GraphEngine.complete(fromJson, null, "s", "minecraft:brick", () -> 0)),
                "KubeJS and JSON inputs must settle equally with a fixed RNG");
            check(GraphEngine.complete(scripted, null, "s", "minecraft:brick", () -> 0).settled(),
                "Partial condition must accept excess marks in actual script graph");
            var plain = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_fixture")).orElseThrow().value();
            check(GraphEngine.complete(plain.definition("test:kube_fixture"), null, "s", "minecraft:nether_brick", () -> 0).settled(),
                "Default script graph must settle");
            var checked = (GraphRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("test:kube_invalid_cases_checked")).orElseThrow().value();
            check(GraphEngine.complete(checked.definition("test:kube_invalid_cases_checked"), null, "s", "minecraft:netherite_scrap", () -> 0).settled(),
                "Negative-case sentinel must be a usable current-format graph");
            var basicExample = example(helper, "example:script_graph");
            check(basicExample.ingredient().equals(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
                    && basicExample.transitionalItem().equals("minecraft:iron_nugget")
                    && basicExample.nodes().keySet().equals(java.util.Set.of("start", "finish"))
                    && basicExample.nodes().get("start").start()
                    && basicExample.nodes().get("start").recipe().get("type").getAsString().equals("create:pressing")
                    && basicExample.nodes().get("finish").finish()
                    && basicExample.nodes().get("finish").recipe().get("type").getAsString().equals("create:cutting")
                    && basicExample.nodes().get("finish").recipe().get("processing_time").getAsInt() == 40
                    && basicExample.edges().size() == 1
                    && basicExample.edges().getFirst().from().equals("start")
                    && basicExample.edges().getFirst().to().equals("finish")
                    && basicExample.outputs().size() == 1,
                "Staged graph_assembly.js must register its authored carrier, steps, path and output rule");
            var basicRule = basicExample.outputs().getFirst();
            check(basicRule.requirements().marks().values().isEmpty()
                    && basicRule.requirements().checkpoints().nodes().equals(List.of("start", "finish"))
                    && !basicRule.requirements().matches(Map.of("unexpected", 1), List.of("start", "finish")),
                "Staged graph_assembly.js must keep exact-empty marks and both checkpoints");
            var basicStart = GraphEngine.complete(basicExample, null, "start", "minecraft:iron_ingot", () -> 0);
            check(!basicStart.settled(), "Basic example must retain its workpiece until finish");
            check(GraphEngine.complete(basicExample, basicStart.state(), "finish", basicStart.item(), () -> 0)
                    .output().item().equals("minecraft:gold_ingot"),
                "Staged graph_assembly.js must settle gold");

            var itemExample = example(helper, "example:item_first");
            check(itemExample.ingredient().equals(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"))
                    && itemExample.transitionalItem().equals("minecraft:iron_nugget")
                    && itemExample.nodes().keySet().equals(java.util.Set.of("start", "finish"))
                    && itemExample.nodes().get("start").start()
                    && itemExample.nodes().get("start").recipe().get("type").getAsString().equals("create:pressing")
                    && itemExample.nodes().get("start").rewards().equals(Map.of("precision", 2))
                    && itemExample.nodes().get("finish").finish()
                    && itemExample.nodes().get("finish").recipe().get("type").getAsString().equals("create:cutting")
                    && itemExample.nodes().get("finish").recipe().get("processing_time").getAsInt() == 40
                    && itemExample.edges().size() == 1
                    && itemExample.edges().getFirst().from().equals("start")
                    && itemExample.edges().getFirst().to().equals("finish")
                    && itemExample.outputs().size() == 1,
                "Staged item_first.js must keep its authored carrier, steps, precision reward, path and output rule");
            var itemRule = itemExample.outputs().getFirst();
            check(itemRule.requirements().marks().values().equals(Map.of("precision", 1))
                    && itemRule.requirements().checkpoints().mode() == CheckpointMode.SUBSEQUENCE
                    && itemRule.requirements().checkpoints().nodes().equals(List.of("start", "finish")),
                "Staged item_first.js must keep partial mark/checkpoint conditions");
            var itemStart = GraphEngine.complete(itemExample, null, "start", "minecraft:copper_ingot", () -> 0);
            check(!itemStart.settled(), "Item-first example must retain its workpiece until finish");
            check(GraphEngine.complete(itemExample, itemStart.state(), "finish", itemStart.item(), () -> 0)
                    .output().item().equals("minecraft:diamond"),
                "Staged item_first.js must settle diamond");
        }
        helper.succeed();
    }

    private static GraphDefinition example(GameTestHelper helper, String id) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();
        check(holder.value() instanceof GraphRecipe, "Staged example script must register a graph recipe: " + id);
        var recipe = (GraphRecipe) holder.value();
        check(!recipe.json().has("format_version"), "Staged example must not declare a version: " + id);
        return recipe.definition(id);
    }

    private static void rejectsRemovedVersion(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Removed version field was accepted");
        } catch (IllegalArgumentException expected) {
            checkVersionDiagnostic(expected.getMessage());
        }
    }

    private static void checkVersionDiagnostic(String message) {
        check(message.contains("graph.format_version") && message.contains("Remove format_version")
            && message.contains("requirements"), "Removed field needs an actionable diagnostic: " + message);
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
