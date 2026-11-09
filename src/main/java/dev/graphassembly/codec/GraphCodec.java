package dev.graphassembly.codec;

import dev.graphassembly.recipe.OperationSpec;
import dev.graphassembly.recipe.RecipeMode;

import static dev.graphassembly.codec.JsonValidation.fields;
import static dev.graphassembly.codec.JsonValidation.object;
import static dev.graphassembly.codec.JsonValidation.array;
import static dev.graphassembly.codec.JsonValidation.requiredText;
import static dev.graphassembly.codec.JsonValidation.text;
import static dev.graphassembly.codec.JsonValidation.integer;
import static dev.graphassembly.codec.JsonValidation.number;
import static dev.graphassembly.codec.JsonValidation.invalid;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;
import dev.graphassembly.core.condition.IntRange;
import dev.graphassembly.core.condition.mark.MarkRequirement;
import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.integration.vanilla.campfire.CampfireKind;
import dev.graphassembly.integration.vanilla.campfire.CampfireDefinition;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.GraphLayout;
import dev.graphassembly.core.definition.GraphTopology;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.WorkpieceInput;
import dev.graphassembly.core.io.OutputGroup;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.progress.ProgressMutation;
import dev.graphassembly.core.route.BranchMode;
import dev.graphassembly.core.route.RouteRedirect;
import dev.graphassembly.core.settlement.FinishMode;
import dev.graphassembly.core.settlement.OutputRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the shared data-pack and script JSON representation, with path-aware errors. */
public final class GraphCodec {
    private static final int MAX_EDGE_WAYPOINTS = 64;
    private static final double MAX_WAYPOINT_COORDINATE = 1_000_000;
    private GraphCodec() {}

