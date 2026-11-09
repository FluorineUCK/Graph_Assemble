package dev.graphassembly.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.graphassembly.client.view.SettlementDescription;
import dev.graphassembly.codec.GraphCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanguageResourcesTest {
    private static final List<String> LOCALES = List.of("zh_cn", "en_us");
    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/assets/graph_assembly/lang", locale + ".json"))).getAsJsonObject();
    }

    @Test void filterUsesCreateDescriptionKeysAndJoinPartnersHaveReadableLabels() throws Exception {
        for (String locale : LOCALES) {
            var lang = language(locale);
            for (String key : List.of("item.graph_assembly.graph_filter.tooltip.summary",
                    "item.graph_assembly.graph_filter.tooltip.condition1", "item.graph_assembly.graph_filter.tooltip.behaviour1",
                    "item.graph_assembly.graph_filter.tooltip.condition2", "item.graph_assembly.graph_filter.tooltip.behaviour2",
                    "graph_assembly.join.primary", "graph_assembly.join.auxiliary", "graph_assembly.join.after",
                    "graph_assembly.join.partner.primary", "graph_assembly.join.partner.auxiliary", "graph_assembly.view.reset")) {
                assertTrue(lang.has(key), locale + ": " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test void retiredSettlementPageHasNoNavigationOrModeSummaryCopy() throws Exception {
        for (String locale : LOCALES) {
            var language = language(locale);
            for (String suffix : List.of("open", "back", "title", "input", "success", "scrap",
                    "success_tooltip", "scrap_tooltip", "slot_hint", "previous", "next", "page", "scroll",
                    "overview", "strict", "byproducts", "navigation", "entry.any", "marks.empty", "marks",
                    "checkpoints.any", "failure.finish", "failure.trigger", "requirements.defaults",
                    "marks.mode_disabled", "checkpoints.order_disabled", "marks.disabled", "marks.partial.empty",
                    "checkpoints.disabled", "checkpoints.subsequence.empty", "checkpoints.unordered.empty",
                    "requirements.effective", "mode.disabled", "mode.exact", "mode.partial", "mode.subsequence",
                    "mode.unordered", "tree.semantics", "tree.path")) {
                assertFalse(language.has("graph_assembly.settlement." + suffix), locale + ": " + suffix);
            }
        }
    }

    @Test void localesKeepMatchingKeysAndFormatArguments() throws Exception {
        var chinese = language("zh_cn");
        var english = language("en_us");
        assertEquals(chinese.keySet(), english.keySet());
        var argument = java.util.regex.Pattern.compile("%(?:\\d+\\$)?[sd]");
        for (String key : chinese.keySet()) {
            assertEquals(argument.matcher(chinese.get(key).getAsString()).results().count(),
                argument.matcher(english.get(key).getAsString()).results().count(), key);
        }
    }

    @Test void exampleInfoAndDynamicDescriptionFamiliesRemainTranslated() throws Exception {
        for (String locale : LOCALES) {
            var language = language(locale);
            for (String suffix : List.of("marks.exact", "marks.partial", "marks.exact.empty",
                    "checkpoints.exact", "checkpoints.subsequence", "checkpoints.unordered", "checkpoints.exact.empty",
                    "tree.all", "tree.weighted", "tree.all.plain", "tree.weighted.plain",
                    "actions.clear_mark", "actions.subtract_mark", "actions.clear_checkpoint", "actions.pop_checkpoint",
                    "actions.goto_node", "actions.enter_edge")) {
                assertTrue(language.has("graph_assembly.settlement." + suffix), locale + ": " + suffix);
            }
            try (var examples = Files.list(Path.of("examples/datapack"))) {
                for (var file : examples.filter(path -> path.toString().endsWith(".json")).toList()) {
                    var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    // Native sequences use Create's own description/translation model, not graph settlement text.
                    if (json.has("type") && json.get("type").getAsString().equals("create:sequenced_assembly")) continue;
                    var graph = GraphCodec.parse("test:language", json);
                    for (var line : SettlementDescription.from(graph).paragraphs()) {
                        String key = "graph_assembly.settlement." + line.key();
                        assertTrue(language.has(key), locale + ": " + key);
                        assertDoesNotThrow(() -> String.format(Locale.ROOT, language.get(key).getAsString(), line.arguments().toArray()), key);
                    }
                }
            }
        }
    }
}
