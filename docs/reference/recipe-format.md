# Recipe Format and Script API

English | [简体中文](recipe-format.zh_cn.md)

## Graphs and Nodes

Graph progress is carried by items; entry ingredients and settlement outputs are also items. Fluids can be used for filling and as auxiliary inputs to a Basin. A complete native recipe can be previewed and executed in [adapted machines](compatibility.md).

Top-level fields: `type: "graph_assembly:assembly"`, `ingredient` (default entry ingredient), required `transitional_item` (item ID string), required `nodes` array, required `edges` array, and optional `outputs` array.

Each node needs a unique `id` and an inner `recipe`. For ordinary processing, `recipe.type` is a native serializer ID, and the corresponding codec decodes the other fields. Anvil and Brewing Stand operations use the dedicated operation descriptions below. Common Create fields include `ingredients`, `results`, `processing_time`, and `keep_held_item`.

**Create shorthand mode (default `recipe_mode: "step"`) specifies only auxiliary ingredients and byproducts.** The system inserts the primary input and output. Set `recipe_mode: "native"` for a complete Create recipe with all inputs and outputs. Vanilla crafting, the furnace family, Campfire, Stonecutter, and Smithing Table always use the complete native format.

Create 6.0.10 native item byproducts use `{ "id":"minecraft:flint", "count":1, "chance":0.25 }`; fluid inputs may use `{ "type":"neoforge:single", "fluid":"minecraft:water", "amount":250 }`. If a machine cannot carry an auxiliary input or output, execution of the entire graph is disabled; unsupported entries are not ignored.

| Node field | Default and meaning |
|---|---|
| `recipe_mode` | `step`; Create shorthand inserts primary input/output, while `native` means a complete native definition; `recipe_id` references always use the complete definition |
| `start`, `finish` | `false`; independent flags and may both be true |
| `ingredient` | Start inherits the graph-level ingredient; non-Start nodes do not participate in entry matching |
| `transitional_item` | Uses the graph-level item initially, then keeps the current item; an explicit value overrides it |
| `mark_amounts` | `{}`; names of marks granted by this operation mapped to nonnegative integer increments |
| `max_mark_applications` | Per-node mark-grant application limit; unlimited when omitted, nonnegative integer, with 0 meaning no mark grant |
| `checkpoint` | `false` for ordinary nodes; always `true` for Start/finish |
| `branch_mode` | `choice`; may be set to `random` |
| `finish_mode` | `lenient`; may be set to `strict` |
| `outputs` | `[]`; additional output rules for this finish |
| `scrap` | `[]`; weighted scrap pool for strict failure |
| `workpiece_inputs` | `[]`; declares workpiece roles by decoded native item-input logical index; see below |
| `progress_actions` | `[]`; modifies primary workpiece progress in order after successful processing; see below |
| `campfire` | `any`; only on Campfire nodes, accepts `normal` / `soul` / `any` |
| `position` | Automatic planar layout; may be `[x,y]` in pixels relative to canvas center |
| `sphere_position` | Automatic Fibonacci sphere layout; may be nonzero `[x,y,z]`, normalized to a unit-sphere direction |

`ingredient` and `transitional_item` mean the **ingredient before it enters the graph** and the **carrier after this step**, respectively; they are not two inputs consumed together. For example, `insert_start` can use an Andesite Alloy as `ingredient` and `infested_filter_insert` as `transitional_item`: the first operation consumes the alloy and declared auxiliary ingredients directly, producing a progress-bearing insert without requiring a preexisting insert. Another entry can use an Attribute Filter and produce `incomplete_graph_filter`; later nodes that omit `transitional_item` keep their own current carrier instead of reverting to the graph default. An explicit node carrier changes only that step's output, not its required input.

Whether an item has entered the graph is determined by the `graph_assembly:progress` component, not item ID. Authors may intentionally use the same item ID for the entry ingredient and carrier: without progress, it initializes from the entry Ingredient; with progress, it can continue only along the original graph's outgoing edges and is not initialized again. Invalid or damaged progress is not retried as an ordinary ingredient.

In graph shorthand previews, Start uses the real entry Ingredient, preserving tags, alternatives, and component conditions. Later previews propagate carriers upstream and use primary-workpiece `current_nodes` to exclude branches meant only for auxiliary workpieces. A preview is not a full progress simulation: if multiple carriers can reach a node, the currently displayed primary input/output represents the first reachable combination; other carriers are not treated as extra consumed inputs. Actual workpiece matching enforces the full conditions. Previews for `recipe_mode: "native"` and `recipe_id` references retain the complete native layout and ingredients and do not perform these substitutions.

An edge is `{"from":"a","to":"b","weight":1,"bidirectional":false}`. A bidirectional edge expands to two directed edges; cycles and self-loops are allowed. Edge weights must be finite and positive, and affect probability only for a source node in `random` mode. Probabilities are normalized over eligible outgoing edges.

Optional `requirements` use the same mark/checkpoint schema as workpiece roles and output rules:

```json
{"from":"heat","to":"finish","weight":3,
 "requirements":{"mark_ranges":{"quality":{"min":2}},"checkpoint_counts":{"heat":{"min":1}}}}
```