    public static GraphDefinition<OperationSpec> parse(String id, JsonObject json) {
        try {
            if (id == null || id.isBlank()) throw invalid("id", "must be a nonempty string");
            if (json == null) throw invalid("graph", "must be an object");
            CampfireDefinition.validateGraphPlacement(json);
            rejectFluid(json.get("transitional_item"), "graph.transitional_item");
            if (json.has("transitional_fluid")) throw invalid("graph.transitional_fluid", "fluid workpieces are unsupported; use an item transitional_item");
            // Loader metadata remains opaque; KubeJS adds its marker before native recipe decoding.
            fields(json,"graph","type","ingredient","transitional_item","nodes","edges","outputs",
                "neoforge:conditions","_kubejs_changed_marker");
            String transitionalItem = requiredText(json, "transitional_item", "graph");
            JsonElement ingredient = ingredient(json, "graph");
            Map<String, NodeDefinition<OperationSpec>> nodes = new LinkedHashMap<>();
            Map<String,GraphLayout.NodeLayout> nodeLayouts = new LinkedHashMap<>();
            Map<GraphLayout.Endpoint,GraphLayout.EdgeLayout> edgeLayouts = new LinkedHashMap<>();
            Map<String,NodeActions> nodeActions = new LinkedHashMap<>();
            JsonArray nodeArray = array(json, "nodes", "graph", true);
            for (int i = 0; i < nodeArray.size(); i++) {
                String path = "nodes[" + i + "]";
                JsonObject n = object(nodeArray.get(i), path);
                String nodeId = requiredText(n, "id", path);
                rejectFluid(n.get("transitional_item"), path + ".transitional_item");
                if (n.has("transitional_fluid")) throw invalid(path + ".transitional_fluid", "fluid workpieces are unsupported; use an item transitional_item");
                JsonObject recipe = object(n.get("recipe"), path + ".recipe");
                String recipeType = requiredText(recipe, "type", path + ".recipe");
                CampfireDefinition.validateRecipePlacement(recipe,path);
                fields(n,path,"id","recipe","recipe_mode","start","finish","ingredient","transitional_item",
                    "mark_amounts","max_mark_applications","checkpoint","branch_mode","finish_mode","outputs","scrap",
                    "workpiece_inputs","progress_actions","position","sphere_position","campfire");
                var roles = new NodeDefinition.NodeRoles(bool(n,"start",path),bool(n,"finish",path));
                JsonElement overrideIngredient = ingredient(n,path);
                String carrier = optionalText(n,"transitional_item",path);
                var markAmounts = counts(n,"mark_amounts",path);
                Integer maxMarkApplications = n.has("max_mark_applications") ? integer(n.get("max_mark_applications"),path+".max_mark_applications",0) : null;
                boolean checkpoint = bool(n,"checkpoint",path);
                BranchMode branch = branchMode(n,path);
                FinishMode finish = finishMode(n,path);
                var outputs = rules(n,"outputs",path);
                var scrap = results(n,"scrap",path,false);
                var layout = new GraphLayout.NodeLayout(coordinates(n,"position",path,2),coordinates(n,"sphere_position",path,3));
                var inputs = new NodeDefinition.InputPolicy(overrideIngredient,workpieceInputs(n,path));
                var actions = actions(n,path);
                CampfireKind campfire = CampfireDefinition.parse(n,recipeType,path);
                var operation = new OperationSpec(recipe,recipeMode(n,path),campfire);
                var node = new NodeDefinition<>(nodeId,roles,operation,inputs,
                    new NodeDefinition.NodeEffects(carrier,markAmounts,maxMarkApplications,checkpoint,actions.mutations()),
                    new NodeDefinition.RoutingPolicy(branch,actions.redirect()),
                    new NodeDefinition.SettlementPolicy(finish,outputs,scrap));
                nodeLayouts.put(nodeId,layout);
                nodeActions.put(nodeId,actions);
                if (nodes.putIfAbsent(nodeId, node) != null) throw invalid(path + ".id", "duplicate node: " + nodeId);
                if (node.roles().start() && node.inputs().ingredientOverride() == null && ingredient == null)
                    throw invalid(path + ".ingredient", "start has no ingredient and no graph default");
            }
            if (nodes.values().stream().noneMatch(node -> node.roles().start())) throw invalid("nodes", "at least one start is required");

            List<Edge> edges = new ArrayList<>();
            Set<List<String>> connections = new HashSet<>();
            JsonArray edgeArray = array(json, "edges", "graph", true);
            for (int i = 0; i < edgeArray.size(); i++) {
                String path = "edges[" + i + "]";
                JsonObject edge = object(edgeArray.get(i), path);
                fields(edge,path,"from","to","weight","bidirectional","waypoints","requirements");
                var admission = requirements(edge,path);
                validateRequirementReferences(admission,nodes,path+".requirements",edge.getAsJsonObject("requirements"));
                String from = requiredText(edge, "from", path), to = requiredText(edge, "to", path);
                requireNode(nodes, from, path + ".from");
                requireNode(nodes, to, path + ".to");
                double weight = weight(edge, path);
                List<GraphLayout.Waypoint> waypoints = waypoints(edge, path);
                addEdge(edges, edgeLayouts, connections, from, to, weight, admission, waypoints, path);
                if (bool(edge, "bidirectional", path) && !from.equals(to))
                    addEdge(edges, edgeLayouts, connections, to, from, weight, admission, waypoints.reversed(), path);
            }
            List<OutputRule> outputs = rules(json, "outputs", "graph");
            validateReferences(outputs, nodes, "graph.outputs", array(json, "outputs", "graph", false));
            int nodeIndex = 0;
            for (NodeDefinition<OperationSpec> node : nodes.values()) {
                String path = "nodes[" + nodeIndex + "]";
                JsonObject rawNode = nodeArray.get(nodeIndex++).getAsJsonObject();
                validateActions(nodeActions.get(node.id()), nodes, edges);
                validateReferences(node.settlement().outputRules(), nodes, path + ".outputs",
                    array(rawNode, "outputs", path, false));
                JsonArray rawInputs = array(rawNode, "workpiece_inputs", path, false);
                for (int i = 0; i < node.inputs().workpieceInputs().size(); i++) {
                    WorkpieceInput input = node.inputs().workpieceInputs().get(i);
                    String here = path + ".workpiece_inputs[" + i + "]";
                    for (int j = 0; j < input.currentNodes().size(); j++)
                        requireNode(nodes, input.currentNodes().get(j), here + ".current_nodes[" + j + "]");
                    validateRequirementReferences(input.requirements(), nodes, here + ".requirements",
                        rawInputs.get(i).getAsJsonObject().getAsJsonObject("requirements"));
                }
            }
            return new GraphDefinition<>(id,new GraphDefaults(ingredient,transitionalItem),nodes,new GraphTopology(edges),outputs,new GraphLayout(nodeLayouts,edgeLayouts));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Graph " + id + ": " + e.getMessage(), e);
        }
    }

    // One authoritative parsed sequence; domain mutations and redirects remain separate types.
    private sealed interface ParsedAction {
        String path();
    }
    private record MutationAction(String path, ProgressMutation mutation) implements ParsedAction {}
    private record RedirectAction(String path, RouteRedirect redirect) implements ParsedAction {}

