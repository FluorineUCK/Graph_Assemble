package dev.graphassembly.client.view;

import dev.graphassembly.core.*;
import java.util.*;

/** Shared Info and node-tooltip copy; no runtime effects. Keys use the settlement language namespace. */
public final class ProgressActionDescription {
    private ProgressActionDescription() {}
    private static SettlementDescription.Line line(String key,Object... args) {
        return new SettlementDescription.Line("actions."+key,List.of(args));
    }
    public static List<SettlementDescription.Line> of(Node node) {
        if(node.progressActions().isEmpty()) return List.of();
        var lines=new ArrayList<SettlementDescription.Line>();
        lines.add(line("node",node.id()));
        for(var action:node.progressActions()) lines.add(switch(action) {
            case ProgressAction.ClearMark a -> a.count()==null ? line("clear_mark",a.mark()) : line("subtract_mark",a.mark(),a.count());
            case ProgressAction.ClearCheckpoint a -> line("clear_checkpoint",a.node());
            case ProgressAction.PopCheckpoint ignored -> line("pop_checkpoint");
            case ProgressAction.GoToNode a -> line("goto_node",a.node());
            case ProgressAction.EnterEdge a -> line("enter_edge",a.from(),a.to());
        });
        return List.copyOf(lines);
    }
}
