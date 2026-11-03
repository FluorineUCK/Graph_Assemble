package dev.graphassembly.integration;

import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTemplatePreviewDataTest {
    @Test void fullNativePreviewOwnsTheOriginalInputsResultsAndSupplierCandidates() {
        var source = JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
              {"id":"f","recipe_mode":"native","recipe":{"type":"create:mixing",
               "ingredients":[{"type":"neoforge:single","fluid":"minecraft:water","amount":250},
                              {"item":"minecraft:gold_ingot"},{"item":"minecraft:gold_ingot"}],
               "results":[{"id":"minecraft:diamond"},{"id":"minecraft:gravel","chance":0.25}],
               "processing_time":77,"heat_requirement":"heated"}}],
             "edges":[{"from":"s","to":"f"}]}
            """).getAsJsonObject();
        var graph = GraphCodec.parse("test:native", source);
        var node = graph.nodes().get("f");
        assertEquals(node.recipe(), PreviewRecipeData.forNode(graph, node), "Native input/result lists must not be prepended");
        assertTrue(PreviewRecipeData.nativeInputsToHide(graph, node).isEmpty(), "Complete supplier inputs remain visible");
        var preview = PreviewRecipeData.forNode(graph, node);
        preview.getAsJsonArray("results").get(1).getAsJsonObject().addProperty("chance", 1);
        assertEquals(0.25, node.recipe().getAsJsonArray("results").get(1).getAsJsonObject().get("chance").getAsDouble());
        source.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").addProperty("type", "minecraft:smithing_trim");
        var dynamicGraph = GraphCodec.parse("test:dynamic_native", source);
        assertEquals(node.recipe(), PreviewRecipeData.forNode(dynamicGraph, dynamicGraph.nodes().get("f")),
            "Complete raw previews do not invent or require a static primary carrier");
    }
}
