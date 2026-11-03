package dev.graphassembly.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {
    private static Progress snapshot() {
        return new Progress("test:graph", "s", "a", Map.of("worked", 2), List.of("s", "a"), Map.of("a", 2), "f", List.of("s", "a"));
    }

    @ParameterizedTest
    @CsvSource({"-1", "1.5", "2147483648", "\"2\"", "null"})
    void decodingRejectsMalformedCountsRatherThanSilentlyChangingState(String count) {
        JsonObject json = snapshot().toJson();
        json.getAsJsonObject("marks").add("worked", JsonParser.parseString(count));
        assertThrows(IllegalArgumentException.class, () -> Progress.fromJson(json));
    }

    @Test
    void snapshotsAndExportedJsonDoNotShareMutableState() {
        Map<String, Integer> marks = new java.util.HashMap<>(Map.of("worked", 2));
        List<String> checkpoints = new java.util.ArrayList<>(List.of("s", "a"));
        Progress state = new Progress("test:graph", "s", "a", marks, checkpoints, Map.of("a", 1), null, List.of("s", "a"));
        marks.put("worked", 99);
        checkpoints.clear();
        state.toJson().getAsJsonObject("marks").addProperty("worked", 99);
        assertEquals(Map.of("worked", 2), state.marks());
        assertEquals(List.of("s", "a"), state.checkpoints());
        assertThrows(UnsupportedOperationException.class, () -> state.marks().clear());
    }

    @Test
    void streamedComponentStringExactlyMatchesLegacyGsonTreeForLargeAndEscapedHistories() {
        String unusual = "<>&='\"\\\b\f\n\r\t\u0000\u001f\u2028\u2029🧭";
        var checkpoints = new java.util.ArrayList<String>();
        for (int i = 0; i < 100_000; i++) checkpoints.add(i % 2 == 0 ? unusual : "duplicate");
        var marks = new java.util.LinkedHashMap<String, Integer>();
        marks.put(unusual, 1);
        marks.put("max", Integer.MAX_VALUE);
        var rewards = new java.util.LinkedHashMap<String, Integer>();
        rewards.put("duplicate", 0);
        for (String locked : new String[]{null, unusual}) {
            Progress progress = new Progress(unusual, "entry", unusual, marks, checkpoints, rewards,
                    locked, List.of(unusual, "duplicate", unusual));
            assertEquals(progress.toJson().toString(), progress.toJsonString());
            assertEquals(progress, Progress.fromJson(JsonParser.parseString(progress.toJsonString()).getAsJsonObject()));
        }
        Progress empty = new Progress("", "", "", Map.of(), List.of(), Map.of(), null, List.of());
        assertEquals(empty.toJson().toString(), empty.toJsonString());
    }
}
