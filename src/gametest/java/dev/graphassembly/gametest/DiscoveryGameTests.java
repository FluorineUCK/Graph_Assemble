package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.AssemblyDiscovery;
import dev.graphassembly.integration.AssemblyDiagnostics;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class DiscoveryGameTests {
    @GameTest(template = "test_empty", batch = "graph_assembly_discovery_scan", timeoutTicks = 100)
    public static void discoversStandaloneAndNestedNonCreateSerializersWithoutEnablingThem(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var standalone = Recipe.CODEC.parse(ops, JsonParser.parseString("""
            {"type":"graph_assembly:discovery_fixture","ingredients":[{"item":"minecraft:iron_ingot"}],
             "results":[{"id":"minecraft:iron_nugget"}]}
            """)).getOrThrow();
        var sequence = Recipe.CODEC.parse(ops, JsonParser.parseString("""
            {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:iron_ingot"},
             "transitional_item":{"id":"minecraft:iron_nugget"},"loops":1,
             "sequence":[{"type":"graph_assembly:discovery_fixture","ingredients":[{"item":"minecraft:iron_nugget"}],
                          "results":[{"id":"minecraft:iron_nugget"}]}],
             "results":[{"id":"minecraft:gold_ingot"}]}
            """)).getOrThrow();
        var found = AssemblyDiscovery.scan(List.of(new RecipeHolder<>(GraphAssembly.id("standalone"), standalone)));
        check(found.size() == 1, "Non-Create IAssemblyRecipe serializer must be discovered: " + found);
        var capability = found.getFirst().capability();
        check(capability.registered() && capability.assemblyRecipe() && capability.supportsAssembly() && !capability.deviceAdapter(),
            "Discovery must not grant an execution adapter: " + capability);
        check(capability.serializer().equals("graph_assembly:discovery_fixture") && capability.recipeType().equals("create:pressing"),
            "Serializer and RecipeType must remain distinct: " + capability);
        var nested = AssemblyDiscovery.scan(List.of(new RecipeHolder<>(GraphAssembly.id("sequence"), sequence)));
        check(nested.size() == 1 && nested.getFirst().capability().equals(capability)
            && nested.getFirst().source().contains("sequence[0]"), "Nested assembly step must be discovered: " + nested);
        check(AssemblyDiscovery.scan(List.of(new RecipeHolder<>(GraphAssembly.id("a"), standalone),
            new RecipeHolder<>(GraphAssembly.id("b"), standalone))).size() == 1, "Equivalent capability observations are deduplicated");

        var params = ProcessingRecipeParams.CODEC.codec().parse(ops, JsonParser.parseString("""
            {"ingredients":[{"item":"minecraft:iron_ingot"}],"results":[{"id":"minecraft:iron_nugget"}]}
            """)).getOrThrow();
        var declined = new DiscoveryFixture(params) { @Override public boolean supportsAssembly() { return false; } };
        var broken = new DiscoveryFixture(params) { @Override public boolean supportsAssembly() { throw new IllegalStateException("fixture failure"); } };
        check(!AssemblyDiscovery.inspect(GraphAssembly.id("discovery_fixture"), declined).supportsAssembly(), "False capability must not be ignored");
        check(AssemblyDiscovery.inspect(GraphAssembly.id("discovery_fixture"), broken).diagnostic().contains("fixture failure"),
            "Capability failures must remain diagnostic rather than crash scanning");
        var manager = helper.getLevel().getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(graph("test:discovered", "graph_assembly:discovery_fixture")));
            GraphIndex.invalidate();
            var index = GraphIndex.get(helper.getLevel());
            check(index.graphs().isEmpty() && index.disabledReason("test:discovered").contains("IAssemblyRecipe=true")
                && index.disabledReason("test:discovered").contains("no verified device adapter"),
                "Discovered assembly type must still be rejected before execution");
            check(index.entries(standalone.getType()).isEmpty() && index.nativeType("test:discovered", "s") == null,
                "Matching an admitted RecipeType must never admit an unsupported serializer into typed routing");
            check(AssemblyDiagnostics.report(helper.getLevel()).stream().anyMatch(line -> line.contains("test:discovered")
                && line.contains("Node s") && line.contains("no verified device adapter")), "Public report must include graph and node diagnostics");
            var server = helper.getLevel().getServer();
            try {
                check(server.getCommands().getDispatcher().execute("graph_assembly compatibility", server.createCommandSourceStack()) == 1,
                    "Operator diagnostic command must be registered");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); }
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "graph_assembly_discovery", timeoutTicks = 100)
    public static void unsupportedTypesExplainRegistryAndAssemblyCapabilities(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(graph("test:unknown", "addon:missing"), graph("test:unsupported", "create:haunting")));
            GraphIndex.invalidate();
            var index = GraphIndex.get(helper.getLevel());
            check(index.graphs().isEmpty(), "Unknown and unsupported types must remain disabled");
            String unknown = index.disabledReason("test:unknown");
            check(unknown.contains("Node s") && unknown.contains("addon:missing") && unknown.contains("Unknown recipe serializer"),
                "Unknown serializer must be distinguished from a missing device adapter: " + unknown);
            String unsupported = index.disabledReason("test:unsupported");
            check(unsupported.contains("Node s") && unsupported.contains("create:haunting") && unsupported.contains("IAssemblyRecipe=false")
                    && unsupported.contains("no verified device adapter"),
                "Registered decodable native type must expose discovered capabilities and missing adapter: " + unsupported);
            helper.succeed();
        } finally {
            manager.replaceRecipes(originals);
            GraphIndex.invalidate();
        }
    }

    private static RecipeHolder<GraphRecipe> graph(String id, String type) {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"%s"}}],"edges":[]}
            """.formatted(type)).getAsJsonObject();
        return new RecipeHolder<>(ResourceLocation.parse(id), new GraphRecipe(json));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