Conditional routes use edge admission; `branch_mode` still has two values. In `choice` mode, eligible successors are available for selection. In `random` mode, the current step first grants marks, records checkpoints, and applies cleanup. It then checks edge requirements, target primary-workpiece conditions, and counter capacity before drawing once and saving the target. The target machine checks auxiliary ingredients, fluids, and heat when it executes the step.

If no edge qualifies, the workpiece stops without a draw. Reloading does not reroll a saved lock. Bidirectional edges use the same conditions in both directions. `enter_edge` must satisfy the selected edge's conditions; `goto_node` bypasses edges but still checks the target's input conditions. Unknown edge fields produce an error.

Optional display waypoints: `"waypoints": [[10,20],[30,40]]`, using the same planar coordinate system as node `position`. Up to 64 pairs of finite numbers are allowed, with each coordinate's absolute value at most `1000000`. Omitted or empty arrays keep automatic routing; explicit `null`, wrong dimensions, and non-numeric values report the field path. The path runs through the start, waypoints, and end; drawing and hit-testing share this path. Waypoints do not add operations, checkpoints, probability, or consumption, and are ignored in sphere mode. `bidirectional:true` copies waypoints in reverse order; define both directions separately if the two paths should diverge. Editing display waypoints does not change valid workpiece routes.

## Complete Native Template

A complete recipe includes its primary input and output. For an inline Create recipe, set node-level `recipe_mode: "native"` to use the native format directly:

```json
{
  "id": "press",
  "start": true,
  "finish": true,
  "recipe_mode": "native",
  "recipe": {
    "type": "create:pressing",
    "ingredients": [{"item": "minecraft:iron_ingot"}],
    "results": [{"id": "create:iron_sheet"}]
  }
}
```

This is a node fragment; the full graph still needs an entry ingredient, default workpiece, and settlement rules. At execution, adapted Create types replace logical item input 0/output 0 with this round's graph workpiece; all other ingredients, processing conditions, and byproducts remain. The graph preview shows the full template, and the footer shows graph settlement outputs.

KubeJS `node(id, KubeRecipe)` and `nativeNode(id, json)` set this mode automatically. `recipe_id` references a loaded recipe and needs no separate mode. `recipe_mode` selects how the node's recipe is represented.

## Post-Processing Progress Actions

`progress_actions` is a node option at the same level as `recipe`. After the machine completes the step, the graph grants marks, records the current checkpoint, and runs the actions in array order. At a finish node, settlement follows. These actions do not add a separate processing step.

| Action | Configuration and effect |
|---|---|
| Clear a mark | `{"type":"clear_mark","mark":"defect"}`: remove the mark |
| Decrement a mark | `{"type":"clear_mark","mark":"defect","count":2}`: subtract the given amount; if insufficient, set to zero and remove the name. 0 changes nothing; range `0..2147483647` |
| Clear a checkpoint node | `{"type":"clear_checkpoint","node":"inspect"}`: remove **all** checkpoint records for that node; preserve the order of other records |
| Pop the latest checkpoint | `{"type":"pop_checkpoint"}`: remove the last record; no effect on an empty list |
| Set the next operation | `{"type":"goto_node","node":"inspect"}`: make the node the next step without recording it as completed |
| Enter a specific edge | `{"type":"enter_edge","from":"inspect","to":"finish"}`: select an existing directed graph edge; the next operation is at its destination, without executing or recording its source |

Each node can have at most one reroute action (`goto_node` or `enter_edge`); it overrides that node's ordinary or random outgoing-edge selection and does not roll a random choice. If this round has already settled successfully or failed strictly, it is not rerouted. The target must still satisfy its own input conditions; `current_nodes` is checked against the node just completed, not `enter_edge.from`.

Clearing a nonexistent mark or record has no effect. Start and finish automatically record checkpoints, so `pop_checkpoint` on either removes the record just appended for this round. Clearing checkpoints also affects `checkpoint_counts`, but does not change the entry workpiece, recent-processing history, or `mark_grant_counts` state. Rerouting does not undo processing or refund consumed ingredients. Dual-workpiece processing modifies only the primary workpiece's progress.

A pending route is stored on the workpiece as `pending_route` until processing at the target succeeds. The engine writes this field; it is not a recipe field. If the target or selected edge is removed, or the current node's reroute configuration changes, processing stops and the workpiece is retained. Older workpieces without this field keep their previous behavior.

Complete rework example: [`examples/datapack/progress-actions.json`](../../examples/datapack/progress-actions.json).

## Multiple Workpiece Input Roles

The primary workpiece is always logical item input `0`. By default, only it may carry graph progress; ordinary auxiliary inputs must not have `graph_assembly:progress`. To use a second or third processed workpiece, declare its role in the node option `workpiece_inputs` rather than accepting a progress-bearing item as an ordinary Ingredient.

```json
{
  "id": "join",
  "recipe": {
    "type": "create:mixing",
    "ingredients": [{"item": "minecraft:iron_nugget"}]
  },
  "workpiece_inputs": [
    {"item_input": 0, "current_nodes": ["body", "polish"]},
    {"item_input": 1, "current_nodes": ["insert"],
     "requirements": {"mark_ranges": {"quality": {"min": 99, "max": 99}}}}
  ]
}
```

