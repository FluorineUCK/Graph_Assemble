package dev.graphassembly.codec;

import dev.graphassembly.core.progress.Progress;

import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.route.RouteState;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProgressCodecTest {
    private static Progress snapshot() {
        return new Progress("test:graph", "s", "a", new MarkAccounting.State(Map.of("worked", 2), Map.of("a", 2)), List.of("s", "a"), new RouteState("f", null), List.of("s", "a"));
    }

    @ParameterizedTest
    @CsvSource({"-1", "1.5", "2147483648", "\"2\"", "null"})
    void decodingRejectsMalformedCountsRatherThanSilentlyChangingState(String count) {
        JsonObject json = ProgressCodec.toJson(snapshot());
        json.getAsJsonObject("marks").add("worked", JsonParser.parseString(count));
        assertThrows(IllegalArgumentException.class, () -> ProgressCodec.fromJson(json));
    }

    @Test
    void snapshotsAndExportedJsonDoNotShareMutableState() {
        Map<String, Integer> marks = new java.util.HashMap<>(Map.of("worked", 2));
        List<String> checkpoints = new java.util.ArrayList<>(List.of("s", "a"));
        Progress state = new Progress("test:graph", "s", "a", new MarkAccounting.State(marks, Map.of("a", 1)), checkpoints, new RouteState(null, null), List.of("s", "a"));
        marks.put("worked", 99);
        checkpoints.clear();
        ProgressCodec.toJson(state).getAsJsonObject("marks").addProperty("worked", 99);
        assertEquals(Map.of("worked", 2), state.marks().values());
        assertEquals(List.of("s", "a"), state.checkpoints());
        assertThrows(UnsupportedOperationException.class, () -> state.marks().values().clear());
    }

    @Test
    void streamedComponentStringMatchesGsonTreeForLargeAndEscapedHistories() {
        String unusual = "<>&='\"\\\b\f\n\r\t\u0000\u001f\u2028\u2029🧭";
        var checkpoints = new java.util.ArrayList<String>();
        for (int i = 0; i < 100_000; i++) checkpoints.add(i % 2 == 0 ? unusual : "duplicate");
        var marks = new java.util.LinkedHashMap<String, Integer>();
        marks.put(unusual, 1);
        marks.put("max", Integer.MAX_VALUE);
        var grants = new java.util.LinkedHashMap<String, Integer>();
        grants.put("duplicate", 0);
        for (String locked : new String[]{null, unusual}) {
            Progress progress = new Progress(unusual, "entry", unusual, new MarkAccounting.State(marks, grants), checkpoints, new RouteState(locked, null), List.of(unusual, "duplicate", unusual));
            assertEquals(ProgressCodec.toJson(progress).toString(), ProgressCodec.toJsonString(progress));
            assertEquals(progress, ProgressCodec.fromJson(JsonParser.parseString(ProgressCodec.toJsonString(progress)).getAsJsonObject()));
        }
        Progress empty = new Progress("", "", "", new MarkAccounting.State(Map.of(), Map.of()), List.of(), new RouteState(null, null), List.of());
        assertEquals(ProgressCodec.toJson(empty).toString(), ProgressCodec.toJsonString(empty));
    }
    @Test void malformedFieldsKeepOriginalDiagnosticPriority() {
        var json=ProgressCodec.toJson(snapshot());
        json.add("checkpoints",new com.google.gson.JsonObject());
        json.add("mark_grant_counts",new com.google.gson.JsonArray());
        json.add("history",new com.google.gson.JsonObject());
        json.add("pending_route",new com.google.gson.JsonArray());
        assertTrue(assertThrows(IllegalArgumentException.class,()->ProgressCodec.fromJson(json)).getMessage().contains("progress.checkpoints"));
        json.add("checkpoints",new com.google.gson.JsonArray());
        assertTrue(assertThrows(IllegalArgumentException.class,()->ProgressCodec.fromJson(json)).getMessage().contains("progress.mark_grant_counts"));
        json.add("mark_grant_counts",new com.google.gson.JsonObject());
        assertTrue(assertThrows(IllegalArgumentException.class,()->ProgressCodec.fromJson(json)).getMessage().contains("progress.history"));
        json.add("history",new com.google.gson.JsonArray());
        assertTrue(assertThrows(IllegalArgumentException.class,()->ProgressCodec.fromJson(json)).getMessage().contains("progress.pending_route"));
    }
}
