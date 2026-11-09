package dev.graphassembly.core.progress;

import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.condition.checkpoint.CheckpointAccounting;
import dev.graphassembly.core.route.RouteState;

import java.util.*;
/** Applies a successful operation, before either settlement or next-route selection. */
public final class ProgressTransition {
    private ProgressTransition() {}
    public record AppliedStep(Progress progress,String carrierItem) {}
    public static AppliedStep apply(String graphId, GraphDefaults defaults, NodeDefinition<?> node, Progress before,String currentItem) {
        String entry=before==null?node.id():before.entry();
        var marks=new MarkAccounting(before==null?new MarkAccounting.State(Map.of(),Map.of()):before.marks());
        var checkpoints=new CheckpointAccounting(before==null?List.of():before.checkpoints());
        marks.grant(node);
        if(node.effects().recordCheckpoint()) checkpoints.record(node.id());
        for(var mutation:node.effects().mutations()) {
            switch(mutation) {
                case ProgressMutation.ClearMark action -> marks.clear(action);
                case ProgressMutation.ClearCheckpoint action -> checkpoints.clear(action);
                case ProgressMutation.PopCheckpoint ignored -> checkpoints.pop();
            }
        }
        String carrier=node.effects().carrierOverride()!=null?node.effects().carrierOverride()
            :before==null?defaults.transitionalItem():currentItem;
        List<String> history=new ArrayList<>(before==null?List.of():before.recentHistory());
        history.add(node.id());
        if(history.size()>3) history=history.subList(history.size()-3,history.size());
        return new AppliedStep(new Progress(graphId,entry,node.id(),marks.snapshot(),
            checkpoints.snapshot(),new RouteState(null,null),history),carrier);
    }
}
