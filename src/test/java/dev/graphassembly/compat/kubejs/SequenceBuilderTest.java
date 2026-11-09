package dev.graphassembly.compat.kubejs;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequenceBuilderTest {
    @Test void sequenceOwnsLoopsResultsAndSnapshotsNestedStep() {
        var recipe = JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"cookingtime\":31}").getAsJsonObject();
        var options = JsonParser.parseString("{\"recipe_mode\":\"native\"}").getAsJsonObject();
        var result = JsonParser.parseString("{\"id\":\"minecraft:iron_pickaxe\",\"components\":{\"minecraft:damage\":5},\"chance\":0.4}").getAsJsonObject();
        var builder = new SequenceBuilder(JsonParser.parseString("{\"id\":\"minecraft:iron_nugget\"}"))
            .ingredient(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"))
            .step(recipe, options).loops(2).output(result);
        recipe.addProperty("cookingtime", 99); options.addProperty("recipe_mode", "step"); result.addProperty("chance", 1);
        var built = builder.build();
        assertEquals("create:sequenced_assembly", built.get("type").getAsString());
        assertEquals(2, built.get("loops").getAsInt());
        var wrapper = built.getAsJsonArray("sequence").get(0).getAsJsonObject();
        assertEquals("graph_assembly:sequence_step", wrapper.get("type").getAsString());
        assertEquals(31, wrapper.getAsJsonObject("step").getAsJsonObject("recipe").get("cookingtime").getAsInt());
        assertEquals("native", wrapper.getAsJsonObject("step").get("recipe_mode").getAsString());
        assertEquals(0.4, built.getAsJsonArray("results").get(0).getAsJsonObject().get("chance").getAsDouble());
        built.getAsJsonArray("sequence").remove(0);
        assertEquals(1, builder.build().getAsJsonArray("sequence").size());
        assertThrows(IllegalArgumentException.class, () -> builder.loops(0));
        assertThrows(IllegalArgumentException.class, () -> new SequenceBuilder(JsonParser.parseString("{}" )).build());
    }
}
