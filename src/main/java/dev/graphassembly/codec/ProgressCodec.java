package dev.graphassembly.codec;

import static dev.graphassembly.codec.JsonValidation.object;
import static dev.graphassembly.codec.JsonValidation.array;
import static dev.graphassembly.codec.JsonValidation.requiredText;
import static dev.graphassembly.codec.JsonValidation.integer;
import static dev.graphassembly.codec.JsonValidation.text;
import static dev.graphassembly.codec.JsonValidation.fields;

import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.route.PendingRoute;
import dev.graphassembly.core.route.RouteState;

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

/** Sole mapping between immutable progress snapshots and the current component JSON format. */
public final class ProgressCodec {
    private ProgressCodec() {}
    public static JsonObject toJson(Progress progress) {
        JsonObject json = new JsonObject();
        json.addProperty("graph_id", progress.graphId());
        json.addProperty("entry", progress.entry());
        json.addProperty("current", progress.current());
        json.add("marks", counts(progress.marks().values()));
        json.add("checkpoints", strings(progress.checkpoints()));
        json.add("mark_grant_counts", counts(progress.marks().grantCounts()));
        if (progress.routing().lockedNext() != null) json.addProperty("locked_next", progress.routing().lockedNext());
        json.add("history", strings(progress.recentHistory()));
        if (progress.routing().pendingRoute() != null) json.add("pending_route", routeJson(progress.routing().pendingRoute()));
        return json;
    }

    /** Existing component JSON without allocating a Gson element for every checkpoint. */
    public static String toJsonString(Progress progress) {
        StringWriter output = new StringWriter();
        JsonWriter json = new JsonWriter(output);
        json.setHtmlSafe(false); // JsonElement.toString() does not use Gson's HTML-safe mode.
        json.setSerializeNulls(true);
        try {
            json.beginObject();
            json.name("graph_id").value(progress.graphId());
            json.name("entry").value(progress.entry());
            json.name("current").value(progress.current());
            json.name("marks"); writeCounts(json, progress.marks().values());
            json.name("checkpoints"); writeStrings(json, progress.checkpoints());
            json.name("mark_grant_counts"); writeCounts(json, progress.marks().grantCounts());
            if (progress.routing().lockedNext() != null) json.name("locked_next").value(progress.routing().lockedNext());
            json.name("history"); writeStrings(json, progress.recentHistory());
            if (progress.routing().pendingRoute() != null) { json.name("pending_route"); writeRoute(json, progress.routing().pendingRoute()); }
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
        object(json, "progress");
        fields(json,"progress","graph_id","entry","current","marks","checkpoints","mark_grant_counts",
            "locked_next","history","pending_route");
        String graphId=requiredText(json,"graph_id","progress");
        String entry=requiredText(json,"entry","progress");
        String current=requiredText(json,"current","progress");
        var values=counts(object(json.get("marks"),"progress.marks"));
        var checkpoints=strings(array(json,"checkpoints","progress",true));
        var grants=counts(object(json.get("mark_grant_counts"),"progress.mark_grant_counts"));
        String locked=json.has("locked_next") && !json.get("locked_next").isJsonNull()
            ? requiredText(json,"locked_next","progress") : null;
        var history=strings(array(json,"history","progress",true));
        var pending=json.has("pending_route") ? parseRoute(object(json.get("pending_route"),"progress.pending_route")) : null;
        return new Progress(graphId,entry,current,new MarkAccounting.State(values,grants),checkpoints,new RouteState(locked,pending),history);
    }

    private static JsonObject counts(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private static Map<String, Integer> counts(JsonObject json) {
        Map<String, Integer> values = new LinkedHashMap<>();
        json.entrySet().forEach(e -> values.put(e.getKey(), integer(e.getValue(), "progress.counts." + e.getKey(), 0)));
        return values;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray json = new JsonArray();
        values.forEach(json::add);
        return json;
    }

    private static List<String> strings(JsonArray json) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : json) values.add(text(value, "progress.nodes"));
        return values;
    }
    private static JsonObject routeJson(PendingRoute route) {
        var json=new JsonObject(); json.addProperty("node",route.node());
        if(route.from()!=null) json.addProperty("from",route.from()); return json;
    }
    private static void writeRoute(JsonWriter json, PendingRoute route) throws IOException {
        json.beginObject().name("node").value(route.node());
        if(route.from()!=null) json.name("from").value(route.from()); json.endObject();
    }
    private static PendingRoute parseRoute(JsonObject json) {
        fields(json,"progress.pending_route","node","from");
        return new PendingRoute(requiredText(json,"node","progress.pending_route"),
            json.has("from") ? requiredText(json,"from","progress.pending_route") : null);
    }
}
