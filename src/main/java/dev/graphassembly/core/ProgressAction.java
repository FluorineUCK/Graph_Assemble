package dev.graphassembly.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Ordered, deterministic edits applied only to the primary workpiece after a successful operation. */
public sealed interface ProgressAction {
    record ClearMark(String mark, Integer count) implements ProgressAction {}
    record ClearCheckpoint(String node) implements ProgressAction {}
    record PopCheckpoint() implements ProgressAction {}
    record GoToNode(String node) implements ProgressAction {}
    record EnterEdge(String from, String to) implements ProgressAction {}

    static List<ProgressAction> parse(JsonObject node, String parent) {
        var values = GraphCodec.array(node, "progress_actions", parent, false);
        List<ProgressAction> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String path = parent + ".progress_actions[" + i + "]";
            var value = GraphCodec.object(values.get(i), path);
            String type = GraphCodec.requiredText(value, "type", path);
            result.add(switch (type) {
                case "clear_mark" -> {
                    GraphCodec.fields(value,path,"type","mark","count");
                    yield new ClearMark(GraphCodec.requiredText(value,"mark",path), value.has("count")
                        ? GraphCodec.integer(value.get("count"),path+".count",0) : null);
                }
                case "clear_checkpoint" -> {
                    GraphCodec.fields(value,path,"type","node");
                    yield new ClearCheckpoint(GraphCodec.requiredText(value,"node",path));
                }
                case "pop_checkpoint" -> {
                    GraphCodec.fields(value,path,"type");
                    yield new PopCheckpoint();
                }
                case "goto_node" -> {
                    GraphCodec.fields(value,path,"type","node");
                    yield new GoToNode(GraphCodec.requiredText(value,"node",path));
                }
                case "enter_edge" -> {
                    GraphCodec.fields(value,path,"type","from","to");
                    yield new EnterEdge(GraphCodec.requiredText(value,"from",path),GraphCodec.requiredText(value,"to",path));
                }
                default -> throw new IllegalArgumentException(path + ".type: unknown progress action: " + type);
            });
        }
        return List.copyOf(result);
    }

    static void validate(List<ProgressAction> actions, Map<String,Node> nodes, List<Edge> edges, String parent) {
        int routes=0;
        for (int i=0;i<actions.size();i++) {
            String path=parent+".progress_actions["+i+"]";
            if (actions.get(i) instanceof ClearCheckpoint action && !nodes.containsKey(action.node()))
                throw new IllegalArgumentException(path+".node: unknown node: "+action.node());
            var route=actions.get(i).route();
            if (route!=null) {
                if (++routes > 1) throw new IllegalArgumentException(path+": at most one route action per node");
                if (!nodes.containsKey(route.node())) throw new IllegalArgumentException(path+": unknown target: "+route.node());
                if (route.from()!=null && edges.stream().noneMatch(e->e.from().equals(route.from()) && e.to().equals(route.node())))
                    throw new IllegalArgumentException(path+": unknown directed edge: "+route.from()+" -> "+route.node());
            }
        }
    }

    default void apply(Map<String,Integer> marks, List<String> checkpoints) {
        switch (this) {
            case ClearMark action -> {
                if (action.count() == null) marks.remove(action.mark());
                else if (action.count() > 0) {
                    int remaining = marks.getOrDefault(action.mark(),0) - action.count();
                    if (remaining <= 0) marks.remove(action.mark());
                    else marks.put(action.mark(),remaining);
                }
            }
            case ClearCheckpoint action -> checkpoints.removeIf(action.node()::equals);
            case PopCheckpoint ignored -> { if (!checkpoints.isEmpty()) checkpoints.removeLast(); }
            case GoToNode ignored -> {}
            case EnterEdge ignored -> {}
        }
    }
    default PendingRoute route() {
        return switch (this) {
            case GoToNode action -> new PendingRoute(action.node(),null);
            case EnterEdge action -> new PendingRoute(action.to(),action.from());
            default -> null;
        };
    }
}
