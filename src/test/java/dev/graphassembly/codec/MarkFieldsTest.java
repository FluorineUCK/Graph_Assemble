package dev.graphassembly.codec;

import com.google.gson.JsonParser;
import dev.graphassembly.compat.kubejs.GraphBuilder;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.route.RouteState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkFieldsTest {
    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "2147483648", "null", "\"1\"", "true"})
    void markAmountsLimitsAndGrantCountsRejectInvalidNumbers(String value) {
        for (String field : List.of("mark_amounts", "max_mark_applications")) {
            var options = JsonParser.parseString("{\"start\":true}").getAsJsonObject();
            options.add(field, JsonParser.parseString(field.equals("mark_amounts")
                ? "{\"quality\":" + value + "}" : value));
            var builder = new GraphBuilder("test:work")
                .ingredient(JsonParser.parseString("{\"item\":\"test:raw\"}"))
                .node("press", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(), options);
            var error = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(error.getMessage().contains("nodes[0]." + field), error.getMessage());
        }
        var state = JsonParser.parseString("""
            {"graph_id":"test:marks","entry":"press","current":"press",
             "marks":{},"checkpoints":[],"mark_grant_counts":{},"history":[]}
            """).getAsJsonObject();
        state.getAsJsonObject("mark_grant_counts").add("press", JsonParser.parseString(value));
        var error = assertThrows(IllegalArgumentException.class, () -> ProgressCodec.fromJson(state));
        assertTrue(error.getMessage().contains("progress.counts.press"), error.getMessage());
    }

    @Test
    void savedProgressRequiresRecognizedGrantCountFields() {
        var json = JsonParser.parseString("""
            {"graph_id":"test:marks","entry":"press","current":"press",
             "marks":{},"checkpoints":[],"mark_grant_counts":{"press":1},"history":[]}
            """).getAsJsonObject();
        json.add("reward_counts", JsonParser.parseString("{\"press\":4}"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProgressCodec.fromJson(json))
            .getMessage().contains("progress.reward_counts: unknown field"));
        json.remove("reward_counts");
        json.remove("mark_grant_counts");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProgressCodec.fromJson(json))
            .getMessage().contains("progress.mark_grant_counts"));
    }

    @Test
    void unsupportedNodeOptionsCannotSilentlyDisableMarkGrants() {
        for (String field : List.of("rewards", "reward_limit", "mark_amount")) {
            var options = JsonParser.parseString("{\"start\":true}").getAsJsonObject();
            options.add(field, JsonParser.parseString("{}"));
            var builder = new GraphBuilder("test:work")
                .ingredient(JsonParser.parseString("{\"item\":\"test:raw\"}"))
                .node("press", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(), options);
            var error = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(error.getMessage().contains("nodes[0]." + field + ": unknown field"));
        }
    }

    @Test
    void savedProgressUsesMarkGrantCountsForBothWriters() {
        var state = new Progress("test:marks", "press", "press",
            new MarkAccounting.State(Map.of("quality", 2), Map.of("press", 1)),
            List.of("press"), new RouteState(null, null), List.of("press"));
        var expected = JsonParser.parseString("""
            {"graph_id":"test:marks","entry":"press","current":"press",
             "marks":{"quality":2},"checkpoints":["press"],
             "mark_grant_counts":{"press":1},"history":["press"]}
            """).getAsJsonObject();

        assertEquals(expected, ProgressCodec.toJson(state));
        assertEquals(expected, JsonParser.parseString(ProgressCodec.toJsonString(state)));
        assertEquals(state, ProgressCodec.fromJson(expected));
    }

    @Test
    void scriptNodeOptionsDefineMarkAmountsAndApplicationLimit() {
        var builder = new GraphBuilder("test:work")
            .ingredient(JsonParser.parseString("{\"item\":\"test:raw\"}"))
            .node("press", JsonParser.parseString("{\"type\":\"create:pressing\"}").getAsJsonObject(),
                JsonParser.parseString("""
                    {"start":true,"mark_amounts":{"quality":2,"trace":0},"max_mark_applications":1}
                    """).getAsJsonObject());

        var authored = builder.build();
        var effects = GraphCodec.parse("test:marks", authored).nodes().get("press").effects();
        assertEquals(Map.of("quality", 2, "trace", 0), effects.markAmounts());
        assertEquals(1, effects.maxMarkApplications());
        assertEquals(2, authored.getAsJsonArray("nodes").get(0).getAsJsonObject()
            .getAsJsonObject("mark_amounts").get("quality").getAsInt());
    }
}
