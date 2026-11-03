package dev.graphassembly.integration;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstReleaseExecutionPolicyTest {
    @Test void productionConfigurationDoesNotRegisterAddonMixins() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/graph_assembly.mixins.json"))).getAsJsonObject();
        for (var side : new String[] {"mixins", "client", "server"}) {
            if (!config.has(side)) continue;
            for (var mixin : config.getAsJsonArray(side))
                assertFalse(mixin.getAsString().startsWith("compat."), mixin.toString());
        }
    }
}
