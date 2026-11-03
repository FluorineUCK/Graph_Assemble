package dev.graphassembly.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreviewRecipeDataTest {
    @ParameterizedTest
    @ValueSource(strings={"minecraft:crafting_special_armordye","minecraft:crafting_special_repairitem","graph_assembly:brewing"})
    void dynamicCarrierDataflowStaysPureAndNeverInventsGraphDefault(String serializer) {
        var graph=dev.graphassembly.core.GraphCodec.parse("test:pure_special",json("""
            {"ingredient":{"item":"minecraft:leather_chestplate"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"special","start":true,"recipe":{"type":"%s"}},
              {"id":"next","recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"special","to":"next"}],"outputs":[]}
            """.formatted(serializer)));
        if (serializer.equals("graph_assembly:brewing")) {
            var descriptor = graph.nodes().get("special");
            assertEquals(descriptor.recipe(), PreviewRecipeData.forNode(graph, descriptor));
        }
        var next=graph.nodes().get("next");
        assertThrows(PreviewRecipeData.UnknownDynamicCarrier.class,()->PreviewRecipeData.carriedInputs(graph,next));
        assertEquals(java.util.List.of(),PreviewRecipeData.nativeInputsToHide(graph,next));
        assertThrows(PreviewRecipeData.UnknownDynamicCarrier.class,()->PreviewRecipeData.forNode(graph,next));
    }

    @Test void anvilDescriptorIsNotMaterializedAndItsSuccessorCarrierIsUnknown() {
        var graph=dev.graphassembly.core.GraphCodec.parse("test:anvil_display",json("""
            {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"combine","start":true,"recipe":{"type":"graph_assembly:anvil","action":"combine","addition":{"item":"minecraft:gold_ingot"}}},
              {"id":"repair","recipe":{"type":"graph_assembly:anvil","action":"repair","addition":{"tag":"c:ingots/iron"}}},
              {"id":"next","recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"combine","to":"repair"},{"from":"repair","to":"next"}],"outputs":[]}
            """));
        var combine=graph.nodes().get("combine");
        var repair=graph.nodes().get("repair");
        var next=graph.nodes().get("next");
        assertThrows(PreviewRecipeData.UnknownDynamicCarrier.class,()->PreviewRecipeData.carriedInputs(graph,repair));
        assertThrows(PreviewRecipeData.UnknownDynamicCarrier.class,()->PreviewRecipeData.forNode(graph,next));
        assertEquals(combine.recipe(),PreviewRecipeData.forNode(graph,combine));
        assertEquals(repair.recipe(),PreviewRecipeData.forNode(graph,repair));
    }
    @Test void nativeInputVisibilityRetainsSupplierAuxiliariesWhenDynamicCarrierIsUnknown() {
        var source=json("""
            {"ingredient":{"item":"minecraft:diamond_chestplate"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"trim","start":true,"recipe":{"type":"minecraft:smithing_trim"}},
              {"id":"finish","finish":true,"recipe":{"type":"minecraft:smithing_trim"}}],
             "edges":[{"from":"trim","to":"finish"}],"outputs":[]}
            """);
        var graph=dev.graphassembly.core.GraphCodec.parse("test:native_supplier",source);var finish=graph.nodes().get("finish");
        var supplier=java.util.List.of("minecraft:diamond_chestplate","minecraft:coast_armor_trim_smithing_template","minecraft:quartz");
        assertThrows(PreviewRecipeData.UnknownDynamicCarrier.class,()->PreviewRecipeData.carriedInputs(graph,finish));
        var hidden=PreviewRecipeData.nativeInputsToHide(graph,finish);
        assertEquals(java.util.List.of(),hidden,"Unknown is not a fabricated default-item exclusion");
        assertEquals(supplier,supplier.stream().filter(item->!hidden.contains(item)).toList(),"Known template, material and native base candidates survive supplier indexing");

        var mixed=source.deepCopy();mixed.getAsJsonArray("nodes").add(json("{\"id\":\"known\",\"start\":true,\"recipe\":{\"type\":\"create:pressing\"}}"));
        mixed.getAsJsonArray("edges").add(json("{\"from\":\"known\",\"to\":\"finish\"}"));
        var mixedGraph=dev.graphassembly.core.GraphCodec.parse("test:mixed_supplier",mixed);
        assertEquals(java.util.List.of(),PreviewRecipeData.nativeInputsToHide(mixedGraph,mixedGraph.nodes().get("finish")),"A partial known set at a mixed merge cannot hide native candidates");

        var known=source.deepCopy();known.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",json("{\"type\":\"create:pressing\"}"));
        var knownGraph=dev.graphassembly.core.GraphCodec.parse("test:known_supplier",known);
        assertEquals(java.util.List.of("minecraft:iron_nugget"),PreviewRecipeData.nativeInputsToHide(knownGraph,knownGraph.nodes().get("finish")),"Existing statically known internal carriers remain hidden");
        assertEquals(java.util.List.of(),PreviewRecipeData.nativeInputsToHide(knownGraph,knownGraph.nodes().get("trim")),"Raw start inputs remain visible even if their item is also a carrier");

        var reference=known.deepCopy();reference.getAsJsonArray("nodes").get(1).getAsJsonObject().add("recipe",json("{\"type\":\"minecraft:smithing_trim\",\"recipe_id\":\"test:native_reference\"}"));
        var referenceGraph=dev.graphassembly.core.GraphCodec.parse("test:reference_supplier",reference);
        assertEquals(java.util.List.of(),PreviewRecipeData.nativeInputsToHide(referenceGraph,referenceGraph.nodes().get("finish")),"Complete native references retain their entire original supplier candidate set");
    }

    @Test void explicitRouteCarriesActualOutputWithoutPretendingToProcessEdgeSource() {
        for (String route : new String[]{"{\"type\":\"goto_node\",\"node\":\"target\"}",
                "{\"type\":\"enter_edge\",\"from\":\"unused\",\"to\":\"target\"}"}) {
            var graph=dev.graphassembly.core.GraphCodec.parse("test:route_preview", json("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"transitional_item":"minecraft:gold_nugget","recipe":{"type":"create:pressing"},"progress_actions":[%s]},
                 {"id":"target","recipe":{"type":"create:cutting"},"workpiece_inputs":[{"item_input":0,"current_nodes":["s"]}]},
                 {"id":"unused","transitional_item":"minecraft:diamond","recipe":{"type":"create:pressing"}},
                 {"id":"bypassed","recipe":{"type":"create:cutting"}}],
                 "edges":[{"from":"unused","to":"target"},{"from":"s","to":"bypassed"}],"outputs":[]}
                """.formatted(route)));
            assertEquals(java.util.List.of("minecraft:gold_nugget"),PreviewRecipeData.carriedInputs(graph,graph.nodes().get("target")));
            assertEquals(java.util.List.of(),PreviewRecipeData.carriedInputs(graph,graph.nodes().get("bypassed")));
        }
    }
    @Test void prependsWorkpieceWithoutDroppingEitherAuxiliaryItem() {
        var source = json("""
            {
              "type": "createcraftedbeginning:reactor_kettle",
              "ingredients": [{"tag": "c:gems/quartz"}, {"tag": "c:nuggets/iron"}],
              "results": [{"id": "minecraft:gravel", "chance": 0.25}],
              "temperature_matching": "compatible"
            }
            """);

        var materialized = PreviewRecipeData.materialize(source, "minecraft:cobblestone", "create:andesite_alloy");

        assertEquals(json("""
            {
              "type": "createcraftedbeginning:reactor_kettle",
              "ingredients": [
                {"item": "minecraft:cobblestone"},
                {"tag": "c:gems/quartz"},
                {"tag": "c:nuggets/iron"}
              ],
              "results": [{"id": "create:andesite_alloy"}, {"id": "minecraft:gravel", "chance": 0.25}],
              "temperature_matching": "compatible"
            }
            """), materialized);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"})
    void cookingUsesSingularIngredientAndResult(String type) {
        var source = json("""
            {
              "ingredient": {"item": "minecraft:potato"},
              "result": {"id": "minecraft:baked_potato"},
              "cookingtime": 100,
              "experience": 0.35
            }
            """);
        source.addProperty("type", type);

        var materialized = PreviewRecipeData.materialize(source, "minecraft:raw_iron", "minecraft:iron_ingot");

        assertEquals(json("{\"item\":\"minecraft:raw_iron\"}"), materialized.getAsJsonObject("ingredient"));
        assertEquals(json("{\"id\":\"minecraft:iron_ingot\"}"), materialized.getAsJsonObject("result"));
        assertFalse(materialized.has("ingredients"));
        assertFalse(materialized.has("results"));
        assertEquals(type, materialized.get("type").getAsString());
        assertEquals(100, materialized.get("cookingtime").getAsInt());
        assertEquals(0.35, materialized.get("experience").getAsDouble());
    }

    @Test void nativeRecipeReferencesMustBeResolvedRatherThanMaterialized() {
        var source = json("""
            {
              "type": "createcraftedbeginning:reactor_kettle",
              "recipe_id": "createcraftedbeginning:reactor_kettle/andesite_alloy"
            }
            """);
        var original = source.deepCopy();

        assertThrows(IllegalArgumentException.class,
            () -> PreviewRecipeData.materialize(source, "minecraft:cobblestone", "create:andesite_alloy"));
        assertEquals(original, source);
    }

    @Test void mixedFluidAndGasInputsAndOutputsRemainNativeData() {
        var source = json("""
            {
              "type": "createcraftedbeginning:reactor_kettle",
              "ingredients": [
                {"tag": "c:flours/wheat"},
                {"type": "neoforge:tag", "amount": 1000, "tag": "c:water"},
                {"type": "neoforge:tag", "amount": 250, "tag": "c:lava"}
              ],
              "results": [{"id": "minecraft:water", "amount": 100}],
              "gas_ingredients": [
                {"ingredient": {"gas": "createcraftedbeginning:ultrawarm_air"}, "amount": 5000, "minimum_pressure": 1000000},
                {"ingredient": {"gas": "createcraftedbeginning:natural_air"}, "amount": 1000, "maximum_pressure": 2000000}
              ],
              "gas_results": [{"id": "createcraftedbeginning:ethereal_air", "amount": 500}],
              "processing_time": 50,
              "temperature": "heated",
              "temperature_matching": "compatible",
              "custom_data": {"nested": ["preserve", {"value": 7}]}
            }
            """);

        var materialized = PreviewRecipeData.materialize(source, "create:cinder_flour", "create:cinder_flour");

        assertEquals(4, materialized.getAsJsonArray("ingredients").size());
        for (int i = 0; i < source.getAsJsonArray("ingredients").size(); i++)
            assertEquals(source.getAsJsonArray("ingredients").get(i), materialized.getAsJsonArray("ingredients").get(i + 1));
        assertEquals(2, materialized.getAsJsonArray("results").size());
        assertEquals(source.getAsJsonArray("results").get(0), materialized.getAsJsonArray("results").get(1));
        for (String field : new String[] {"gas_ingredients", "gas_results", "processing_time", "temperature", "temperature_matching", "custom_data"})
            assertEquals(source.get(field), materialized.get(field), field);
    }

    @Test void materializedNestedValuesDoNotAliasSource() {
        var source = json("""
            {
              "type": "createcraftedbeginning:reactor_kettle",
              "ingredients": [{"tag": "c:flours/wheat"}],
              "results": [{"id": "minecraft:gravel", "chance": 0.25}],
              "gas_ingredients": [{"ingredient": {"gas": "createcraftedbeginning:ultrawarm_air"}, "amount": 5000}],
              "gas_results": [{"id": "createcraftedbeginning:natural_air", "amount": 500}],
              "custom_data": {"values": [1, 2]}
            }
            """);
        var original = source.deepCopy();

        var materialized = PreviewRecipeData.materialize(source, "create:cinder_flour", "create:cinder_flour");
        assertEquals(original, source);
        materialized.getAsJsonArray("ingredients").get(1).getAsJsonObject().addProperty("tag", "c:gems/quartz");
        materialized.getAsJsonArray("results").get(1).getAsJsonObject().addProperty("chance", 1.0);
        materialized.getAsJsonArray("gas_ingredients").get(0).getAsJsonObject()
            .getAsJsonObject("ingredient").addProperty("gas", "createcraftedbeginning:natural_air");
        materialized.getAsJsonArray("gas_results").get(0).getAsJsonObject().addProperty("amount", 1000);
        materialized.getAsJsonObject("custom_data").getAsJsonArray("values").add(3);

        assertEquals(original, source);
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
