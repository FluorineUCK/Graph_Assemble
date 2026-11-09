package dev.graphassembly.codec;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.route.BranchMode;
import dev.graphassembly.core.settlement.FinishMode;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GraphCodecTest {
    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "0", "1.5", "\"2\"", "null", "true", "{}", "[]"})
    void unknownGraphFieldsReportTheirPathForEveryValue(String value) {
        var json = valid();
        json.add("unexpected_field", JsonParser.parseString(value));
        var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:field_validation", json));
        assertTrue(error.getMessage().contains("test:field_validation"));
        assertTrue(error.getMessage().contains("graph.unexpected_field"));
        assertTrue(error.getMessage().contains("unknown field"), error.getMessage());
    }
    @Test void branchingExampleParsesMarkAndCheckpointRules() throws Exception {
        var source = Files.readString(Path.of("examples", "datapack", "branching.json"));
        var graph = GraphCodec.parse("example:branching", JsonParser.parseString(source).getAsJsonObject());
        assertEquals(3, graph.outputRules().size());
        assertEquals(java.util.Map.of("polished", 1, "complete", 1),
            graph.outputRules().getFirst().requirements().marks().match().values());
    }
    @Test void retainedItemFirstExamplePreservesDisabledMarksAndPartialCheckpoints() throws Exception {
        var source = Files.readString(Path.of("examples", "datapack", "item-first.json"));
        var graph = GraphCodec.parse("example:item_first", JsonParser.parseString(source).getAsJsonObject());
        assertEquals(3, graph.outputRules().size());
        var fallback = graph.outputRules().get(2);
        assertNull(fallback.requirements().marks().match());
        assertEquals(CheckpointMode.SUBSEQUENCE, fallback.requirements().checkpoints().match().mode());
        assertEquals(java.util.List.of("start", "finish"), fallback.requirements().checkpoints().match().nodes());
        assertEquals("minecraft:iron_ingot", fallback.leaves().getFirst().item());
    }
    @Test void omittedRequirementsDoNotRestrictMarksOrCheckpoints() {
        var json = valid();
        var graph = GraphCodec.parse("test:current", json);
        assertNull(graph.outputRules().getFirst().requirements().marks().match());
        assertNull(graph.outputRules().getFirst().requirements().checkpoints().match());
        assertTrue(ProgressMatcher.matches(graph.outputRules().getFirst().requirements(), new ProgressFacts(java.util.Map.of("extra", 1), java.util.List.of("s"))));
    }
    @Test void typedItemIngredientsKeepTheirOpaqueFluidNamedFields() {
        var json = valid();
        var ingredient = JsonParser.parseString("""
            [{"type":"example:filled_container","fluid":"minecraft:water"},{"item":"minecraft:bucket"}]
            """);
        json.add("ingredient", ingredient);
        assertEquals(ingredient, GraphCodec.parse("test:custom_item",json).defaults().ingredient());
    }
    @Test
    void omittedConditionsAreDisabledAtGraphAndNodeLevels() {
        var json = valid();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", json.getAsJsonArray("outputs").deepCopy());
        var graph = GraphCodec.parse("test:current", json);
        for (var rule : java.util.List.of(graph.outputRules().getFirst(), graph.nodes().get("s").settlement().outputRules().getFirst())) {
            assertNull(rule.requirements().marks().match());
            assertNull(rule.requirements().checkpoints().match());
            assertTrue(ProgressMatcher.matches(rule.requirements(), new ProgressFacts(java.util.Map.of("extra", 3), java.util.List.of("s"))));
        }
    }

    private static ProgressRequirements requirements(String fields) {
        var json = valid();
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements", JsonParser.parseString(fields));
        return GraphCodec.parse("test:requirements",json).outputRules().getFirst().requirements();
    }

    @Test
    void shorthandAndFullFormsNormalizeInheritedAndOverriddenModes() {
        assertEquals(ProgressRequirements.NONE, requirements("{}"));
        assertEquals(ProgressRequirements.NONE, requirements("{\"mode\":\"partial\"}"));
        var shorthand = requirements("""
            {"mode":"partial","marks":{"x":3},"checkpoints":["s","s"]}
            """);
        var full = requirements("""
            {"marks":{"mode":"partial","values":{"x":3}},"checkpoints":{"mode":"partial","nodes":["s","s"]}}
            """);
        assertEquals(shorthand, full);
        assertTrue(ProgressMatcher.matches(full, new ProgressFacts(java.util.Map.of("x", 4, "extra", 1), java.util.List.of("s", "s", "s"))));
        var override = requirements("""
            {"mode":"partial","marks":{"mode":"exact","values":{}},"checkpoints":{"mode":"exact","nodes":[]}}
            """);
        assertEquals(requirements("{\"marks\":{},\"checkpoints\":[]}"), override);
        assertFalse(ProgressMatcher.matches(override, new ProgressFacts(java.util.Map.of(), java.util.List.of("s"))));
        assertEquals(java.util.Map.of("mode", 2, "values", 3), requirements("{\"marks\":{\"mode\":2,\"values\":3}}").marks().match().values());
        assertEquals(CheckpointMode.UNORDERED, requirements("{\"checkpoints\":{\"mode\":\"unordered\",\"nodes\":[\"s\"]}}").checkpoints().match().mode());
        assertNull(requirements("{\"marks\":{}}").checkpoints().match());
        assertNull(requirements("{\"checkpoints\":[]}").marks().match());
    }

    static Stream<Arguments> invalidRequirementFields() {
        return Stream.of(
            Arguments.of("marks", "{}", "outputs[0].marks"),
            Arguments.of("checkpoints", "[]", "outputs[0].checkpoints"),
            Arguments.of("requirements", "null", "outputs[0].requirements"),
            Arguments.of("requirements", "[]", "outputs[0].requirements"),
            Arguments.of("requirements", "{\"mark\":{}}", "requirements.mark"),
            Arguments.of("requirements", "{\"mode\":null}", "requirements.mode"),
            Arguments.of("requirements", "{\"mode\":\"unordered\"}", "requirements.mode"),
            Arguments.of("requirements", "{\"mode\":\"subsequence\"}", "requirements.mode"),
            Arguments.of("requirements", "{\"marks\":null}", "requirements.marks"),
            Arguments.of("requirements", "{\"marks\":[]}", "requirements.marks"),
            Arguments.of("requirements", "{\"marks\":{\"x\":-1}}", "requirements.marks.x"),
            Arguments.of("requirements", "{\"marks\":{\"x\":1.5}}", "requirements.marks.x"),
            Arguments.of("requirements", "{\"marks\":{\"x\":2147483648}}", "requirements.marks.x"),
            Arguments.of("requirements", "{\"marks\":{\"mode\":\"exact\"}}", "requirements.marks.mode"),
            Arguments.of("requirements", "{\"marks\":{\"values\":{}}}", "requirements.marks.mode"),
            Arguments.of("requirements", "{\"marks\":{\"mode\":\"unordered\",\"values\":{}}}", "requirements.marks.mode"),
            Arguments.of("requirements", "{\"marks\":{\"mode\":\"exact\",\"values\":null}}", "requirements.marks"),
            Arguments.of("requirements", "{\"marks\":{\"mode\":\"exact\",\"values\":{},\"extra\":0}}", "requirements.marks.extra"),
            Arguments.of("requirements", "{\"checkpoints\":null}", "requirements.checkpoints"),
            Arguments.of("requirements", "{\"checkpoints\":\"s\"}", "requirements.checkpoints"),
            Arguments.of("requirements", "{\"checkpoints\":{\"mode\":\"exact\"}}", "requirements.checkpoints.nodes"),
            Arguments.of("requirements", "{\"checkpoints\":{\"nodes\":[]}}", "requirements.checkpoints.mode"),
            Arguments.of("requirements", "{\"checkpoints\":{\"mode\":\"foo\",\"nodes\":[]}}", "requirements.checkpoints.mode"),
            Arguments.of("requirements", "{\"checkpoints\":{\"mode\":\"exact\",\"nodes\":null}}", "requirements.checkpoints.nodes"),
            Arguments.of("requirements", "{\"checkpoints\":{\"mode\":\"exact\",\"nodes\":[],\"extra\":0}}", "requirements.checkpoints.extra"),
            Arguments.of("requirements", "{\"checkpoints\":[null]}", "requirements.checkpoints[0]"),
            Arguments.of("requirements", "{\"checkpoints\":[\"missing\"]}", "requirements.checkpoints[0]"),
            Arguments.of("requirements", "{\"checkpoints\":{\"mode\":\"exact\",\"nodes\":[\"missing\"]}}", "requirements.checkpoints.nodes[0]")
        );
    }

    @ParameterizedTest @MethodSource("invalidRequirementFields")
    void rejectsInvalidRequirementsWithActualRuleIndexAndFieldPath(String field, String value, String path) {
        for (boolean local : java.util.List.of(false, true)) {
            var json = valid();
            var rule = json.getAsJsonArray("outputs").get(0).getAsJsonObject();
            rule.add(field, JsonParser.parseString(value));
            if (local) {
                json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", json.remove("outputs"));
            }
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:bad_requirements", json));
            assertTrue(error.getMessage().contains("test:bad_requirements"), error.getMessage());
            assertTrue(error.getMessage().contains(path), error.getMessage());
            if (local) assertTrue(error.getMessage().contains("nodes[0].outputs[0]"), error.getMessage());
        }
    }

    @Test
    void legacyRuleFieldsAndCheckpointModeAreRejectedAtBothLevels() {
        for (boolean local : java.util.List.of(false, true)) {
            for (String legacy : java.util.List.of("marks", "checkpoints")) {
                var json = valid();
                var rule = json.getAsJsonArray("outputs").get(0).getAsJsonObject();
                rule.add(legacy, JsonParser.parseString(legacy.equals("marks") ? "{}" : "[]"));
                if (local) json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", json.remove("outputs"));
                var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:legacy", json));
                assertTrue(error.getMessage().contains("test:legacy"), error.getMessage());
                assertTrue(error.getMessage().contains((local ? "nodes[0].outputs[0]." : "graph.outputs[0].") + legacy), error.getMessage());
            }
            var json = valid();
            json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("requirements",
                JsonParser.parseString("{\"checkpoints\":{\"mode\":\"subsequence\",\"nodes\":[\"s\"]}}"));
            if (local) json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", json.remove("outputs"));
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:legacy", json));
            assertTrue(error.getMessage().contains("test:legacy"), error.getMessage());
            assertTrue(error.getMessage().contains((local ? "nodes[0].outputs[0]." : "graph.outputs[0].")
                + "requirements.checkpoints.mode"), error.getMessage());
        }
    }

    @Test
    void legacyDiagnosticsUseActualNonzeroNodeAndRuleIndexes() {
        for (boolean local : java.util.List.of(false, true)) {
            var json = valid();
            var invalidRule = json.getAsJsonArray("outputs").get(0).deepCopy().getAsJsonObject();
            invalidRule.add("marks", JsonParser.parseString("{}"));
            if (local) {
                json.getAsJsonArray("nodes").add(JsonParser.parseString("""
                    {"id":"t","recipe":{"type":"create:cutting"},
                     "outputs":[{"results":[{"item":"test:ok"}]}]}
                    """));
                json.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("outputs").add(invalidRule);
            } else json.getAsJsonArray("outputs").add(invalidRule);
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:indices", json));
            assertTrue(error.getMessage().contains("test:indices"), error.getMessage());
            assertTrue(error.getMessage().contains(local ? "nodes[1].outputs[1].marks" : "graph.outputs[1].marks"),
                error.getMessage());
        }
    }

    @Test
    void partialKeepsRepeatedCheckpointVisitsAndExplicitEmptyIsNotDisabled() {
        for (boolean local : java.util.List.of(false, true)) {
            var json = valid();
            var rule = json.getAsJsonArray("outputs").get(0).getAsJsonObject();
            rule.add("requirements", JsonParser.parseString("{\"checkpoints\":{\"mode\":\"partial\",\"nodes\":[\"s\",\"s\"]}}"));
            if (local) json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", json.remove("outputs"));
            var graph = GraphCodec.parse("test:repeated", json);
            var requirement = local ? graph.nodes().get("s").settlement().outputRules().getFirst().requirements()
                : graph.outputRules().getFirst().requirements();
            assertFalse(ProgressMatcher.matches(requirement, new ProgressFacts(java.util.Map.of(), java.util.List.of("s"))));
            assertTrue(ProgressMatcher.matches(requirement, new ProgressFacts(java.util.Map.of(), java.util.List.of("s", "s"))));
            assertEquals(java.util.List.of("s", "s"), requirement.checkpoints().match().nodes());

            var explicit = valid();
            var explicitRule = explicit.getAsJsonArray("outputs").get(0).getAsJsonObject();
            explicitRule.add("requirements", JsonParser.parseString("{\"marks\":{},\"checkpoints\":[]}"));
            if (local) explicit.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", explicit.remove("outputs"));
            var parsed = GraphCodec.parse("test:empty", explicit);
            var conditions = local ? parsed.nodes().get("s").settlement().outputRules().getFirst().requirements()
                : parsed.outputRules().getFirst().requirements();
            assertNotNull(conditions.marks().match());
            assertNotNull(conditions.checkpoints().match());
            assertFalse(ProgressMatcher.matches(conditions, new ProgressFacts(java.util.Map.of("extra", 1), java.util.List.of("s"))));
        }
    }

    @Test
    void fluidCarriersAndSettlementOutputsHaveExplicitItemOnlyDiagnostics() {
        for (String target : java.util.List.of("ingredient", "transitional_item", "output")) {
            var json = valid();
            if (target.equals("output")) json.getAsJsonArray("outputs").get(0).getAsJsonObject()
                .add("results", JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":250}]"));
            else json.add(target, JsonParser.parseString("{\"fluid\":\"minecraft:water\",\"amount\":250}"));
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:fluid_carrier", json));
            assertTrue(error.getMessage().contains("item") && error.getMessage().contains("fluid"), error.getMessage());
        }
    }

    private static JsonObject valid() {
        return JsonParser.parseString("""
                {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
                 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[],"outputs":[{"results":[{"item":"test:result"}]}]}
                """).getAsJsonObject();
    }

    static Stream<Arguments> malformedFields() {
        return Stream.of(
                Arguments.of("transitional_item", "null"),
                Arguments.of("transitional_item", "17"),
                Arguments.of("transitional_item", "\"\""),
                Arguments.of("nodes", "{}"),
                Arguments.of("nodes", "[]"),
                Arguments.of("nodes.0.id", "\" \""),
                Arguments.of("nodes.0.start", "false"),
                Arguments.of("nodes.0.start", "\"true\""),
                Arguments.of("nodes.0.recipe", "null"),
                Arguments.of("nodes.0.recipe.type", "null"),
                Arguments.of("nodes.0.recipe.type", "5"),
                Arguments.of("nodes.0.mark_amounts", "{\"work\":-1}"),
                Arguments.of("nodes.0.mark_amounts", "{\"work\":1.5}"),
                Arguments.of("nodes.0.max_mark_applications", "-1"),
                Arguments.of("nodes.0.max_mark_applications", "1.5"),
                Arguments.of("nodes.0.branch_mode", "\"sometimes\""),
                Arguments.of("nodes.0.finish_mode", "\"sometimes\""),
                Arguments.of("nodes.0.position", "[1]"),
                Arguments.of("nodes.0.position", "[1,1e400]"),
                Arguments.of("nodes.0.position", "[1,\"2\"]"),
                Arguments.of("nodes.0.sphere_position", "[1,2]"),
                Arguments.of("nodes.0.sphere_position", "[0,0,0]"),
                Arguments.of("nodes.0.sphere_position", "[1,0,1e400]"),
                Arguments.of("edges", "[{\"from\":\"s\",\"to\":\"missing\"}]"),
                Arguments.of("edges", "[{\"from\":\"s\",\"to\":\"s\",\"weight\":0}]"),
                Arguments.of("edges", "[{\"from\":\"s\",\"to\":\"s\",\"weight\":-1}]"),
                Arguments.of("edges", "[{\"from\":\"s\",\"to\":\"s\",\"weight\":1e400}]"),
                Arguments.of("outputs.0.results", "[]"),
                Arguments.of("outputs.0.results.0.item", "null"),
                Arguments.of("outputs.0.results.0.count", "-1"),
                Arguments.of("outputs.0.results.0.count", "0"),
                Arguments.of("outputs.0.results.0.count", "1.5"),
                Arguments.of("outputs.0.results.0.weight", "0"),
                Arguments.of("outputs.0.results.0.weight", "1e400"),
                Arguments.of("outputs.0.results.0.components", "[]"),
                Arguments.of("outputs.0.start", "\"missing\""),
                Arguments.of("outputs.0.finish", "\"missing\""),
                Arguments.of("outputs.0.marks", "{\"work\":-1}"),
                Arguments.of("outputs.0.checkpoints", "[\"missing\"]"),
                Arguments.of("outputs.0.checkpoints", "{\"mode\":\"almost\",\"nodes\":[\"s\"]}"),
                Arguments.of("outputs.0.checkpoints", "{\"mode\":\"exact\"}"));
    }

    @ParameterizedTest
    @MethodSource("malformedFields")
    void invalidAuthorDataIsRejectedWithGraphContext(String path, String replacement) {
        JsonObject json = valid();
        String[] keys = path.split("\\.");
        com.google.gson.JsonElement parent = json;
        for (int i = 0; i < keys.length - 1; i++) {
            parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(keys[i]))
                    : parent.getAsJsonObject().get(keys[i]);
        }
        parent.getAsJsonObject().add(keys[keys.length - 1], JsonParser.parseString(replacement));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", json));
        assertTrue(error.getMessage().contains("test:assembly"), error.getMessage());
    }

    @Test
    void defaultsExposeAutomaticCheckpointsAndPreserveOpaqueNativeRecipeFields() {
        JsonObject json = valid();
        json.add("neoforge:conditions", JsonParser.parseString(
            "[{\"type\":\"neoforge:mod_loaded\",\"modid\":\"create\"}]"));
        JsonObject node = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.addProperty("checkpoint", false);
        node.getAsJsonObject("recipe").addProperty("processingTime", 123);
        node.getAsJsonObject("recipe").addProperty("custom_metadata", 9);
        node.add("position", JsonParser.parseString("[0,-2.5]"));
        node.add("sphere_position", JsonParser.parseString("[1e300,0,0]"));
        GraphDefinition<OperationSpec> graph = GraphCodec.parse("test:assembly", json);
        NodeDefinition<OperationSpec> parsed = graph.nodes().get("s");
        assertTrue(parsed.effects().recordCheckpoint());
        assertEquals(BranchMode.CHOICE, parsed.routing().branchMode());
        assertEquals(FinishMode.LENIENT, parsed.settlement().finishMode());
        assertNull(parsed.effects().maxMarkApplications());
        assertNull(parsed.effects().carrierOverride());
        assertNull(parsed.inputs().ingredientOverride());
        assertTrue(parsed.effects().markAmounts().isEmpty());
        assertEquals(java.util.List.of(0d, -2.5), graph.layout().node(parsed.id()).position());
        assertEquals(java.util.List.of(1e300, 0d, 0d), graph.layout().node(parsed.id()).spherePosition());
        assertEquals(123, parsed.operation().recipe().get("processingTime").getAsInt());
        assertEquals(9, parsed.operation().recipe().get("custom_metadata").getAsInt());
        assertEquals(1, graph.outputRules().getFirst().leaves().getFirst().count());
        assertEquals(1d, graph.outputRules().getFirst().leaves().getFirst().weight());
        assertTrue(graph.outputRules().getFirst().leaves().getFirst().components().isEmpty());
        json.getAsJsonObject("ingredient").addProperty("item", "test:mutated");
        parsed.operation().recipe().addProperty("processingTime", 99);
        assertEquals("test:raw", graph.defaults().ingredient().getAsJsonObject().get("item").getAsString());
        assertEquals(123, parsed.operation().recipe().get("processingTime").getAsInt());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    }

    @Test
    void kubeRecipeChangeMarkerDoesNotAffectGraphSemantics() {
        var json = valid();
        var expected = GraphCodec.parse("test:assembly", json);
        var marker = JsonParser.parseString("{\"source\":\"server_scripts:graph.js\",\"line\":12}");
        json.add("_kubejs_changed_marker", marker.deepCopy());
        assertEquals(expected, GraphCodec.parse("test:assembly", json));
        assertEquals(marker, json.get("_kubejs_changed_marker"));
    }

    @Test
    void duplicateNodeIdsAndDirectedEdgesAreRejectedIncludingExpandedEdges() {
        JsonObject duplicateNodes = valid();
        duplicateNodes.getAsJsonArray("nodes").add(duplicateNodes.getAsJsonArray("nodes").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", duplicateNodes));
        JsonObject duplicateEdges = valid();
        duplicateEdges.add("edges", JsonParser.parseString("""
                [{"from":"s","to":"s","weight":1},{"from":"s","to":"s","weight":2}]
                """));
        assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", duplicateEdges));
        JsonObject expanded = valid();
        expanded.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"a\",\"recipe\":{\"type\":\"create:cutting\"}}"));
        expanded.add("edges", JsonParser.parseString("""
                [{"from":"s","to":"a","bidirectional":true},{"from":"a","to":"s"}]
                """));
        assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", expanded));
        JsonObject selfLoop = valid();
        selfLoop.add("edges", JsonParser.parseString("[{\"from\":\"s\",\"to\":\"s\",\"bidirectional\":true}]"));
        assertEquals(1, GraphCodec.parse("test:assembly", selfLoop).topology().edges().size());
    }

    @Test
    void requiredFieldsAndAnEffectiveIngredientForEveryStartAreEnforced() {
        for (String field : java.util.List.of("transitional_item", "nodes", "edges", "ingredient")) {
            JsonObject json = valid();
            json.remove(field);
            assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", json), field);
        }
        for (String field : java.util.List.of("id", "recipe")) {
            JsonObject json = valid();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().remove(field);
            assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", json), field);
        }
        JsonObject override = valid();
        override.remove("ingredient");
        override.getAsJsonArray("nodes").get(0).getAsJsonObject().add("ingredient", JsonParser.parseString("{\"tag\":\"test:raw\"}"));
        GraphDefinition<OperationSpec> graph = GraphCodec.parse("test:assembly", override);
        assertNull(graph.defaults().ingredient());
        assertEquals("test:raw", graph.nodes().get("s").inputs().ingredientOverride().getAsJsonObject().get("tag").getAsString());
    }

    @Test
    void nodeLocalOutputReferencesAreValidatedToo() {
        JsonObject json = valid();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("outputs", JsonParser.parseString("""
                [{"finish":"missing","results":[{"item":"test:result"}]}]
                """));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:assembly", json));
        assertTrue(error.getMessage().contains("nodes[0].outputs[0].finish"));
    }
}
