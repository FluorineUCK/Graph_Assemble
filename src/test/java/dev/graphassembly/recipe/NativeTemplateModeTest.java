package dev.graphassembly.recipe;

import dev.graphassembly.codec.GraphCodec;

import dev.graphassembly.core.definition.basic.NodeDefinition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTemplateModeTest {
    @Test void invalidRecipeModeIsRejectedAtTheNodePath() {
        var json = graph();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("recipe_mode", "natve");
        var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:native", json));
        assertTrue(error.getMessage().contains("nodes[0].recipe_mode"), error.getMessage());
    }

    @Test void nativeModeSurvivesJsonRoundTripWhileLegacyAndExplicitStepRemainShorthand() {
        var json = graph();
        assertFalse((GraphCodec.parse("test:legacy", json).nodes().get("s").operation().recipeMode() == RecipeMode.NATIVE));
        assertFalse(GraphCodec.parse("test:legacy", json).nodes().get("s").operation().completeRecipe());
        var raw = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        raw.addProperty("recipe_mode", "step");
        assertFalse((GraphCodec.parse("test:step", json).nodes().get("s").operation().recipeMode() == RecipeMode.NATIVE));
        raw.addProperty("recipe_mode", "native");
        var node = GraphCodec.parse("test:native", JsonParser.parseString(json.toString()).getAsJsonObject()).nodes().get("s");
        assertTrue((node.operation().recipeMode() == RecipeMode.NATIVE));
        assertTrue(node.operation().completeRecipe());
        assertFalse(node.operation().recipe().has("recipe_mode"), "Mode is node metadata, not native serializer data");
        raw.remove("recipe_mode");
        raw.getAsJsonObject("recipe").addProperty("recipe_id", "test:ordinary");
        var reference = GraphCodec.parse("test:reference", json).nodes().get("s");
        assertFalse((reference.operation().recipeMode() == RecipeMode.NATIVE));
        assertTrue(reference.operation().completeRecipe());
    }

    @Test void nonStringAndNullModesAreNotSilentlyDefaulted() {
        for (String invalid : new String[]{"true", "7", "null", "{}", "[]", "\"Native\"", "\"\""}) {
            var json = graph();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe_mode", JsonParser.parseString(invalid));
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:mode", json));
            assertTrue(error.getMessage().contains("nodes[0].recipe_mode"), error.getMessage());
        }
    }

    @Test void groupedNodeConstructionKeepsExplicitOperationMode() {
        var parsed = GraphCodec.parse("test:grouped_constructor", graph()).nodes().get("s");
        var copied = new NodeDefinition<>(parsed.id(),parsed.roles(),parsed.operation(),parsed.inputs(),
            parsed.effects(),parsed.routing(),parsed.settlement());
        assertEquals(parsed,copied);
        assertEquals(RecipeMode.STEP,copied.operation().recipeMode());
    }

    @Test void campfireValidationStillPrecedesRecipeModeValidation() {
        var json = JsonParser.parseString("""
            {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},
               "campfire":"invalid","recipe_mode":"invalid"}],"edges":[]}
            """).getAsJsonObject();
        assertTrue(assertThrows(IllegalArgumentException.class,()->GraphCodec.parse("test:order",json))
            .getMessage().contains("nodes[0].campfire"));
    }

    private static JsonObject graph() {
        return JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}}],"edges":[]}
            """).getAsJsonObject();
    }
}
