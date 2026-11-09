# Execution Compatibility Reference

English | [简体中文](compatibility.zh_cn.md)

Graph nodes can use the machines and recipe types listed below, subject to their input and output rules. Addon recipes using a supported native type follow the same rules; custom machines need a separate integration. JEI reuses native layouts and can preview some recipes whose machines cannot execute graph steps.

## Graph Execution

| Recipe type | Execution support |
|---|---|
| `create:pressing` | Mechanical Press; no auxiliary inputs, up to 1 byproduct |
| `create:cutting` | Mechanical Saw; no auxiliary inputs, up to 3 byproducts; `processing_time` is decoded by the native codec with no additional positive-value constraint |
| `create:deploying` | Deployer; exactly 1 auxiliary item input, up to 3 byproducts; supports `keep_held_item` |
| `create:filling` | Spout; exactly 1 fluid input; no byproducts |
| `create:milling` | Millstone; no auxiliary inputs, up to 3 byproducts, and `processing_time > 0` |
| `create:mixing` | Basin mixing; up to 63 auxiliary items, 2 auxiliary fluids, and 3 byproducts; at most 64 total item inputs and 4 outputs; heat requirements are preserved |
| `create:compacting` | Basin compacting; up to 63 auxiliary items, 2 auxiliary fluids, and 3 byproducts; at most 64 total item inputs and 4 outputs; heat requirements are preserved |
| `create:crushing` | Crushing Wheels; no auxiliary inputs, up to 6 byproducts, native `processing_time` (including 0); also accepts milling steps |
| `create:sandpaper_polishing` | Handheld sandpaper or a Deployer holding sandpaper; no recipe auxiliaries/byproducts; native tool durability is consumed |
| `create:splashing` | Washing airflow; no auxiliary inputs, up to 11 byproducts; native exposure timers and completion locks remain |
| `create:haunting` | Haunting airflow; no auxiliary inputs, up to 11 byproducts; native exposure timers and completion locks remain |
| `create:emptying` | Item Drain; no auxiliary inputs, exactly one nonempty fluid output; item delivery must fit one stack |

The auxiliary-input and byproduct limits in this table exclude one primary input and one primary output. Step mode inserts the primary workpiece automatically; native templates use their existing item input 0/output 0. Both paths use the same native codec and machine validation.

The Mechanical Saw reserves space for the worst-case batch output; if capacity is insufficient, it keeps the input and does not process. The Millstone keeps its input until an output slot is available. Only the Item Drain accepts native fluid output; fluids never carry graph progress. The drain rechecks tank capacity and fluid identity before consuming inputs; if delivery is blocked, inputs are kept and no random draw is made. Crushing Wheels process the batch portion that fits worst-case output capacity, sending the remaining input unchanged through the native exit without discarding workpieces. See [Recipe Format](recipe-format.md) and the [KubeJS guide](../guide/kubejs.md) for fields and limits.

### Vanilla Crafting

`minecraft:crafting_shaped` and `minecraft:crafting_shapeless` are supported in vanilla 2×2 inventory and 3×3 crafting-table menus. Patterns, mirroring, ingredient conditions, and container-return rules are preserved. Multiple workpieces are validated separately; only the primary workpiece's progress is inherited by the result. Recipes use full native JSON or a reference, not Create's auxiliary-input shorthand.

Inputs are revalidated on click. Insufficient space consumes nothing and makes no random selection. Left- or right-click takes one complete round: the first stack goes to the cursor and the rest to inventory. Shift crafts repeatedly, rechecking every round. Number-key/offhand swaps only accept an empty target slot. The graph preview cannot be middle-click copied or dropped directly with Q; after taking it into inventory, it can be handled normally.

Other special crafting recipes and third-party crafting menus are not enabled for graph execution, except for the dyeing and repair recipes described below. Sharing a serializer does not mean custom recipe subclasses are supported.

### Vanilla Dyeing and Repair

The exact vanilla `ArmorDyeRecipe` and `RepairItemRecipe` are supported in inventory, crafting-table, and vanilla crafter menus. The item being dyed is the sole primary workpiece; dye slots become auxiliary inputs in left-to-right, top-to-bottom order. For repair, the first nonempty slot is primary and the second auxiliary; they are validated separately, and only the primary progress is inherited. Non-finish steps use the native result as the carrier; finish steps use graph settlement. Node-level `transitional_item` is not allowed. Create special graph steps and other special crafting recipes are not supported.

