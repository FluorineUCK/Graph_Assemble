package dev.graphassembly.filter;

import com.google.gson.*;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Capacity contract shared by the draft, wire, component and saved data; never truncates. */
public record FilterConfiguration(String graph, List<String> targets, Set<String> inverted, Mode mode) {
    public static final int MAX_TARGETS = 256, MAX_ID_LENGTH = 1024, MAX_JSON_BYTES = 16384;
    public enum Mode {
        ANY, ALL, NONE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public FilterConfiguration(String graph, List<String> targets) { this(graph, targets, Set.of(), Mode.ANY); }
    public enum Problem { INVALID, TOO_MANY_TARGETS, ID_TOO_LONG, TOO_LARGE }
    public static final class Invalid extends IllegalArgumentException {
        private final Problem problem;
        public Invalid(Problem problem) { super("graph_assembly.filter.capacity." + problem.name().toLowerCase(Locale.ROOT)); this.problem = problem; }
        public Problem problem() { return problem; }
    }
    public FilterConfiguration {
        if (graph == null || targets == null || inverted == null || mode == null
            || targets.stream().anyMatch(Objects::isNull) || inverted.stream().anyMatch(Objects::isNull)) throw new Invalid(Problem.INVALID);
        targets = List.copyOf(new LinkedHashSet<>(targets));
        inverted = Set.copyOf(inverted);
        if (!targets.containsAll(inverted)) throw new Invalid(Problem.INVALID);
        if (targets.size() > MAX_TARGETS) throw new Invalid(Problem.TOO_MANY_TARGETS);
        if (graph.length() > MAX_ID_LENGTH || targets.stream().anyMatch(s -> s.length() > MAX_ID_LENGTH)) throw new Invalid(Problem.ID_TOO_LONG);
        if (targets.stream().anyMatch(String::isEmpty) || graph.isEmpty() && !targets.isEmpty()) throw new Invalid(Problem.INVALID);
        checkSize(encode(graph, targets, inverted, mode));
    }
    private static void checkSize(String json) {
        if (json == null) throw new Invalid(Problem.INVALID);
        if (json.length() > MAX_JSON_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) throw new Invalid(Problem.TOO_LARGE);
    }
    private static String encode(String graph, List<String> targets, Set<String> inverted, Mode mode) {
        var json = new JsonObject(); json.addProperty("graph", graph);
        var array = new JsonArray(); targets.forEach(array::add); json.add("targets", array);
        if (!inverted.isEmpty()) {
            var negated = new JsonArray(); targets.stream().filter(inverted::contains).forEach(negated::add);
            json.add("inverted", negated);
        }
        if (mode != Mode.ANY) json.addProperty("mode", mode.id());
        return json.toString();
    }
    public String toJson() { return encode(graph, targets, inverted, mode); }
    /** Predicate combination only; the runtime must first establish the graph/input domain. */
    public boolean matches(Set<String> available) {
        return matches(targets, inverted, mode, available);
    }
    static boolean matches(List<String> targets, Set<String> inverted, Mode mode, Set<String> available) {
        if (targets.isEmpty()) return false;
        java.util.function.Predicate<String> test = id -> available.contains(id) != inverted.contains(id);
        return switch (mode) {
            case ANY -> targets.stream().anyMatch(test);
            case ALL -> targets.stream().allMatch(test);
            case NONE -> targets.stream().noneMatch(test);
        };
    }
    public static FilterConfiguration parse(String json) {
        checkSize(json);
        try {
            PerformanceMetrics.filterParse();
            var obj = JsonParser.parseString(json).getAsJsonObject();
            if (!Set.of("graph", "targets", "inverted", "mode").containsAll(obj.keySet())
                || !obj.has("graph") || !obj.has("targets")) throw new Invalid(Problem.INVALID);
            var targets = new ArrayList<String>();
            for (var value : obj.getAsJsonArray("targets")) targets.add(string(value));
            var inverted = new LinkedHashSet<String>();
            if (obj.has("inverted")) for (var value : obj.getAsJsonArray("inverted")) inverted.add(string(value));
            Mode mode = obj.has("mode") ? Mode.valueOf(string(obj.get("mode")).toUpperCase(Locale.ROOT)) : Mode.ANY;
            return new FilterConfiguration(string(obj.get("graph")), targets, inverted, mode);
        } catch (Invalid e) { throw e; }
        catch (RuntimeException e) { throw new Invalid(Problem.INVALID); }
    }
    private static String string(JsonElement value) {
        if (!(value instanceof JsonPrimitive p) || !p.isString()) throw new Invalid(Problem.INVALID);
        return value.getAsString();
    }
    public static Problem problem(String graph, List<String> targets) {
        return problem(graph, targets, Set.of(), Mode.ANY);
    }
    public static Problem problem(String graph, List<String> targets, Set<String> inverted, Mode mode) {
        try { new FilterConfiguration(graph, targets, inverted, mode); return null; }
        catch (Invalid e) { return e.problem(); }
    }
}
