package dev.graphassembly.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A workpiece snapshot. The engine never mutates an existing snapshot. */
public record Progress(String graphId, String entry, String current, Map<String, Integer> marks,
                       List<String> checkpoints, Map<String, Integer> rewardCounts,
                       String lockedNext, List<String> history, PendingRoute pendingRoute) {
    public Progress(String graphId, String entry, String current, Map<String,Integer> marks,
                    List<String> checkpoints, Map<String,Integer> rewardCounts, String lockedNext, List<String> history) {
        this(graphId,entry,current,marks,checkpoints,rewardCounts,lockedNext,history,null);
    }
    public Progress {
        marks = Map.copyOf(marks);
        checkpoints = List.copyOf(checkpoints);
        rewardCounts = Map.copyOf(rewardCounts);
        history = List.copyOf(history);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("graph_id", graphId);
        json.addProperty("entry", entry);
        json.addProperty("current", current);
        json.add("marks", counts(marks));
        json.add("checkpoints", strings(checkpoints));
        json.add("reward_counts", counts(rewardCounts));
        if (lockedNext != null) json.addProperty("locked_next", lockedNext);
        json.add("history", strings(history));
        if (pendingRoute != null) json.add("pending_route", pendingRoute.toJson());
        return json;
    }

    /** Existing component JSON without allocating a Gson element for every checkpoint. */
    public String toJsonString() {
        StringWriter output = new StringWriter();
        JsonWriter json = new JsonWriter(output);
        json.setHtmlSafe(false); // JsonElement.toString() does not use Gson's HTML-safe mode.
        json.setSerializeNulls(true);
        try {
            json.beginObject();
            json.name("graph_id").value(graphId);
            json.name("entry").value(entry);
            json.name("current").value(current);
            json.name("marks"); writeCounts(json, marks);
            json.name("checkpoints"); writeStrings(json, checkpoints);
            json.name("reward_counts"); writeCounts(json, rewardCounts);
            if (lockedNext != null) json.name("locked_next").value(lockedNext);
            json.name("history"); writeStrings(json, history);
            if (pendingRoute != null) { json.name("pending_route"); pendingRoute.write(json); }
            json.endObject();
            return output.toString();
        } catch (IOException impossible) {
            throw new AssertionError("StringWriter failed", impossible);
        }
    }

    private static void writeCounts(JsonWriter json, Map<String, Integer> values) throws IOException {
        json.beginObject();
        for (var entry : values.entrySet()) json.name(entry.getKey()).value(entry.getValue());
        json.endObject();
    }

    private static void writeStrings(JsonWriter json, List<String> values) throws IOException {
        json.beginArray();
        for (String value : values) json.value(value);
        json.endArray();
    }

    public static Progress fromJson(JsonObject json) {
        GraphCodec.object(json, "progress");
        return new Progress(GraphCodec.requiredText(json, "graph_id", "progress"), GraphCodec.requiredText(json, "entry", "progress"),
                GraphCodec.requiredText(json, "current", "progress"), counts(GraphCodec.object(json.get("marks"), "progress.marks")),
                strings(GraphCodec.array(json, "checkpoints", "progress", true)), counts(GraphCodec.object(json.get("reward_counts"), "progress.reward_counts")),
                json.has("locked_next") && !json.get("locked_next").isJsonNull()
                        ? GraphCodec.requiredText(json, "locked_next", "progress") : null,
                strings(GraphCodec.array(json, "history", "progress", true)),
                json.has("pending_route") ? PendingRoute.parse(GraphCodec.object(json.get("pending_route"),"progress.pending_route")) : null);
    }

    private static JsonObject counts(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private static Map<String, Integer> counts(JsonObject json) {
        Map<String, Integer> values = new LinkedHashMap<>();
        json.entrySet().forEach(e -> values.put(e.getKey(), GraphCodec.integer(e.getValue(), "progress.counts." + e.getKey(), 0)));
        return values;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray json = new JsonArray();
        values.forEach(json::add);
        return json;
    }

    private static List<String> strings(JsonArray json) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : json) values.add(GraphCodec.text(value, "progress.nodes"));
        return values;
    }
}
