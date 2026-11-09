package dev.graphassembly.core.condition.mark;

import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.progress.ProgressMutation;

import java.util.*;
/** One operation's private working copy; owns mark arithmetic and application-count semantics. */
public final class MarkAccounting {
    private final Map<String,Integer> values;
    private final Map<String,Integer> grantCounts;

    public MarkAccounting(State before) {
        values = new LinkedHashMap<>(before.values());
        grantCounts = new LinkedHashMap<>(before.grantCounts());
    }

    /** Immutable current values and per-node application counts; clearing values does not reset counts. */
    public record State(Map<String,Integer> values, Map<String,Integer> grantCounts) {
        public State { values=Map.copyOf(values); grantCounts=Map.copyOf(grantCounts); }
    }

    private static boolean grants(NodeDefinition<?> node, Map<String,Integer> grantCounts) {
        var effects=node.effects();
        return !effects.markAmounts().isEmpty() && (effects.maxMarkApplications()==null
            || grantCounts.getOrDefault(node.id(),0)<effects.maxMarkApplications());
    }
    public static String capacityProblem(NodeDefinition<?> node, State state) {
        if(state==null || !grants(node,state.grantCounts())) return null;
        for(var amount:node.effects().markAmounts().entrySet()) {
            int previous=state.values().getOrDefault(amount.getKey(),0);
            if((long)previous+amount.getValue()>Integer.MAX_VALUE)
                return "Node "+node.id()+": mark '"+amount.getKey()+"' would exceed "+Integer.MAX_VALUE
                    +" ("+previous+" + "+amount.getValue()+")";
        }
        if(state.grantCounts().getOrDefault(node.id(),0)==Integer.MAX_VALUE)
            return "Node "+node.id()+": mark grant counter would exceed "+Integer.MAX_VALUE;
        return null;
    }
    public void grant(NodeDefinition<?> node) {
        if(!grants(node,grantCounts)) return;
        node.effects().markAmounts().forEach((mark,amount)->values.merge(mark,amount,Math::addExact));
        grantCounts.put(node.id(),Math.incrementExact(grantCounts.getOrDefault(node.id(),0)));
    }
    public void clear(ProgressMutation.ClearMark action) {
        if(action.count()==null) values.remove(action.mark());
        else if(action.count()>0) {
            int remaining=values.getOrDefault(action.mark(),0)-action.count();
            if(remaining<=0) values.remove(action.mark()); else values.put(action.mark(),remaining);
        }
    }

    public State snapshot() { return new State(values,grantCounts); }
}
