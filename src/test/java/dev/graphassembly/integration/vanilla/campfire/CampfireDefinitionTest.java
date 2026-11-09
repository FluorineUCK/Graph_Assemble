package dev.graphassembly.integration.vanilla.campfire;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.recipe.OperationSpec;
import dev.graphassembly.recipe.RecipeMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes device JSON and diagnostic ordering at the graph codec boundary. */
class CampfireDefinitionTest {
    private static JsonObject graph(String type) {
        JsonObject graph = JsonParser.parseString("""
            {"transitional_item":"minecraft:iron_nugget","ingredient":{"item":"minecraft:potato"},
             "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:campfire_cooking",
             "recipe_id":"minecraft:campfire_potato"}}],"edges":[]}
            """).getAsJsonObject();
        node(graph).getAsJsonObject("recipe").addProperty("type", type);
        return graph;
    }

    private static JsonObject node(JsonObject graph) {
        return graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
    }

    private static void error(JsonObject graph, String expected) {
        assertEquals("Graph test:device: " + expected,
            assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:device", graph)).getMessage());
    }

    @Test void omittedKindsPreserveExistingDefaults() {
        var campfire = GraphCodec.parse("test:device", graph("minecraft:campfire_cooking")).nodes().get("s").operation();
        assertEquals(CampfireKind.ANY, campfire.campfire());
        assertTrue(campfire.isCampfire());
        var other = GraphCodec.parse("test:device", graph("minecraft:smelting")).nodes().get("s").operation();
        assertNull(other.campfire());
        assertFalse(other.isCampfire());
    }

    @Test void acceptsExactlyTheThreeLowerCaseKinds() {
        JsonObject graph = graph("minecraft:campfire_cooking");
        for (CampfireKind kind : CampfireKind.values()) {
            node(graph).addProperty("campfire", kind.name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(kind, GraphCodec.parse("test:device", graph).nodes().get("s").operation().campfire());
        }
        for (String value : new String[]{"SOUL", "both", " normal "}) {
            node(graph).addProperty("campfire", value);
            error(graph, "nodes[0].campfire: must be normal, soul, or any");
        }
    }

    @Test void graphPlacementWinsOverOtherGraphErrors() {
        JsonObject graph = graph("minecraft:campfire_cooking");
        graph.addProperty("campfire", "any");
        graph.addProperty("unexpected_field", 1);
        error(graph, "graph.campfire: must be on a campfire node");
    }

    @Test void recipePlacementWinsOverRecipeMode() {
        JsonObject graph = graph("minecraft:campfire_cooking");
        node(graph).getAsJsonObject("recipe").addProperty("campfire", "any");
        node(graph).addProperty("recipe_mode", "invalid");
        error(graph, "nodes[0].recipe.campfire: must be beside recipe on the node");
        node(graph).getAsJsonObject("recipe").addProperty("type", 3);
        error(graph, "nodes[0].recipe.type: must be a nonempty string");
    }

    @Test void nonCampfireRejectsEvenAnyBeforeValueAndModeValidation() {
        JsonObject graph = graph("minecraft:smelting");
        node(graph).addProperty("recipe_mode", "invalid");
        for (String value : new String[]{"\"any\"", "null", "2", "{}"}) {
            node(graph).add("campfire", JsonParser.parseString(value));
            error(graph, "nodes[0].campfire: requires a minecraft:campfire_cooking node");
        }
    }

    @Test void malformedKindWinsOverRecipeMode() {
        JsonObject graph = graph("minecraft:campfire_cooking");
        node(graph).addProperty("recipe_mode", "invalid");
        for (String value : new String[]{"null", "2", "true", "{}", "[]", "\"\"", "\"  \""}) {
            node(graph).add("campfire", JsonParser.parseString(value));
            error(graph, "nodes[0].campfire: must be a nonempty string");
        }
        node(graph).addProperty("campfire", "invalid");
        error(graph, "nodes[0].campfire: must be normal, soul, or any");
        node(graph).addProperty("campfire", "soul");
        error(graph, "nodes[0].recipe_mode: expected native or step");
    }

    @Test void deviceKindParticipatesInGraphEqualityAndRecipeIsDefensivelyCopied() {
        JsonObject graph = graph("minecraft:campfire_cooking");
        node(graph).addProperty("campfire", "normal");
        var normal = GraphCodec.parse("test:device", graph);
        assertEquals(normal, GraphCodec.parse("test:device", graph.deepCopy()));
        node(graph).addProperty("campfire", "soul");
        var soul = GraphCodec.parse("test:device", graph);
        assertNotEquals(normal, soul);
        var operation = normal.nodes().get("s").operation();
        JsonObject originalRecipe = operation.recipe();
        node(graph).getAsJsonObject("recipe").addProperty("recipe_id", "test:changed");
        JsonObject exposedRecipe = operation.recipe();
        exposedRecipe.addProperty("recipe_id", "test:also_changed");
        assertEquals(originalRecipe, operation.recipe());
        assertEquals(CampfireKind.NORMAL, operation.campfire());
        assertTrue(operation.completeRecipe());
    }

    @Test void operationConstructionRequiresApplicableDeviceConfigurationAndMode() {
        JsonObject campfire = node(graph("minecraft:campfire_cooking")).getAsJsonObject("recipe");
        JsonObject other = node(graph("minecraft:smelting")).getAsJsonObject("recipe");
        assertThrows(IllegalArgumentException.class, () -> new OperationSpec(campfire, RecipeMode.STEP, null));
        assertThrows(IllegalArgumentException.class, () -> new OperationSpec(other, RecipeMode.STEP, CampfireKind.ANY));
        assertThrows(NullPointerException.class, () -> new OperationSpec(campfire, null, CampfireKind.ANY));
        assertEquals(CampfireKind.SOUL, new OperationSpec(campfire, RecipeMode.STEP, CampfireKind.SOUL).campfire());
        assertNull(new OperationSpec(other, RecipeMode.STEP, null).campfire());
    }

    @Test void operationCopiesNestedRecipeAtConstructionAndAccess() {
        JsonObject recipe = node(graph("minecraft:campfire_cooking")).getAsJsonObject("recipe");
        recipe.add("ingredient", JsonParser.parseString("{\"item\":\"minecraft:potato\"}"));
        JsonObject expected = recipe.deepCopy();
        var operation = new OperationSpec(recipe, RecipeMode.STEP, CampfireKind.NORMAL);
        recipe.getAsJsonObject("ingredient").addProperty("item", "test:mutated_source");
        operation.recipe().getAsJsonObject("ingredient").addProperty("item", "test:mutated_accessor");
        assertEquals(expected, operation.recipe());
    }

    @Test void completeRecipeRemainsNativeModeOrRecipeIdPresence() {
        JsonObject recipe = node(graph("minecraft:smelting")).getAsJsonObject("recipe");
        recipe.remove("recipe_id");
        assertFalse(new OperationSpec(recipe, RecipeMode.STEP, null).completeRecipe());
        assertTrue(new OperationSpec(recipe, RecipeMode.NATIVE, null).completeRecipe());
        recipe.add("recipe_id", com.google.gson.JsonNull.INSTANCE);
        assertTrue(new OperationSpec(recipe, RecipeMode.STEP, null).completeRecipe());
    }
}
