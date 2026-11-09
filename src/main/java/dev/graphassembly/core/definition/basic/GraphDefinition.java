package dev.graphassembly.core.definition.basic;

import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.GraphLayout;
import dev.graphassembly.core.definition.GraphTopology;
import dev.graphassembly.core.settlement.OutputRule;

import java.util.*;
/** Immutable graph with an opaque operation payload; metadata remains part of value equality. */
public record GraphDefinition<O>(String id, GraphDefaults defaults, Map<String, NodeDefinition<O>> nodes,
                              GraphTopology topology, List<OutputRule> outputRules, GraphLayout layout) {
    public GraphDefinition {
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        outputRules = List.copyOf(outputRules);
    }
}