### Vanilla Brewing Stand

`graph_assembly:brewing` supports two primary-workpiece modes: each bottle keeps its own progress, or a shared ingredient distributes progress to every eligible bottle. Each bottle advances and settles independently, while the shared ingredient is consumed only once. Progress-bearing auxiliary workpieces must be declared and validated separately.

Actual effects come from native `PotionBrewing`. Potion components, fuel, duration, events, and ingredient remainders follow native behavior. Each bottle slot accepts an empty graph result or one item, and the whole batch is checked before inputs are consumed. Ordinary brewing uses its original call path; graph workpieces cannot fall back to ordinary processing.

Integration covers only the exact vanilla `BrewingStandBlockEntity` and Brewing Stand block. Rules registered in the native query need no mod-name distinction; this does not enable custom machines, Create fluid processing, or addon Brewing Stand subclasses. See [Brewing Operation](recipe-format.md#brewing-stand-operation) for the format.

### Vanilla Anvil

`graph_assembly:anvil` supports repair, enchantment enhancement, explicit renaming, and valid two-input combinations already supported by the host. The left slot is primary and the right slot auxiliary; they are validated separately, and only left-slot progress is inherited. Results, costs, and consumption follow the full native operation. Intermediate steps retain the native result; finish steps deliver only graph settlement outputs.

The adapter integrates with vanilla `AnvilMenu` calculation and take-result flows, and accepts valid results returned by upstream extensions in that flow. AnvilCraft standalone two-/four-/eight-input menus, Royal Anvils, and world-falling-anvil recipes are outside this support.

### Vanilla Crafter

The vanilla Crafter uses the standard shaped/shapeless recipe formats, primary/auxiliary workpiece validation, and graph previews described above. Each redstone rising edge processes one round, rebinding inputs and the current recipe at execution time. Disabled slots and hopper distribution follow vanilla behavior.

Outputs and native remainders enter the container from the front; items that do not fit are ejected as in vanilla. Remainders are not returned to the input grid. Ordinary recipes without graph progress remain supported. Other special recipes, custom recipe subclasses, and addon Crafters do not gain this adapter automatically, except for the dyeing and repair recipes described above.

### Vanilla Furnace Family

| Recipe type | Vanilla machine |
|---|---|
| `minecraft:smelting` | Furnace |
| `minecraft:smoking` | Smoker |
| `minecraft:blasting` | Blast Furnace |

Use a full native recipe or a reference. There is one primary workpiece input; fuel is not an auxiliary workpiece. Every possible output combination must fit completely in one output slot. If multiple items from a round cannot merge, processing stops; outputs are neither truncated nor discarded. Fuel duration, cooldown, and remainders follow vanilla rules. Experience from successful rounds can be collected by taking the output or breaking the machine.

Only the listed vanilla machines and exact standard recipe classes are integrated; addon custom furnaces are not.

### Vanilla Campfire

`minecraft:campfire_cooking` works on vanilla Campfires and Soul Campfires, reusing the cooking query and graph settlement. The node condition `campfire` can require `normal` or `soul`; omitted or set to `any`, either fire is accepted. Both placement and processing check the current fire type without changing ordinary campfire recipes. Each of the four slots has its own timer and processes one primary workpiece; the slots do not form a multi-input recipe. Vanilla placement, cooling while extinguished, and item drops are preserved. Campfires do not consume fuel items, grant furnace experience, or provide vanilla hopper input. Outputs drop as the complete graph result and may include multiple items. Special recipe subclasses and addon Campfires are not automatically supported.

### Vanilla Stonecutter

Standard `minecraft:stonecutting` recipes are supported in the vanilla Stonecutter menu. Input 0 is the sole primary workpiece. The native recipe list lets the player select an executable graph step; if workpiece progress or the recipe definition changes, the step must be selected again.

Left- or right-click takes one complete round: the first stack goes to the cursor and the rest to inventory. Shift processes successive rounds while inputs and capacity allow. On menu close, inputs are returned as in vanilla; previews do not drop as products. There is no item-inventory block entity or hopper processing path. Other menus and custom recipe subclasses do not inherit this adapter.

### Vanilla Smithing Table

The exact standard `minecraft:smithing_transform` is supported with full native JSON or a recipe reference. The primary workpiece is the center input (the base), not the template on the left:

| Logical input | Native slot | Role |
|---|---|---|
| 0 | Center, physical slot 1 | Primary workpiece / base |
| 1 | Left, physical slot 0 | Template; may be declared as an auxiliary workpiece |
| 2 | Right, physical slot 2 | Addition; may be declared as an auxiliary workpiece |

Each round consumes one item from all three slots. Auxiliary workpieces are validated against the graph and route separately; only primary progress is inherited. Native `result` is a decoding template and does not additionally grant the upgrade result. Left- or right-click takes a full round, with the first stack to the cursor and the rest to inventory; Shift processes successive rounds subject to inputs and capacity. On menu close, inputs are returned as in vanilla; previews do not drop.

Standard `minecraft:smithing_trim` can also execute graph steps. Native base conditions are preserved, and the valid trimmed armor and its native components carry the primary workpiece's updated progress. If it already has the same pattern and material, nothing is consumed or advanced. The graph's default intermediate item does not replace the armor, and trim nodes must not set `transitional_item`; finish steps use graph settlement.

Ordinary smithing retains vanilla behavior. Other dynamic recipes, special subclasses, and addon menus are not supported by this adapter.

### Create's Vanilla Replacement Machines

The same operation does not need to be rewritten as a Create recipe. Machines accept the following standard vanilla graph nodes under native conditions; these recipes are not copied into the global recipe table:

| Vanilla source | Create machine | Conditions |
|---|---|---|
| Standard shapeless crafting | Mixer + Basin | Toggle enabled; more than 1 ingredient; not currently compressible |
| Standard crafting with 4 or 9 equivalent ingredients | Mechanical Press + Basin | Toggle enabled; call upstream `canCompress` with original ingredients, then replace the graph primary workpiece |
| `minecraft:stonecutting` | Mechanical Saw | Toggle enabled; output filtering still applies and filters the current graph workpiece/product, not the template result |
| Standard shaped/shapeless crafting | Mechanical Crafter | Toggle enabled; preserves grid matching, primary/auxiliary workpiece binding, and native output handling |
| `minecraft:smoking` | Fan Smoking | Native medium and processing time |
| `minecraft:smelting`, otherwise `minecraft:blasting` | Fan Smelting | Smelting takes priority; native medium, timing, and food checks are preserved |

Each machine follows upstream automation-exclusion rules. Referenced recipes are identified by their recipe ID; inline nodes use the graph ID. The Mechanical Crafter outputs the complete graph result and native container remainders, without additionally granting the template result.

Each fan operation advances only the current step. Replacing the input or reloading does not reuse an old task's countdown, and the completed product retains the lock from that processing operation. If smoking and smelting recipes for the same input have the same result, Create's food rules still determine the path. Workpieces with graph progress do not fall back to ordinary burning. `campfire_cooking` is not a smoking source.

This integration excludes special crafting, potion mixing, fan washing/soul-sandblasting, and addon custom machines.

## Protection and Preview Boundaries

- Fan processing types without an adapter still only protect existing graph workpieces; they do not execute graph steps.
- During native `RecipeManager` matching queries, any nonempty input with graph progress blocks fallback to an ordinary recipe, including shapeless crafting and cached queries. Graph processing in inventory/crafting-table menus, the Crafter, furnace family, Campfire, Stonecutter, and Smithing Table uses separate commit paths and does not remove this protection. Direct calls to ordinary recipe methods, standalone cached outputs, and machines that bypass the query are outside this protection layer.
- Full native recipe references can display multi-input recipes from more mods in JEI. Only the machine types adapted above can execute them, and they must still pass workpiece-role, resource, and capacity checks.
- Addon recipes using supported native Create types can execute; addon custom processing types do not gain graph execution. Generic JEI previews require access to the native layout, which is not available for every category.
- The initial release does not include dedicated machine adapters for addons such as CCB and Fantasizing. Their ordinary processing may consume or overwrite workpiece progress.

Administrators can run `/graph_assembly compatibility` to see discovered recipes, adapter status, and the reasons nodes are disabled.

### Addons Reading Vanilla Recipes

An addon that reads vanilla recipes does not necessarily use the graph execution path. Ratatouille ovens reject progress-bearing workpieces through vanilla queries but cannot discover a smoking step defined only in a graph. Vintage Improvements' Mechanical Vibrator uses a decomposition cache that turns a progress-bearing iron block into 9 ordinary iron ingots, losing its progress. That bypass remains unfixed.
