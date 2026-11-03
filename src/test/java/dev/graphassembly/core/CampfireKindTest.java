package dev.graphassembly.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampfireKindTest {
    private static final String SOURCE = """
        {"transitional_item":"minecraft:iron_nugget","ingredient":{"item":"minecraft:potato"},
         "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:campfire_cooking","recipe_id":"minecraft:campfire_potato"}}],"edges":[]}
        """;

    @Test void omittedAndExplicitKindsParseWithoutChangingRecipeReferences() {
        var data = JsonParser.parseString(SOURCE).getAsJsonObject();
        assertEquals(CampfireKind.ANY, GraphCodec.parse("test:kinds", data).nodes().get("s").campfire());
        var raw = data.getAsJsonArray("nodes").get(0).getAsJsonObject();
        for (var kind : CampfireKind.values()) {
            raw.addProperty("campfire", kind.name().toLowerCase(java.util.Locale.ROOT));
            var node = GraphCodec.parse("test:kinds", JsonParser.parseString(data.toString()).getAsJsonObject()).nodes().get("s");
            assertEquals(kind, node.campfire());
            assertEquals(raw.getAsJsonObject("recipe"), node.recipe());
        }
    }

    @Test void wrongValuesAndLocationsReportTheirFieldPath() {
        var data = JsonParser.parseString(SOURCE).getAsJsonObject();
        var node = data.getAsJsonArray("nodes").get(0).getAsJsonObject();
        for (String value : new String[]{"null", "2", "true", "\"SOUL\"", "\"both\""}) {
            node.add("campfire", JsonParser.parseString(value));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:kinds", data))
                .getMessage().contains("nodes[0].campfire"));
        }
        node.remove("campfire");
        node.getAsJsonObject("recipe").addProperty("campfire", "soul");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:kinds", data))
            .getMessage().contains("nodes[0].recipe.campfire"));
        node.getAsJsonObject("recipe").remove("campfire");
        node.getAsJsonObject("recipe").addProperty("type", "minecraft:smelting");
        node.addProperty("campfire", "any");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GraphCodec.parse("test:kinds", data))
            .getMessage().contains("nodes[0].campfire"));
    }
}