This is a node fragment; the full graph also needs nodes such as `body`, `polish`, and `insert`, with valid outgoing edges. Role fields:

| Field | Meaning |
|---|---|
| `item_input` | Required nonnegative integer: logical index of the item Ingredient. 0 is the primary workpiece, not a physical machine-inventory slot. In Create shorthand, numbering follows ingredients after inserting the primary item; complete definitions use native item-input order; vanilla crafting uses nonempty grid/array order as described below |
| `current_nodes` | Optional; when provided, a nonempty, duplicate-free array of node IDs in this graph restricting the input workpiece's current node |
| `requirements` | Optional; checks this role's workpiece marks and complete checkpoint history using the four condition types below; it is not limited to the primary workpiece |

Fluid inputs do not occupy `item_input` indices. For example, Basin shorthand ingredients listed as water then iron nuggets become logical item input 1 for the nuggets after the primary workpiece is inserted. A complete recipe does not insert the primary item, so its first item input is 0. Do not substitute raw JSON indices or Basin physical slot indices. Each index may be declared once; an index beyond the decoded native recipe's item-input count disables the graph.

Every declared workpiece must carry valid progress, belong to the **same graph**, and be allowed to enter this node from its current state: a saved pending route (`pending_route`) takes priority; otherwise a drawn random lock is followed if present; otherwise ordinary outgoing edges apply. `current_nodes` and `requirements` are additional filters and do not grant a route. Primary role 0 may be omitted and remains subject to normal graph traversal. If explicitly declared, it too must already be a graph workpiece; an ordinary ingredient cannot fill that role.

All inputs must still satisfy native Ingredient, quantity, fluid, heat, and output-capacity conditions. Auxiliary inputs are consumed or retained according to native machine behavior; for example, a Deployer's `keep_held_item` is not changed by declaring a workpiece role. **Only the primary workpiece's updated graph progress reaches the primary output; auxiliary marks, checkpoints, mark-grant counts, and random locks are not merged. Byproducts do not clone workpiece progress.**

