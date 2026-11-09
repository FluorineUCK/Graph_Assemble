package dev.graphassembly.client.view;

import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.progress.ProgressMutation;
import dev.graphassembly.core.route.RouteRedirect;

import java.util.*;

/** Shared Info and node-tooltip copy; no runtime effects. Keys use the settlement language namespace. */
public final class ProgressActionDescription {
    private ProgressActionDescription() {}
    private static SettlementDescription.Line line(String key,Object... args) {
        return new SettlementDescription.Line("actions."+key,List.of(args));
    }
    public static List<SettlementDescription.Line> of(NodeDefinition<?> node) {
        if(node.effects().mutations().isEmpty() && node.routing().redirect()==null) return List.of();
        var lines=new ArrayList<SettlementDescription.Line>();
        lines.add(line("node",node.id()));
        for(var action:node.effects().mutations()) lines.add(switch(action) {
            case ProgressMutation.ClearMark a -> a.count()==null ? line("clear_mark",a.mark()) : line("subtract_mark",a.mark(),a.count());
            case ProgressMutation.ClearCheckpoint a -> line("clear_checkpoint",a.node());
            case ProgressMutation.PopCheckpoint ignored -> line("pop_checkpoint");
        });
        if(node.routing().redirect()!=null) lines.add(switch(node.routing().redirect()) {
            case RouteRedirect.GoToNode a -> line("goto_node",a.node());
            case RouteRedirect.EnterEdge a -> line("enter_edge",a.from(),a.to());
        });
        return List.copyOf(lines);
    }
}
