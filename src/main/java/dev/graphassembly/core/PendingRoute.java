package dev.graphassembly.core;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** A selected next operation, not a fictitious completed node. from is present only for an explicit edge. */
public record PendingRoute(String node, String from) {
    public PendingRoute {
        if (node == null || node.isBlank() || from != null && from.isBlank())
            throw new IllegalArgumentException("Invalid pending route");
    }
    public JsonObject toJson() {
        var json=new JsonObject();json.addProperty("node",node);
        if(from!=null) json.addProperty("from",from);
        return json;
    }
    void write(JsonWriter json) throws IOException {
        json.beginObject().name("node").value(node);
        if(from!=null) json.name("from").value(from);
        json.endObject();
    }
    static PendingRoute parse(JsonObject json) {
        GraphCodec.fields(json,"progress.pending_route","node","from");
        return new PendingRoute(GraphCodec.requiredText(json,"node","progress.pending_route"),
            json.has("from") ? GraphCodec.requiredText(json,"from","progress.pending_route") : null);
    }
}