See [`examples/kubejs/multi_workpiece.js`](../../examples/kubejs/multi_workpiece.js) for a complete example, and the [KubeJS guide](../guide/kubejs.md#dual-workpiece-basin-merge-example) for a two-branch flow and range-settlement tutorial.

## Current Inline Execution Types and Machine Conditions

The limits below exclude the primary workpiece and primary output. Shorthand inserts them automatically; complete definitions use native item input 0/output 0:

| Type | Auxiliary item inputs | Auxiliary fluid inputs | Extra item outputs | Native conditions |
|---|---:|---:|---:|---|
| `create:pressing` | 0 | 0 | 1 | Mechanical Press |
| `create:cutting` | 0 | 0 | 3 | Native cutting time and machine conditions |
| `create:deploying` | Exactly 1 | 0 | 3 | `keep_held_item` can retain the auxiliary item |
| `create:filling` | 0 | Exactly 1 | 0 | Byproducts are not accepted |
| `create:milling` | 0 | 0 | 3 | `processing_time > 0` |
| `create:mixing` | Up to 63 | Up to 2 | 3 | Basin limits: 64 total item inputs, 4 total item outputs; checks `heat_requirement` and actual heat |
| `create:compacting` | Up to 63 | Up to 2 | 3 | Same Basin capacity and heat limits; uses the Basin compacting path, not single-item pressing |
| `create:crushing` | 0 | 0 | 6 | native `processing_time` (including 0); excess input remains untouched if output capacity is insufficient |
| `create:sandpaper_polishing` | 0 | 0 | 0 | Handheld or Deployer sandpaper; settlement must fit at most one stack |
| `create:splashing` | 0 | 0 | 11 | Native washing airflow timer |
| `create:haunting` | 0 | 0 | 11 | Native haunting airflow timer |
| `create:emptying` | 0 | 0 | 0 | Exactly one nonempty fluid output; tank and single-stack delivery capacity checks |

Native `ingredients` may mix items and fluids, but only decoded item Ingredients count toward role indices. Basin `heat_requirement` may use native requirements such as `heated` / `superheated`; missing heat or resources cannot be bypassed by graph rules. Only `create:emptying` accepts native fluid outputs; fluid workpieces and fluid settlement remain unsupported. Step-mode emptying uses `results: [{"id":"minecraft:water","amount":250}]`, with the graph inserting the primary item result. Native mode supplies both the item and fluid results.

Addon recipes using these native types follow the same machine rules. See [Execution Compatibility](compatibility.md) for custom-machine and processing-type status.

The base environment works without optional mods, but a graph containing a missing serializer cannot execute. Standard vanilla graph nodes can also run in the corresponding Create replacement machines, including fan smoking and smelting, following each machine's settings, automation exclusions, and native conditions. See [Execution Compatibility](compatibility.md) for the full boundaries.

## Vanilla Inventory and Crafting Table

Standard `minecraft:crafting_shaped` / `minecraft:crafting_shapeless` nodes use complete native recipes (including `result`) or strict `{type, recipe_id}` references. Vanilla inventory, crafting-table, and Crafter menus are supported. The exact native `ArmorDyeRecipe` and `RepairItemRecipe` are also supported. Other special crafting recipes, custom recipe subclasses, and third-party menus are out of scope.

For shaped crafting, nonempty cells in the unmirrored pattern are numbered row by row; repeated symbols count once per cell. For shapeless crafting, numbering follows `ingredients` order. The primary role 0 ingredient is replaced by the graph ingredient/workpiece; all other ingredients retain native conditions, and each cell consumes one item. The actual output is determined by graph progress and settlement; native `result` is not produced as an extra output. Mirroring and translation do not swap primary and auxiliary roles.

Left- or right-click takes one complete round, with the first stack on the cursor and the rest in inventory. Shift sends all output to inventory and revalidates each round. Swaps accept only an empty target. The graph preview disables middle-click copying and direct dropping.

See the [KubeJS guide](../guide/kubejs.md#inventory-and-crafting-table-recipes) for node examples.

## Vanilla Furnace Family

`minecraft:smelting`, `minecraft:smoking`, and `minecraft:blasting` run in Furnace, Smoker, and Blast Furnace respectively; only standard recipe classes are accepted. The corresponding Create fan paths are described under [Execution Compatibility](compatibility.md#creates-vanilla-replacement-machines). Use full native JSON or a strict `{type, recipe_id}` reference. Logical item input is only 0; fuel is not an auxiliary workpiece. `cookingtime > 0`, and `experience` must be finite and nonnegative. Native `result` is used only for decoding; actual output follows graph settlement.

Every possible output combination must fit completely in one output slot. Items merge only when item and components match, and the total must not exceed slot capacity or the item stack limit. A second output is never discarded, and the machine does not reroll to fit available space. An empty result can complete a round. If space is insufficient, no input is consumed and no random choice is made; already burning fuel continues on its vanilla timer.

Adding more input with the same item and components preserves current processing progress. Replacing the item or changing the current graph/reference definition restarts the timer. After loading a save, the machine checks the definition and workpiece again before producing output. Experience is recorded only for successful rounds and collected by taking output or breaking the machine; hopper extraction does not collect it early. Custom subclasses are not supported.

See the [KubeJS guide](../guide/kubejs.md#furnaces-smokers-and-blast-furnaces) for examples.

## Vanilla Campfire

Standard `minecraft:campfire_cooking` works on Campfires and Soul Campfires. Node condition `campfire: "normal"` restricts it to a Campfire; `campfire: "soul"` restricts it to a Soul Campfire; omitted or set to `"any"` allows either. The condition is a sibling of `recipe`, not part of the native recipe; it affects only the graph step and does not change ordinary food recipes. Full native JSON or a `{ type, recipe_id }` reference still uses native `cookingtime`.

Explicit `campfire` is allowed only on `minecraft:campfire_cooking` nodes. Invalid values, `null`, non-string values, and mistakenly placing it inside `recipe` report a field-path error. Different fire types can disambiguate recipes with the same ingredient; if multiple steps still match the same fire type, processing does not occur rather than choosing by definition order.

Each of the four slots processes one item independently; it is not a four-input recipe. Each operation has only logical input 0, the primary workpiece, and cannot declare auxiliary workpieces. When extinguished, the Campfire cools as in vanilla and resumes timing when relit. On completion it drops the complete graph settlement result, without the Furnace's single-output-slot limit. Campfires consume no fuel items, grant no furnace experience, and have no native hopper inventory input. Replacing the item or changing the graph/reference definition restarts the timer. Special recipe subclasses and addon custom Campfires are not included in this adapter.

## Vanilla Dyeing and Repair

The exact native `minecraft:crafting_special_armordye` and `minecraft:crafting_special_repairitem` are supported in inventory, crafting-table, and vanilla Crafter menus. For dyeing, the dyed item is logical input 0, and nonempty dye slots are inputs 1 onward from left to right, top to bottom. For repair, the first nonempty slot is the primary workpiece and the second is auxiliary; both are validated separately and only primary progress is inherited.

Non-finish steps use the native result as the carrier, overriding the graph-level default carrier; finish steps settle graph outputs. Node-level `transitional_item` is not allowed. Native repair removes the name and ordinary enchantments; curses and durability follow the native result. Create special graph execution and other special crafting recipes are not supported.

## Brewing Stand Operation

`graph_assembly:brewing` describes one operation of a vanilla Brewing Stand. It queries native `PotionBrewing` effects and does not register separate potion formulas.

| Field | Meaning |
|---|---|
| `type` | Fixed to `graph_assembly:brewing` |
| `primary` | `bottle` (default) or `ingredient` |
| `addition` | Optional Ingredient restricting the non-primary input |

Only these three fields are accepted. `recipe_id`, result, time, and cost fields are unsupported. Node-level `transitional_item` is disabled; non-finish steps use the actual native result as the progress carrier and ignore the graph default carrier.

Physical slots 0–2 hold bottles, 3 the shared ingredient, and 4 fuel. Logical input 0 is always primary and input 1 the other input: in `bottle` mode they are bottle, ingredient; in `ingredient` mode ingredient, bottle. A progress-bearing auxiliary workpiece must be declared as input 1 in `workpiece_inputs` and is checked separately for graph identity, route, and requirements. The result inherits only the primary workpiece's history.

When the ingredient is primary, it distributes progress to all eligible bottles; each bottle advances and makes its own random selection, while the ingredient is consumed once. If any progress-bearing input is invalid, the two modes compete, or capacity is insufficient, the entire round is rejected. Unclaimed ordinary bottles remain untouched in ingredient-primary mode. With an ordinary ingredient, bottle-primary rounds may also process eligible ordinary bottles.

Each participating bottle and any progress-bearing ingredient must have a count of one. Each original bottle slot accepts an empty graph result or one item; all possible settlement combinations must fit, checked before random selection. Graph outputs are not spilled or dropped. Native ingredient remainders are returned normally. A progress-bearing Blaze Powder cannot be consumed as fuel.

A changed graph definition or workpiece invalidates the old timer. Adding ordinary fuel does not reset the operation. Successful completion preserves native Pre/Post events; cancellation does not advance graph progress or refund elapsed time and fuel. A Post event may change participating bottle results; if it changes the ingredient, fuel, or a nonparticipating bottle, the graph round is rejected.

See the [KubeJS guide](../guide/kubejs.md#brewing-stand) for an example. Integration covers the exact vanilla Brewing Stand, not standalone addon devices or Create fluid processing.

## Anvil Operation

`graph_assembly:anvil` describes an operation on a graph node; it is not a standalone executable recipe. `recipe_id` and custom `result`, cost, or ingredient-consumption fields are unsupported.

| Field | Meaning |
|---|---|
| `type` | Fixed to `graph_assembly:anvil` |
| `action` | Optional: `work` (default; repair or enchantment enhancement), `repair`, `enchant`, `rename`, or `combine` |
| `addition` | Optional Ingredient restricting the right-slot auxiliary input; disabled for `rename` |
| `name` | Exact target name for `rename` only; empty string removes the name, and omission accepts any actual rename |

Only native operations that produce the corresponding effect advance the graph. `combine` accepts only valid two-input combinations already supported by the host, and the result must change beyond name, repair cost, and graph progress; it need not be the same item as the input. For `rename`, the right slot is empty and no auxiliary workpiece can be declared.

The left slot is logical input 0 (primary workpiece), and the right is input 1 (auxiliary). Both are validated separately; only left-slot progress is inherited. The primary and native result must each be exactly one item, and a progress-bearing auxiliary item must be consumed exactly once. Native result, level cost, and consumption are preserved. Non-finish steps use the native result as carrier, overriding the graph default; node-level `transitional_item` is disabled. Finish steps must still satisfy native operation conditions, then settle graph outputs without additionally granting the native result.

This supports the exact vanilla `AnvilMenu`, not standalone or derived menus automatically. See the [KubeJS guide](../guide/kubejs.md#anvil).

## Vanilla Crafter

The Crafter reuses the standard shaped/shapeless crafting format and logical roles above; it adds no fields. Each redstone rising edge rematches and processes one round; disabled slots retain vanilla behavior. Products and native remainders exit from the front. Items the container cannot accept are ejected as in vanilla and are not returned to the grid.

Unlike inventory and crafting-table processing, the Crafter delivers output through its front face rather than to the cursor or player inventory, and does not return remainders to the grid. The same recipe therefore uses different delivery and capacity rules on each machine.

## Vanilla Stonecutter

Standard `minecraft:stonecutting` nodes use complete native JSON (including `result`) or a strict `{type, recipe_id}` reference. This section describes the vanilla Stonecutter menu and accepts only standard recipe classes; the same node can also run in a Create Mechanical Saw. Logical input 0 is the sole primary workpiece. Native `result` is decoded but replaced by graph settlement.

The native list lets the player select an executable graph step; the selection is bound to the graph and node. Left- or right-click takes a complete round; Shift processes successive rounds subject to input and capacity. Every possible result must pass capacity checks before random selection and consumption. On menu close, inputs are returned as in vanilla.

See the [KubeJS guide](../guide/kubejs.md#stonecutter) for node syntax.

## Vanilla Smithing Table

Standard `minecraft:smithing_transform` uses full native JSON or a strict `{type, recipe_id}` reference. Logical input 0 is the base (physical slot 1), input 1 is the template (physical slot 0), and input 2 is the addition (physical slot 2). The graph entry/current workpiece replaces the base condition; the other two slots retain native conditions and may be declared as auxiliary workpieces.

Each round consumes one item from all three slots, validates primary and auxiliary workpieces separately, and inherits only primary progress. Native `result` is used only for decoding; actual graph output is delivered in full after capacity preflight. Ordinary smithing is unchanged.

Standard `minecraft:smithing_trim` uses the same three-slot roles but retains native base, template, and material conditions. A valid result preserves the armor and native components while advancing graph progress; if the same pattern and material are already present, nothing is consumed or advanced. Intermediate workpieces use the native trim result; the graph default does not override it, and node-level `transitional_item` is explicitly forbidden. Finish steps settle graph outputs and do not additionally grant armor. Unknown recipe subclasses and addon menus are not supported. See the [KubeJS guide](../guide/kubejs.md#smithing-table).

## Complete Native Recipe References (Preview and Validated Execution)

To reference an existing native recipe, set both serializer `type` and loaded `recipe_id` in the node's `recipe`:

```json
{
  "id": "filling",
  "start": true,
  "finish": true,
  "recipe": {
    "type": "create:filling",
    "recipe_id": "create:filling/chocolate_glazed_berries"
  }
}
```

This is a node fragment; it still belongs in a normal graph recipe's `nodes` and requires top-level ingredient, workpiece, edges, and settlement rules. The mod providing the target recipe must be installed. If the ID does not exist or the serializer does not match, the preview graph is not registered.

- The reference uses the complete native recipe object directly. **No primary workpiece or intermediate output is inserted, and no other inline fields are merged.** Existing ingredients, quantities, and recipe-specific fields remain unchanged.
- The graph node embeds the native JEI layout, preserving actual input/output slots, animations, and ingredient tooltips. Recipe/usage shortcuts on slots use the ingredient types registered with JEI, preserving their native type and amount.
- Referenced ingredients/products can be queried to find the preview graph. The graph footer still shows successful products from graph settlement, which may differ from the native product in the node.
- Only the adapted machines listed above can execute references. Create uses an independent native copy, routing item input 0 and result 0 through graph-workpiece rules while preserving other ingredients, conditions, and byproducts. Vanilla crafting follows inventory/crafting-table or Crafter delivery rules; the furnace family follows its single-input/single-output-slot rules; Stonecutters follow native list-selection and take-result behavior. Every path checks roles and capacity. Other machines can preview only and cannot advance graph progress this way.
- Other inline steps that are structurally valid and decode natively can also be previewed; execution availability is determined by independent machine checks. Intermediate items generated by shorthand are not indexed in JEI as external ingredients or obtainable products. Complete templates and references retain native ingredient indexing.

## Completion Conditions and Settlement

Graph-level and node-level `outputs` use the same rules. Omitted `start` / `finish` means no entry/exit restriction; omitted `requirements` or `{}` means marks and checkpoints are not checked. Graph-level rules and rules on the current finish participate together in settlement. Item leaves in `results` and `scrap` use **`item`/`count`/`weight`/`components`/`chance`**; the item key differs from **`id`** in inner native recipes. Default count, weight, and chance are 1; components default to empty. Each leaf's maximum count must fit in a normal item stack.

Two recursive group forms are available: `{"mode":"all","results":[...]}` executes every child; `{"mode":"weighted","results":[...]}` selects one child by local weights. Groups may also have `weight` and `chance`, and can be nested freely. Top-level entries from matching rules still form one weighted pool; after selection, the whole subtree runs without flattening nested pools. Leaf `chance` is rolled independently for each item (as in Create); group `chance` is rolled once for the whole group. Range is `0..1`; 0 never produces, and 1 always runs. Empty `all` is valid; empty `weighted` is invalid. A missed chance is not rerolled and does not enter the scrap pool. See the [KubeJS probability-composition guide](../guide/kubejs.md#weighted-selection-by-default-nested-groups-for-simultaneous-and-independent-probability-outputs) and [complete example](../../examples/kubejs/nested_probability_outputs.js).

Settlement may return multiple item stacks. A machine consumes inputs only after reserving capacity for the simultaneous output combination. The Spout still has a single-stack output API: it stops rather than truncating a workpiece whose settlement would produce multiple stacks. Non-settlement filling and single-stack output continue to work.

Processing completes first, then the item and mark grants update, this node's checkpoint is appended, `progress_actions` run in order, and settlement is attempted only at a finish node. Lenient failure retains the updated state and continues. Strict failure consumes the primary workpiece and draws from `scrap` (if omitted, there is no primary output). Byproducts are retained independently. A random node draws its next edge only if it remains unsettled and has no reroute action.

Workpiece progress is stored in the `graph_assembly:progress` component; intermediate workpieces have a maximum stack size of 1. Recipe reloads do not reset state or reroll. If the graph, current node, or locked edge becomes invalid, processing stops with a reason.

The saved `marks` map holds current mark quantities; `mark_grant_counts` records how many times each node has granted marks. Each application increments its node count once, even when several marks are granted. Clearing marks leaves that count unchanged. A nonempty `mark_amounts` map counts as an application even when all increments are zero; an empty map does not.

Mark counts and per-node mark-grant counts range from `0..2147483647`. A step that would overflow any count when granting marks is not offered for execution: item, fluid, and progress are retained, with a node/mark or mark-grant-count reason shown. Values are not saturated, wrapped, or automatically rerolled to another random branch. Once a `max_mark_applications` is reached, its count no longer increases and processing can continue normally. Other non-overflowing steps in a choice branch are unaffected.

Complete minimal recipe (unconditional, but settlement still occurs only at finish):

```json
{"type":"graph_assembly:assembly",
 "ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],
 "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
```

Each output rule can independently set `start` / `finish`; these flags are not affected by condition toggles. Turning off checks does not stop processing records, and other rules may still use them.

The following are `requirements` values for individual rules (referenced nodes must exist):

| Purpose | Configuration |
|---|---|
| Marks only, exact match | `{"marks":{"precision":3}}` |
| Marks only, minimum count | `{"mode":"partial","marks":{"precision":3}}` |
| Checkpoints only, exact full history | `{"checkpoints":["start","wash","finish"]}` |
| Checkpoints only, ordered inclusion | `{"mode":"partial","checkpoints":["start","finish"]}` |
| Both, AND | `{"mode":"partial","marks":{"precision":3},"checkpoints":["start","wash","finish"]}` |
| Local override | `{"mode":"partial","marks":{"mode":"exact","values":{"precision":3}},"checkpoints":{"mode":"exact","nodes":["start","finish"]}}` |
| Local unordered inclusion | `{"checkpoints":{"mode":"unordered","nodes":["wash","finish"]}}` |

The public mode defaults to `exact`: mark names and counts must match exactly, and the complete checkpoint sequence must match exactly. `partial` requires minimum mark counts and allows extra names and counts; checkpoints must contain the requested ordered subsequence and may include extra visits. Repeated requested nodes require the corresponding repeated visits. Automatically recorded Start/finish checkpoints **participate in matching and are not filtered out**.

### Quantity Ranges and Checkpoint Visit Counts

`requirements` supports two independent range conditions, also available in `workpiece_inputs[].requirements`:

```json
{
  "mark_ranges": {"quality": {"min": 2, "max": 4}},
  "checkpoint_counts": {
    "polish": {"max": 0},
    "join": {"min": 1, "max": 1}
  }
}
```

- `mark_ranges` checks cumulative counts by mark name; a missing mark counts as 0.
- `checkpoint_counts` counts occurrences of node IDs in the **complete checkpoint sequence**. Never recorded counts as 0. This is not the last three steps shown in a tooltip and is not a mark-grant count. Entry/exit nodes are recorded automatically; ordinary nodes count only with `checkpoint: true`. Loop visits can accumulate repeatedly.
- Each range must provide at least `min` or `max`; an empty object is invalid. Endpoints are integers in `0..2147483647`, with `min <= max`; **both endpoints are inclusive**.
- Omitted `min` is equivalent to 0; omitted `max` is equivalent to `2147483647`. For marks, `{ "max": 0 }` requires zero. For checkpoints, it requires no recorded visits to that node.
- Checkpoint keys and nodes referenced by `current_nodes` must exist. A normal node does not appear in `checkpoint_counts` merely because it was processed; set `checkpoint` explicitly when it needs to be counted.

When `marks`, `checkpoints`, `mark_ranges`, and `checkpoint_counts` are all configured, they are combined with AND. Range conditions check only listed names; they do not require unlisted marks or checkpoints to be empty. They do not change existing exact/partial/unordered behavior and are not affected by the public `mode`. Omitting a condition type skips that type; an empty range map `{}` adds no restriction, but an individual range object cannot be `{}`.

Local checkpoint modes are `exact`, `partial`, and `unordered`; `partial` means ordered inclusion. `unordered` retains containsAll behavior and ignores multiplicity; it cannot be used as the public mode or for marks. Complete objects must provide `mode` and `values` / `nodes`. Complete mark form is recognized by an object-valued `values`; `{"mode":2,"values":3}` is still shorthand for two ordinary marks.

**Explicit empty conditions do not disable checks:** exact marks `{}` requires no marks; exact checkpoints `[]` requires empty history (usually impossible because finish is recorded automatically). Partial `{}` / `[]` places no restriction on actual records but still appears as enabled. Omit a condition to disable it; do not use an empty collection for that purpose.

All matching graph-level and current-finish rules contribute to one weighted pool, with a single draw. Rule order and exact matches have no priority; rules do not each produce an item. Duplicate products add their weights, so a probability calculated within one rule is not the final probability.

JEI distinguishes disabled, exact match, minimum satisfied, ordered inclusion, and unordered inclusion; explicit empty requirements are shown separately. Page tooltips explain the public default and effective mode after local overrides. Product, scrap, and item/fluid input indices remain independent.

The JEI native Info page shows the conditions above for graph-level/node-level rules and associates them with settlement outputs; the graph canvas no longer has a “Settlement Rules” button at the top. Successful outputs and strict-failure scrap are shown separately. `scrap` from lenient failure is not an obtainable scrap output.

Current datapack examples: [`examples/datapack/branching.json`](../../examples/datapack/branching.json) and [`examples/datapack/item-first.json`](../../examples/datapack/item-first.json). Do not enable examples that use the same entry at the same time.

## Assembly-Type Discovery and Diagnostics

For ordinary nodes, `recipe.type` is a native **serializer ID** and may differ from `RecipeType`. See [Compatibility Reference](compatibility.md) for machine execution and conditions, including the vanilla Brewing Stand and Anvil operations. Other registered serializers are first tried through native decoding, then reported with interface declarations and machine-adapter status. Decode failures and unregistered serializers are reported separately.

At startup, datapack reload, and the admin command `/graph_assembly compatibility`, the mod checks currently loaded standalone recipes and steps inside `create:sequenced_assembly`, across all namespaces. Reports include sample sources. A type with no loaded instance is not assumed capable based on its registry entry or reflection. Finding a third-party interface alone does not enable execution; explicit codec, machine consumption, progress transfer, and output-capacity adapters are still required.

The component stores complete JSON state internally. NBT and network data use Unicode-safe string chunks of at most 8192 UTF-16 units each, preserving complete checkpoint history without truncation; legacy single-string NBT progress is not read. Datapack authors do not need to edit these internal fields. Unreleased development data is not guaranteed to survive format changes; the mod does not rewrite saves or clear old workpieces automatically.

## KubeJS

The complete tutorial, copyable scripts, and troubleshooting for recipe authors are in the [KubeJS guide](../guide/kubejs.md).

When KubeJS is installed, the plugin automatically provides the global `GraphAssembly`; without KubeJS, the entire optional package is not loaded. CreateJS is not required.

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({item: 'minecraft:iron_ingot'})
        .node('s', {type: 'create:pressing'}, {start: true})
        .node('f', {type: 'create:cutting', processing_time: 40}, {finish: true})
        .edge('s', 'f')
        .output({results: [{item: 'minecraft:gold_ingot'}]});
    event.custom(graph.build()).id('example:graph');
});
```

Builder methods: `create(transitionalItem)`, `ingredient(value)`, `node(id, recipe[, options])`, `nativeNode(id, recipe[, options])`, `edge(from,to[, options])`, `weightedEdge(from,to,weight)`, `bidirectionalEdge(from,to)`, `output(ruleOrItem)`, and `build()`. Edge option example: `.edge('a','b',{weight:1,waypoints:[[10,20],[30,40]]})`; explicit `from/to` arguments take precedence.

`node` accepts the existing shorthand or a KubeJS recipe object created during the current event. For the latter, it saves a complete snapshot at call time and does not register it independently as an ordinary recipe. `nativeNode` accepts complete native JSON; both complete entry points set `recipe_mode: "native"`. Entry ingredients may be item IDs, tags, or Ingredients. Outputs may be item strings, ItemStacks, or existing rules, preserving count and components. Array shorthand is an equal-weight choice pool; use a `mode: "all"` group to produce multiple entries at once.

KubeJS `keepIngredient`, `damageIngredient`, `modifyResult`, and custom ingredient actions are not integrated and report errors when embedded. Native Create `keep_held_item` remains available. See [native recipe and ingredient examples](../../examples/kubejs/native_recipes.js).

The builder produces the same JSON used by datapacks. Configure complex nodes, layout, outputs, and checkpoints in their corresponding JSON fields. Put `workpiece_inputs` in the third `.node(...)` argument and ranges in the relevant `requirements`; the fluent helpers below cover common settings. `build()` uses the same `GraphCodec` validator, with no separate scripting defaults. You can also pass full JSON directly to `event.custom`. Complete scripts: [basic graph](../../examples/kubejs/graph_assembly.js), [conditional settlement](../../examples/kubejs/item_first.js), [dual workpiece](../../examples/kubejs/multi_workpiece.js), and [built-in graph filter replacement example](../../examples/kubejs/filter_crafting.js).

## Real Create sequenced assembly

Keep `create:sequenced_assembly` as the outer recipe and mix native Create steps with `graph_assembly:sequence_step` entries in `sequence`. See the complete [datapack](../../examples/datapack/native-sequence.json) and [KubeJS event.custom](../../examples/kubejs/native_sequence.js) examples; neither CreateJS nor the graph Builder is required.

Each wrapper has `step: {recipe: {...}}`, with optional existing `recipe_mode` and cooking-specific `campfire`. The operation uses the same validated template/reference format as graph nodes, including native required fields. Graph-only IDs, edges, marks/checkpoints, settlement rules and multi-workpiece roles are not accepted. Only already-supported device families are admitted; wrappers do not authorize arbitrary third-party serializers or recursive sequences. Invalid embedded definitions/references disable the parent's wrapped steps with a diagnostic.

The parent owns its native `ingredient`, `transitional_item`, `loops`, weighted `results` and Create progress component. No extra graph recipe or graph progress is created. The example runs furnace → native press twice. Outer transitional/result item components are preserved; inner native computation still validates machine operations, costs, ingredients and remainders, but its primary reward is replaced by the parent's result. Dynamic repair/dye/smithing components are not automatically merged into that result. Create processing byproducts follow existing host rules.

Preview and capacity checks do not make random draws. Every possible final output must fit before inputs are consumed; single-item slots, such as brewing bottle slots, require count 1. Reloading invalidates old bindings and matches saved native workpieces against the current parent recipe with the same ID.

Wrapped steps show **only their target block** at the native JEI step position, with no animation or extra ingredient slots. The hint reads the current step on each draw because Create caches subcategories by serializer. Ordinary native steps keep Create's animations. A wrapper describes a step inside a sequence; it cannot run as a standalone recipe or automatically convert a graph into a sequence. All [device restrictions](compatibility.md) still apply.

### Additional fluent APIs

Graph builders expose `register(event,id)`, `finishOutput(nodeId,results[,requirements])`, `scrap(nodeId,results)`, `requirements(nodeId,requirements)`, `conditionalEdge(from,to,requirements[,weight])`, `branchMode(nodeId,mode)`, and `progressAction(nodeId,action)`. Add nodes before configuring them by ID. Exit/scrap pools append rather than replace. `requirements()` configures primary role 0 while retaining its existing `current_nodes` and auxiliary roles.

`GraphAssembly.sequence(transitionalItem)` provides `ingredient`, `step`, `loops`, `output`, `build`, and `register`; step options are only `recipe_mode` and `campfire`. See the [KubeJS guide](../guide/kubejs.md) for scripts.
