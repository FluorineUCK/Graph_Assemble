package dev.graphassembly.core.progress;

import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.route.RouteState;

import java.util.List;
/** An immutable completed-operation snapshot, not a pending operation. */
public record Progress(String graphId, String entry, String current, MarkAccounting.State marks,
                       List<String> checkpoints, RouteState routing, List<String> recentHistory) {
    public Progress { checkpoints=List.copyOf(checkpoints); recentHistory=List.copyOf(recentHistory); }

    /** Replace only pending routing; completed-operation facts remain unchanged. */
    public Progress withRouting(RouteState routing) {
        return new Progress(graphId,entry,current,marks,checkpoints,routing,recentHistory);
    }
}
