package dev.graphassembly.core.settlement;

import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.progress.Progress;

import java.util.*;
/** Merges all eligible rules without drawing randomness or touching device state. */
public final class SettlementPlanner {
    private SettlementPlanner() {}
    public sealed interface Decision {
        record Continue() implements Decision {}
        record Settle(List<OutputNode> outputPool) implements Decision {
            public Settle { outputPool=List.copyOf(outputPool); }
        }
    }
    public static Decision plan(List<OutputRule> graphRules, NodeDefinition<?> node, Progress after) {
        if(!node.roles().finish()) return new Decision.Continue();
        List<OutputNode> pool=new ArrayList<>();
        List<OutputRule> rules=new ArrayList<>(graphRules); rules.addAll(node.settlement().outputRules());
        var facts=new ProgressFacts(after);
        for(var rule:rules) {
            if((rule.start()==null || rule.start().equals(after.entry()))
                && (rule.finish()==null || rule.finish().equals(after.current()))
                && ProgressMatcher.matches(rule.requirements(),facts)) pool.addAll(rule.entries());
        }
        if(!pool.isEmpty()) return new Decision.Settle(pool);
        if(node.settlement().finishMode()==FinishMode.STRICT) return new Decision.Settle(node.settlement().scrapEntries());
        return new Decision.Continue();
    }
}
