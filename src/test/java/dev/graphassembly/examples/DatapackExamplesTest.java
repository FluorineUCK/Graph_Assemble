package dev.graphassembly.examples;

import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DatapackExamplesTest {
    @TestFactory List<DynamicTest> documentedExamplesUseTheCurrentCodec() {
        return List.of("branching", "item-first", "progress-actions").stream().map(name ->
            DynamicTest.dynamicTest(name, () -> assertDoesNotThrow(() -> GraphCodec.parse("test:" + name,
                JsonParser.parseString(Files.readString(Path.of("examples", "datapack", name + ".json"))).getAsJsonObject()))))
            .toList();
    }
}
