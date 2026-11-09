package dev.graphassembly.core.io;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OutputItemTest {
    @Test void determinedOutputDefensivelyCopiesNestedComponents() {
        var data = new JsonObject();
        data.addProperty("value", "original");
        var components = new JsonObject();
        components.add("minecraft:custom_data", data);
        var output = new OutputItem("test:result", 2, components);

        data.addProperty("value", "input changed");
        output.components().getAsJsonObject("minecraft:custom_data").addProperty("value", "accessor changed");

        assertEquals("original", output.components().getAsJsonObject("minecraft:custom_data").get("value").getAsString());
        assertEquals("test:result", output.item());
        assertEquals(2, output.count());
    }

    @Test void rolledOutputRetainsDeterminedCountAndComponentsWithoutProbabilityConfiguration() {
        var components = new JsonObject();
        components.addProperty("minecraft:custom_name", "result");
        var configured = new WeightedOutput("test:result", 2, 7, components, 0.5);
        double[] draws = {0, 0.5, 0.6};
        var index = new java.util.concurrent.atomic.AtomicInteger();

        List<OutputItem> outputs = OutputTrees.roll(List.of(configured), () -> draws[index.getAndIncrement()]);

        assertEquals(List.of(new OutputItem("test:result", 1, components)), outputs);
        assertEquals(draws.length, index.get());
        assertThrows(UnsupportedOperationException.class, outputs::clear);
    }
}
