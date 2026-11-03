package dev.graphassembly.client;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemPresentationResourcesTest {
    @Test void suspiciousInsertHasTheRequestedLocalizedTooltip() throws Exception {
        String key = "item.graph_assembly.infested_filter_insert.tooltip";
        for (var locale : java.util.Map.of("zh_cn", "真的很可疑...", "en_us", "really sus...").entrySet()) {
            var file = Path.of("src/main/resources/assets/graph_assembly/lang", locale.getKey() + ".json");
            var language = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertTrue(language.has(key), locale.getKey() + " is missing " + key);
            assertEquals(locale.getValue(), language.get(key).getAsString());
        }
    }
}
