package dev.graphassembly.core;

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
        assertFalse(GraphCodec.parse("test:legacy", json).nodes().get("s").nativeRecipe());
        assertFalse(GraphCodec.parse("test:legacy", json).nodes().get("s").completeRecipe());
        var raw = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        raw.addProperty("recipe_mode", "step");
        assertFalse(GraphCodec.parse("test:step", json).nodes().get("s").nativeRecipe());
        raw.addProperty("recipe_mode", "native");
        var node = GraphCodec.parse("test:native", JsonParser.parseString(json.toString()).getAsJsonObject()).nodes().get("s");
        assertTrue(node.nativeRecipe());
        assertTrue(node.completeRecipe());
        assertFalse(node.recipe().has("recipe_mode"), "Mode is node metadata, not native serializer data");
        raw.remove("recipe_mode");
        raw.getAsJsonObject("recipe").addProperty("recipe_id", "test:ordinary");
        var reference = GraphCodec.parse("test:reference", json).nodes().get("s");
        assertFalse(reference.nativeRecipe());
        assertTrue(reference.completeRecipe());
    }

    @Test void nonStringAndNullModesAreNotSilentlyDefaulted() {
        for (String invalid : new String[]{"true", "7", "null", "{}", "[]", "\"Native\"", "\"\""}) {
            var json = graph();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe_mode", JsonParser.parseString(invalid));
            var error = assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:mode", json));
            assertTrue(error.getMessage().contains("nodes[0].recipe_mode"), error.getMessage());
        }
    }

    @Test void oldNodeConstructorsStillDefaultToStep() {
        var parsed = GraphCodec.parse("test:old_constructor", graph()).nodes().get("s");
        var copied = new Node(parsed.id(), parsed.start(), parsed.finish(), parsed.ingredient(), parsed.recipe(),
            parsed.transitionalItem(), parsed.rewards(), parsed.rewardLimit(), parsed.checkpoint(), parsed.branchMode(),
            parsed.finishMode(), parsed.outputs(), parsed.scrapEntries(), parsed.position(), parsed.spherePosition(),
            parsed.workpieceInputs(), parsed.progressActions(), parsed.campfire());
        assertEquals(parsed, copied);
        assertFalse(copied.nativeRecipe());
    }

    private static JsonObject graph() {
        return JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}}],"edges":[]}
            """).getAsJsonObject();
    }
}