    private record NodeActions(List<ParsedAction> ordered) {
        private NodeActions {
            ordered = List.copyOf(ordered);
        }

        List<ProgressMutation> mutations() {
            return ordered.stream().filter(MutationAction.class::isInstance)
                .map(MutationAction.class::cast).map(MutationAction::mutation).toList();
        }

        RouteRedirect redirect() {
            return ordered.stream().filter(RedirectAction.class::isInstance)
                .map(RedirectAction.class::cast).map(RedirectAction::redirect).findFirst().orElse(null);
        }
    }

    private static NodeActions actions(JsonObject node, String parent) {
        var values = array(node, "progress_actions", parent, false);
        List<ParsedAction> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String path = parent + ".progress_actions[" + i + "]";
            var value = object(values.get(i), path);
            String type = requiredText(value, "type", path);
            result.add(switch (type) {
                case "clear_mark" -> {
                    fields(value,path,"type","mark","count");
                    yield new MutationAction(path, new ProgressMutation.ClearMark(requiredText(value,"mark",path), value.has("count")
                        ? integer(value.get("count"),path+".count",0) : null));
                }
                case "clear_checkpoint" -> {
                    fields(value,path,"type","node");
                    yield new MutationAction(path, new ProgressMutation.ClearCheckpoint(requiredText(value,"node",path)));
                }
                case "pop_checkpoint" -> {
                    fields(value,path,"type");
                    yield new MutationAction(path, new ProgressMutation.PopCheckpoint());
                }
                case "goto_node" -> {
                    fields(value,path,"type","node");
                    yield new RedirectAction(path, new RouteRedirect.GoToNode(requiredText(value,"node",path)));
                }
                case "enter_edge" -> {
                    fields(value,path,"type","from","to");
                    yield new RedirectAction(path, new RouteRedirect.EnterEdge(requiredText(value,"from",path),requiredText(value,"to",path)));
                }
                default -> throw new IllegalArgumentException(path + ".type: unknown progress action: " + type);
            });
        }
        return new NodeActions(result);
    }

    private static void validateActions(NodeActions parsed, Map<String,NodeDefinition<OperationSpec>> nodes, List<Edge> edges) {
        int routes=0;
        for (ParsedAction action : parsed.ordered()) {
            String path=action.path();
            if (action instanceof MutationAction mutation && mutation.mutation() instanceof ProgressMutation.ClearCheckpoint checkpoint
                && !nodes.containsKey(checkpoint.node()))
                throw new IllegalArgumentException(path+".node: unknown node: "+checkpoint.node());
            var route=action instanceof RedirectAction redirect ? redirect.redirect().pendingRoute() : null;
            if (route!=null) {
                if (++routes > 1) throw new IllegalArgumentException(path+": at most one route action per node");
                if (!nodes.containsKey(route.node())) throw new IllegalArgumentException(path+": unknown target: "+route.node());
                if (route.from()!=null && edges.stream().noneMatch(e->e.from().equals(route.from()) && e.to().equals(route.node())))
                    throw new IllegalArgumentException(path+": unknown directed edge: "+route.from()+" -> "+route.node());
            }
        }
    }

    private static RecipeMode recipeMode(JsonObject node, String path) {
        if (!node.has("recipe_mode")) return RecipeMode.STEP;
        return switch (requiredText(node, "recipe_mode", path)) {
            case "native" -> RecipeMode.NATIVE;
            case "step" -> RecipeMode.STEP;
            default -> throw invalid(path + ".recipe_mode", "expected native or step");
        };
    }

    private static void addEdge(List<Edge> edges, Map<GraphLayout.Endpoint,GraphLayout.EdgeLayout> layouts, Set<List<String>> connections, String from, String to, double weight, ProgressRequirements admission,
                                List<GraphLayout.Waypoint> waypoints, String path) {
        if (!connections.add(List.of(from, to))) throw invalid(path, "duplicate directed edge: " + from + " -> " + to);
        edges.add(new Edge(from,to,weight,admission));
        layouts.put(new GraphLayout.Endpoint(from,to),new GraphLayout.EdgeLayout(waypoints));
    }

    private static List<GraphLayout.Waypoint> waypoints(JsonObject edge, String parent) {
        JsonArray values = array(edge, "waypoints", parent, false);
        if (values.size() > MAX_EDGE_WAYPOINTS)
            throw invalid(parent + ".waypoints", "must contain at most " + MAX_EDGE_WAYPOINTS + " pairs");
        List<GraphLayout.Waypoint> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String path = parent + ".waypoints[" + i + "]";
            JsonElement value = values.get(i);
            if (!value.isJsonArray() || value.getAsJsonArray().size() != 2)
                throw invalid(path, "expected exactly two coordinates");
            JsonArray pair = value.getAsJsonArray();
            result.add(new GraphLayout.Waypoint(waypointCoordinate(pair.get(0), path + "[0]"),
                waypointCoordinate(pair.get(1), path + "[1]")));
        }
        return result;
    }

    private static double waypointCoordinate(JsonElement value, String path) {
        double coordinate = number(value, path);
        if (Math.abs(coordinate) > MAX_WAYPOINT_COORDINATE)
            throw invalid(path, "absolute value must not exceed " + MAX_WAYPOINT_COORDINATE);
        return coordinate;
    }

    private static JsonElement ingredient(JsonObject json, String parent) {
        JsonElement value = json.get("ingredient");
        rejectFluid(value, parent + ".ingredient", true);
        return value == null || value.isJsonNull() ? null : value;
    }

    private static void rejectFluid(JsonElement value, String path) {
        rejectFluid(value, path, false);
    }

    private static void rejectFluid(JsonElement value, String path, boolean ingredient) {
        if (value != null && value.isJsonObject()) {
            var object = value.getAsJsonObject();
            // A typed item Ingredient owns its schema (e.g. a filled-container predicate).
            // Let the native item codec validate it, rather than infer its carrier from field names.
            if (ingredient && object.has("type")) return;
            if (object.has("fluid") || object.has("fluid_tag"))
                throw invalid(path, "fluid workpieces/results are unsupported; use an item (fluids are only filling auxiliaries)");
        } else if (value != null && value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++) rejectFluid(value.getAsJsonArray().get(i), path + "[" + i + "]", ingredient);
        }
    }

    private static List<OutputRule> rules(JsonObject json, String field, String parent) {
        List<OutputRule> rules = new ArrayList<>();
        JsonArray array = array(json, field, parent, false);
        for (int i = 0; i < array.size(); i++) {
            String path = parent + "." + field + "[" + i + "]";
            JsonObject rule = object(array.get(i), path);
            fields(rule, path, "start", "finish", "requirements", "results");
            ProgressRequirements requirements = requirements(rule, path);
            rules.add(new OutputRule(optionalText(rule, "start", path), optionalText(rule, "finish", path),
                    requirements, results(rule, "results", path, true)));
        }
        return rules;
    }

    private static ProgressRequirements requirements(JsonObject rule, String parent) {
        if (!rule.has("requirements")) return ProgressRequirements.NONE;
        String path = parent + ".requirements";
        JsonObject value = object(rule.get("requirements"), path);
        fields(value, path, "mode", "marks", "checkpoints", "mark_ranges", "checkpoint_counts");
        MarkRequirement.Mode common = value.has("mode") ? markMode(requiredText(value, "mode", path), path + ".mode") : MarkRequirement.Mode.EXACT;
        MarkRequirement.Match marks = null;
        if (value.has("marks")) {
            JsonObject raw = object(value.get("marks"), path + ".marks");
            if (raw.has("values") && raw.get("values").isJsonObject()) {
                fields(raw, path + ".marks", "mode", "values");
                marks = new MarkRequirement.Match(markMode(requiredText(raw, "mode", path + ".marks"), path + ".marks.mode"),
                    counts(raw, "values", path + ".marks"));
            } else marks = new MarkRequirement.Match(common, counts(value, "marks", path));
        }
        CheckpointRequirement.Match checkpoints = value.has("checkpoints")
            ? checkpoints(value.get("checkpoints"), common == MarkRequirement.Mode.EXACT ? CheckpointMode.EXACT : CheckpointMode.SUBSEQUENCE,
                path + ".checkpoints") : null;
        return new ProgressRequirements(new MarkRequirement(marks, ranges(value,"mark_ranges",path)),
            new CheckpointRequirement(checkpoints, ranges(value,"checkpoint_counts",path)));
    }

    private static List<WorkpieceInput> workpieceInputs(JsonObject node, String parent) {
        List<WorkpieceInput> result = new ArrayList<>();
        JsonArray inputs = array(node, "workpiece_inputs", parent, false);
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            String path = parent + ".workpiece_inputs[" + i + "]";
            JsonObject input = object(inputs.get(i), path);
            fields(input, path, "item_input", "current_nodes", "requirements");
            int index = integer(input.get("item_input"), path + ".item_input", 0);
            if (!indexes.add(index)) throw invalid(path + ".item_input", "duplicate item input: " + index);
            List<String> currentNodes = new ArrayList<>();
            if (input.has("current_nodes")) {
                JsonArray ids = array(input, "current_nodes", path, true);
                if (ids.isEmpty()) throw invalid(path + ".current_nodes", "must not be empty");
                Set<String> seen = new HashSet<>();
                for (int j = 0; j < ids.size(); j++) {
                    String id = text(ids.get(j), path + ".current_nodes[" + j + "]");
                    if (!seen.add(id)) throw invalid(path + ".current_nodes[" + j + "]", "duplicate node: " + id);
                    currentNodes.add(id);
                }
            }
            result.add(new WorkpieceInput(index, currentNodes, requirements(input, path)));
        }
        return result;
    }

    private static Map<String, IntRange> ranges(JsonObject json, String field, String parent) {
        Map<String, IntRange> result = new LinkedHashMap<>();
        if (!json.has(field)) return result;
        String path = parent + "." + field;
        for (var entry : object(json.get(field), path).entrySet()) {
            String here = path + "." + entry.getKey();
            if (entry.getKey().isBlank()) throw invalid(here, "name must not be empty");
            JsonObject range = object(entry.getValue(), here);
            fields(range, here, "min", "max");
            if (!range.has("min") && !range.has("max")) throw invalid(here, "must provide min or max");
            int min = range.has("min") ? integer(range.get("min"), here + ".min", 0) : 0;
            int max = range.has("max") ? integer(range.get("max"), here + ".max", 0) : Integer.MAX_VALUE;
            if (min > max) throw invalid(here, "min must not exceed max");
            result.put(entry.getKey(), new IntRange(min, max));
        }
        return result;
    }

    private static MarkRequirement.Mode markMode(String value, String path) {
        return switch (value) {
            case "exact" -> MarkRequirement.Mode.EXACT;
            case "partial" -> MarkRequirement.Mode.PARTIAL;
            default -> throw invalid(path, "expected exact or partial, got " + value);
        };
    }

    private static CheckpointRequirement.Match checkpoints(JsonElement value, CheckpointMode inherited, String path) {
        CheckpointMode mode = inherited;
        JsonArray requiredNodes;
        String nodesPath = path;
        if (value.isJsonArray()) requiredNodes = value.getAsJsonArray();
        else {
            JsonObject constraint = object(value, path);
            fields(constraint, path, "mode", "nodes");
            String name = requiredText(constraint, "mode", path);
            mode = switch (name) {
                case "exact" -> CheckpointMode.EXACT;
                case "partial" -> CheckpointMode.SUBSEQUENCE;
                case "unordered" -> CheckpointMode.UNORDERED;
                default -> throw invalid(path + ".mode", "unknown checkpoint mode: " + name);
            };
            requiredNodes = array(constraint, "nodes", path, true);
            nodesPath += ".nodes";
        }
        List<String> ids = new ArrayList<>();
        for (int j = 0; j < requiredNodes.size(); j++) ids.add(text(requiredNodes.get(j), nodesPath + "[" + j + "]"));
        return new CheckpointRequirement.Match(mode, ids);
    }

    private static List<OutputNode> results(JsonObject json, String field, String parent, boolean required) {
        List<OutputNode> outputs = new ArrayList<>();
        JsonArray array = array(json, field, parent, required);
        if (required && array.isEmpty()) throw invalid(parent + "." + field, "must contain at least one result");
        for (int i = 0; i < array.size(); i++) {
            String path = parent + "." + field + "[" + i + "]";
            JsonObject output = object(array.get(i), path);
            rejectFluid(output, path);
            double chance = output.has("chance") ? number(output.get("chance"), path + ".chance") : 1;
            if (chance < 0 || chance > 1) throw invalid(path + ".chance", "must be between 0 and 1");
            if (output.has("mode") || output.has("results")) {
                fields(output, path, "mode", "results", "weight", "chance");
                var mode = switch (requiredText(output, "mode", path)) {
                    case "all" -> OutputGroup.Mode.ALL;
                    case "weighted" -> OutputGroup.Mode.WEIGHTED;
                    default -> throw invalid(path + ".mode", "expected all or weighted");
                };
                array(output, "results", path, true);
                outputs.add(new OutputGroup(mode, results(output, "results", path, mode == OutputGroup.Mode.WEIGHTED), weight(output, path), chance));
            } else {
                fields(output, path, "item", "count", "weight", "components", "chance");
                outputs.add(new WeightedOutput(requiredText(output, "item", path), output.has("count")
                        ? integer(output.get("count"), path + ".count", 1) : 1, weight(output, path),
                        output.has("components") ? object(output.get("components"), path + ".components") : new JsonObject(), chance));
            }
        }
        return outputs;
    }

    private static void validateReferences(List<OutputRule> rules, Map<String, NodeDefinition<OperationSpec>> nodes, String path, JsonArray rawRules) {
        for (int i = 0; i < rules.size(); i++) {
            OutputRule rule = rules.get(i);
            String here = path + "[" + i + "]";
            if (rule.start() != null && !requireNode(nodes, rule.start(), here + ".start").roles().start())
                throw invalid(here + ".start", "node is not a start: " + rule.start());
            if (rule.finish() != null && !requireNode(nodes, rule.finish(), here + ".finish").roles().finish())
                throw invalid(here + ".finish", "node is not a finish: " + rule.finish());
            validateRequirementReferences(rule.requirements(), nodes, here + ".requirements",
                rawRules.get(i).getAsJsonObject().getAsJsonObject("requirements"));
        }
    }

    private static void validateRequirementReferences(ProgressRequirements requirements, Map<String, NodeDefinition<OperationSpec>> nodes,
                                                       String path, JsonObject raw) {
        if (requirements.checkpoints().match() != null) {
            String checkpointPath = path + ".checkpoints" + (raw.get("checkpoints").isJsonObject() ? ".nodes" : "");
            List<String> ids = requirements.checkpoints().match().nodes();
            for (int j = 0; j < ids.size(); j++) requireNode(nodes, ids.get(j), checkpointPath + "[" + j + "]");
        }
        for (String id : requirements.checkpoints().counts().keySet())
            requireNode(nodes, id, path + ".checkpoint_counts." + id);
    }

    private static NodeDefinition<OperationSpec> requireNode(Map<String, NodeDefinition<OperationSpec>> nodes, String id, String path) {
        NodeDefinition<OperationSpec> node = nodes.get(id);
        if (node == null) throw invalid(path, "unknown node: " + id);
        return node;
    }

    private static BranchMode branchMode(JsonObject json, String path) {
        String value = optionalText(json, "branch_mode", path);
        if (value == null || value.equals("choice")) return BranchMode.CHOICE;
        if (value.equals("random")) return BranchMode.RANDOM;
        throw invalid(path + ".branch_mode", "unknown branch mode: " + value);
    }

    private static FinishMode finishMode(JsonObject json, String path) {
        String value = optionalText(json, "finish_mode", path);
        if (value == null || value.equals("lenient")) return FinishMode.LENIENT;
        if (value.equals("strict")) return FinishMode.STRICT;
        throw invalid(path + ".finish_mode", "unknown finish mode: " + value);
    }

    private static List<Double> coordinates(JsonObject json, String field, String parent, int dimensions) {
        if (!json.has(field)) return List.of();
        JsonArray values = array(json, field, parent, true);
        String path = parent + "." + field;
        if (values.size() != dimensions) throw invalid(path, "expected " + dimensions + " coordinates");
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(number(values.get(i), path + "[" + i + "]"));
        if (dimensions == 3 && result.stream().allMatch(value -> value == 0)) throw invalid(path, "sphere direction must not be zero");
        return result;
    }

    private static Map<String, Integer> counts(JsonObject json, String field, String parent) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (json.has(field)) object(json.get(field), parent + "." + field).entrySet().forEach(entry -> {
            if (entry.getKey().isBlank()) throw invalid(parent + "." + field, "mark names must not be empty");
            values.put(entry.getKey(), integer(entry.getValue(), parent + "." + field + "." + entry.getKey(), 0));
        });
        return values;
    }

    private static String optionalText(JsonObject json, String field, String path) {
        return json.has(field) ? requiredText(json, field, path) : null;
    }

    private static boolean bool(JsonObject json, String field, String path) {
        if (!json.has(field)) return false;
        JsonElement value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid(path + "." + field, "must be boolean");
        return value.getAsBoolean();
    }

    private static double weight(JsonObject json, String path) {
        if (!json.has("weight")) return 1;
        double weight = number(json.get("weight"), path + ".weight");
        if (weight <= 0) throw invalid(path + ".weight", "must be positive");
        return weight;
    }

}
