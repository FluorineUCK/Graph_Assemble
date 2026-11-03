# Player Guide

English | [简体中文](player.zh_cn.md)

## Processing workpieces

Send an entry ingredient into the machine specified by the graph to obtain a workpiece carrying graph progress. Its tooltip shows a localized summary of the processing step; press F3+H to see technical IDs and the full stop diagnosis. A valid workpiece can perform only a legal next step in its current graph. Once selected, a random branch is locked and is not rerolled by queries or `/reload`. At a finish node, outputs are settled according to recipe conditions. If no rule matches, a lenient finish retains the workpiece and allows processing along outgoing edges; a strict finish handles it according to its failure configuration.

Before a merge step, the primary and auxiliary workpiece tooltips list the items and completed steps required from the other workpiece. A ready auxiliary workpiece is no longer incorrectly reported as stopped; the merged result continues with the primary workpiece's progress.

## Graph Assembly Filter

In Survival mode, craft the filter using the [built-in filter tutorial recipe](kubejs.md#13-built-in-graph-filter-two-workpiece-tutorial-recipe); for testing, you can also obtain one with `/give @s graph_assembly:graph_filter`. Right-click to open the selection screen. Use the mouse wheel on recipe and step rows to select targets. “+” adds a positive condition (the step is currently allowed); “!+” adds a negative condition (the step is currently disallowed). Choose “Any / All / Exclude” at the bottom: at least one condition matches, all conditions match, or none match (negate each condition, then combine). A step cannot have both positive and negative conditions. Confirm to save or press Esc to cancel. Clear changes only the current draft; switching recipes clears the previous graph's conditions.

The filter matches only valid workpieces or legal entry ingredients for the selected graph, and there must be an executable step. Invalid configurations, stopped workpieces, other graphs, and unrelated items are not allowed through by negative conditions. JEI and technical IDs are not required. The filter can also be placed in a Create filter slot or an ordinary filter.

Filter conditions are evaluated against the primary workpiece; an auxiliary workpiece that can participate in a merge does not thereby become a primary workpiece eligible to execute that step. By default, the tooltip shows saved conditions; hold Shift for a usage summary styled like Create's native filters.

## Limits and preview

A single filter supports at most 256 unique targets. Recipe IDs and node IDs are limited to 1,024 UTF-16 code units each; configuration JSON is limited to 16,384 UTF-8 bytes, including escapes. Over-limit configurations cannot be confirmed and show the reason; the server validates them too and never truncates them.

With JEI installed, open the “Graph Assembly” preview from an ingredient's uses or a final product's recipes. Toggle planar/spherical view at the upper left; drag with the left mouse button to pan or rotate, scroll to zoom, and click a node or edge for details. Settlement information appears on JEI's native Info page. The finish selector in the canvas footer is linked to that finish's successful candidates and strict scrap pool. If there are many candidates, expand them with `+N`; item slots can also query recipes or uses. Preview is for inspection only: seeing a native recipe does not mean the corresponding machine can execute graph processing.

The reset button at the upper right restores the initial view and zoom for the current mode while preserving the selected node, edge, and finish. The `×` and `+N` buttons have no hover tooltips.
