# Graph Assembly: KubeJS Guide

English | [简体中文](kubejs.zh_cn.md)

This guide is for modpack and recipe authors using **Graph Assembly 0.1.2 and Minecraft 1.21.1**. Workpieces and settlement outputs must be items; supported machines can consume fluids as auxiliary ingredients.

Graph Assembly represents each processing operation as a node and each allowed next operation as a directed edge. As a workpiece moves between machines, it carries its current node, marks, and checkpoint history. At an exit, it is settled according to the configured rules.

## Contents

1. [Installation and script location](#1-installation-and-script-location)
2. [Build your first graph: press, then cut](#2-build-your-first-graph-press-then-cut)
3. [Inputs, workpieces, auxiliary ingredients, and outputs](#3-inputs-workpieces-auxiliary-ingredients-and-outputs)
4. [A complete processing chain and machine limits](#4-a-complete-processing-chain-and-machine-limits)
5. [Builder API](#5-builder-api)
6. [Node options](#6-node-options)
7. [Choice branches](#7-choice-branches)
8. [Random branches, loops, and mark-grant limits](#8-random-branches-loops-and-mark-grant-limits)
9. [Completion requirements and output settlement](#9-completion-requirements-and-output-settlement)
10. [Addon execution adapters and full native recipe previews](#10-addon-execution-adapters-and-full-native-recipe-previews)
11. [Direct JSON, editing, and removal](#11-direct-json-editing-and-removal)
12. [Reloading, troubleshooting, and release checks](#12-reloading-troubleshooting-and-release-checks)
13. [Built-in graph filter: dual-workpiece tutorial recipe](#13-built-in-graph-filter-two-workpiece-tutorial-recipe)

## 1. Installation and script location

### Versions and dependencies

| Component | Version / purpose in this guide |
|---|---|
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | Development verification baseline 21.1.234 |
| Create | 6.0.10, development build 6.0.10-281; install its required dependencies as well |
| Graph Assembly | 0.1.2 |
| KubeJS NeoForge | Development verification baseline `2101.7.2-build.379` |
| Rhino | Corresponding baseline `2101.2.7-build.81` |
| KubeJS Create (optional) | `2101.3.1-build.18`; provides `event.recipes.create.*`, not required by the basic examples |
| JEI (optional) | Graph previews support `19.39.0.368`–`19.70.0.469` (inclusive) |

KubeJS is required for the scripts in this guide; it is not required when authoring graph recipes with datapacks alone. The `GraphAssembly` global object is provided by this mod's KubeJS plugin.

Save recipe scripts as UTF-8 files in the instance or server directory, for example:

```text
kubejs/
└─ server_scripts/
   └─ graph_assembly_recipes.js
```

Recipes must be registered inside `ServerEvents.recipes(function (event) { ... })`. Run `/reload` after editing. In multiplayer, the server loads recipe scripts; players do not need to register them again in their own `server_scripts` directory. Install Graph Assembly, Create, and their required dependencies on both client and server. JEI is for client-side display.

Each complete example has its own recipe ID. Enable one at a time, check the result, then combine them as needed. Examples with the same input and entry operation must not be enabled together.

## 2. Build your first graph: press, then cut

Put this complete script in `kubejs/server_scripts/graph_assembly_recipes.js`:

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:iron_ingot' })
        .node('press', {
            type: 'create:pressing'
        }, { start: true })
        .node('cut', {
            type: 'create:cutting',
            processing_time: 40
        }, { finish: true })
        .edge('press', 'cut')
        .output({
            results: [{ item: 'minecraft:gold_ingot', count: 1 }]
        });

    event.custom(graph.build()).id('my_pack:graph/basic');
});
```

Processing steps:

1. Feed an iron ingot into a Mechanical Press. The entry node `press` runs and produces an iron nugget workpiece carrying graph progress.
2. Feed that workpiece into a Mechanical Saw to run `cut`.
3. `cut` is an exit, and the output rule has no conditions, so settlement produces one gold ingot.

An ordinary iron nugget without graph progress cannot start at the second step. `minecraft:iron_nugget` is the item ID of the progress carrier; `press` and `cut` are node IDs within this graph.

`my_pack:graph/basic` is the recipe ID for the entire graph. Replace it with your preferred namespace and path. Keep recipe IDs and node IDs stable where possible so existing workpieces can continue to work and maintenance stays simpler. ;-)

## 3. Inputs, workpieces, auxiliary ingredients, and outputs

| Concept | Defined in | Meaning |
|---|---|---|
| Entry input | `.ingredient(...)`, or `ingredient` on the entry node | An item that has not entered the graph yet |
| Default workpiece | `GraphAssembly.create('item_id')` | The item that carries progress after the first operation |
| Node workpiece variant | Node option `transitional_item` | Replaces the workpiece item after this step succeeds |
| Auxiliary ingredients | Node `recipe.ingredients`, or the corresponding native resource field | Ordinary auxiliary items, role-declared auxiliary workpieces, and resources such as native fluids for filling or basin recipes |
| Byproducts | Node `recipe.results` | Additional items produced by this operation |
| Final outputs | `.output({ results: [...] })` or node option `outputs` | Produced when settlement requirements are met at an exit |
| Scrap | Exit node option `scrap` | Drawn when a strict exit has no matching output rule |

### Create shorthand steps: omit the primary workpiece

For the supported Create shorthand types in `.node(id, rawJson, options)` described below, the system inserts the primary input and primary output automatically. Put **auxiliary ingredients only** in `ingredients` and **byproducts only** in `results`; do not repeat the current workpiece. Each additional field is interpreted by its native codec. See the next section for full native templates. Workbench, furnace, and other nodes are configured in their respective subsections.

The entry `.ingredient(...)` and node option `ingredient` accept an item ID, tag string, `Ingredient.of(...)`, or native Ingredient JSON, for example `'minecraft:iron_ingot'`, `'#c:ingots/iron'`, or `{ tag: 'c:ingots/iron' }`. Auxiliary ingredients in legacy `recipe` JSON must still use native format. Shorthand counts and values such as `Fluid.of(...)` are parsed by the upstream recipe builder.

For simple settlement, write `.output('2x minecraft:gold_ingot')` or `.output(Item.of(...))`; counts and item components are preserved. For conditions, weights, probabilities, or simultaneous outputs, use the settlement rules and nested groups below. Item entries in node `outputs` and `scrap` also accept these shorthands.

Do not mix the two output field formats:

| Location | Format | Probability semantics |
|---|---|---|
| Operation `recipe.results` | `{ "id": "minecraft:flint", "count": 1, "chance": 0.25 }` | Create native byproduct probability |
| Graph settlement `results` / `scrap` | `{ "item": "minecraft:gold_ingot", "count": 1, "weight": 3, "chance": 0.5 }` | The outer level selects an entry by weight, then checks the item's chance independently; nested groups are also supported |

### Optional: reuse an upstream recipe builder

`node()` can embed an upstream recipe object created in the current `ServerEvents.recipes` event. Use KubeJS built-in methods for ordinary crafting, furnaces, stonecutting, smithing, and similar recipes. The Create methods below also require KubeJS Create:

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:iron_ingot')
        .node('press', event.recipes.create.pressing(
            'minecraft:iron_nugget', 'minecraft:iron_ingot'), { start: true })
        .node('cut', event.recipes.create.cutting(
            'minecraft:iron_nugget', 'minecraft:iron_nugget').processingTime(40), { finish: true })
        .edge('press', 'cut')
        .output('minecraft:gold_ingot');
    event.custom(graph.build()).id('my_pack:graph/native_builder');
});
```

This is a complete recipe: its inputs include the primary item, and its outputs include the primary workpiece. During processing, graph rules replace that item position while keeping auxiliary ingredients, byproducts, fluids, and native parameters. Graph settlement still determines the final outputs.

Embedding the node saves a recipe snapshot without registering a standalone recipe. Later changes to the upstream builder do not affect the node, so set chained parameters before `.node(...)`.

Use `.nativeNode(id, json, options)` for full native JSON; it sets `recipe_mode: 'native'` automatically. This mode uses the complete definition without inserting a primary input. Create logical item input 0 is still replaced by the current workpiece. Omitting the mode or using `'step'` selects shorthand mode; raw JSON shorthand is valid only for `step`. Reference existing ordinary recipes with `{ type, recipe_id }`; do not pass objects from `event.forEachRecipe` into a node.

Native parameters such as `.processingTime()`, `.heated()`, `.superheated()`, `.keepHeldItem()`, `.xp()`, and `.cookingTime()` can be reused. Ordinary `shaped` / `shapeless` recipes are converted to vanilla crafting. Special behaviors such as `keepIngredient`, `damageIngredient`, `modifyResult`, `noMirror`, and `noShrink` are not supported in this release and produce an explicit error. `CreateItem.of(..., chance)` is for native operation byproducts, not graph settlement shorthand.

Full templates do not expand execution support; unsupported machines remain preview-only. See [native_recipes.js](../../examples/kubejs/native_recipes.js) for complete examples that require only KubeJS.

### Optional: register a dedicated workpiece

A modpack can use a dedicated item to distinguish ordinary materials from workpieces. Put the following in a **separate file**, `kubejs/startup_scripts/graph_assembly_items.js`, then restart the game or server after editing:

```javascript
StartupEvents.registry('item', function (event) {
    event.create('graph_workpiece');
});
```

Then change `GraphAssembly.create('minecraft:iron_nugget')` in the recipe to `GraphAssembly.create('kubejs:graph_workpiece')`. Registration only makes the item exist; configure its name and texture separately. Once an item carries graph progress, Graph Assembly limits its workpiece stack size to 1.

## 4. A complete processing chain and machine limits

This example includes pressing, deploying, filling, cutting, and milling, and demonstrates auxiliary ingredients, byproducts, and marks:

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:copper_ingot' })
        .node('press', {
            type: 'create:pressing'
        }, { start: true })
        .node('install', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:iron_nugget' }],
            keep_held_item: false
        }, { mark_amounts: { precision: 1 } })
        .node('wash', {
            type: 'create:filling',
            ingredients: [{
                type: 'neoforge:single',
                fluid: 'minecraft:water',
                amount: 250
            }]
        }, { checkpoint: true })
        .node('cut', {
            type: 'create:cutting',
            processing_time: 40,
            results: [{ id: 'minecraft:flint', count: 1, chance: 0.25 }]
        }, { mark_amounts: { precision: 1 } })
        .node('mill', {
            type: 'create:milling',
            processing_time: 100
        }, { finish: true })
        .edge('press', 'install')
        .edge('install', 'wash')
        .edge('wash', 'cut')
        .edge('cut', 'mill')
        .output({
            requirements: {
                mode: 'partial',
                marks: { precision: 2 },
                checkpoints: ['press', 'wash', 'mill']
            },
            results: [{ item: 'minecraft:gold_ingot', count: 2 }]
        });

    event.custom(graph.build()).id('my_pack:graph/five_steps');
});
```

`wash` is a custom node name; the actual operation is **filling with a Spout**, not washing with a fan. Filling consumes 250 mB of water. `processing_time` is a native recipe processing-time parameter.

### Currently executable types

The following are Create processing types. The counts **exclude the automatically inserted primary workpiece and primary output**:

| `recipe.type` | Machine | Auxiliary item ingredients | Auxiliary fluids | Extra item output limit |
|---|---|---:|---:|---:|
| `create:pressing` | Mechanical Press | 0 | 0 | 1 |
| `create:cutting` | Mechanical Saw | 0 | 0 | 3 |
| `create:deploying` | Deployer | 1 | 0 | 3 |
| `create:filling` | Spout | 0 | 1 | 0 |
| `create:milling` | Millstone | 0 | 0 | 3 |
| `create:mixing` | Mechanical Mixer and Basin | Up to 63 | Up to 2 | 3 |
| `create:compacting` | Basin compacting | Up to 63 | Up to 2 | 3 |
| `create:crushing` | Crushing Wheels | 0 | 0 | 6 |
| `create:sandpaper_polishing` | Handheld sandpaper / sandpaper Deployer | 0 | 0 | 0 |
| `create:splashing` | Washing airflow | 0 | 0 | 11 |
| `create:haunting` | Haunting airflow | 0 | 0 | 11 |
| `create:emptying` | Item Drain | 0 | 0 | 0 (plus one fluid output) |

- The Deployer supports `keep_held_item`; `true` keeps the held auxiliary item. Tool behavior otherwise follows Create.
- Milling requires `processing_time` greater than 0; crushing keeps native timing and accepts omitted or zero duration. Crushing Wheels also accept milling steps; oversized batches process only the portion that fits and deliver untouched excess input without dropping workpieces.
- Filling cannot have byproducts. Only the Item Drain accepts native fluid output; fluid workpieces and graph settlement remain unsupported. Sandpaper and draining settlement must fit one item stack. An incompatible or full drain tank consumes nothing and makes no random draw.
- Mixing / basin compacting support at most **64 native item inputs (including the primary item, so at most 63 auxiliary Item Ingredients)**, **4 item outputs (including the primary output, so at most 3 byproducts)**, and 2 auxiliary fluid inputs. These are recipe capacities, not 64 physical Basin inventory slots; actual matching is also limited by inventory and output space.
- Basin inline `ingredients` may mix items and fluids. Preserve native `heat_requirement`, such as `'heated'` or `'superheated'`. When heat is required, the corresponding heat source must actually be present; graph recipes do not bypass this requirement.
- A recipe that exceeds machine capacity disables execution of the entire graph; excess ingredients are not silently ignored.
- Machines check output space. Large Mechanical Saw jobs may wait if the worst-case output exceeds capacity; split the input upstream.

Addon recipes using the native types listed in this section follow the same validation rules. Custom processing types need their own execution adapter; a graph preview alone does not make them executable. The table below lists Create replacements for standard vanilla recipe nodes.

### Create substitute machines

The same standard vanilla recipe node can be processed by the corresponding Create machine; no duplicate node or recipe format is needed:

| Vanilla node type | Available Create machine |
|---|---|
| `minecraft:crafting_shapeless` | Mechanical Mixer + Basin: more than 1 ingredient, and not a currently supported compacting recipe |
| Standard crafting with 4 or 9 equivalent ingredients | Mechanical Press + Basin: compacting eligibility is determined from the original ingredients, then the primary workpiece is substituted |
| `minecraft:stonecutting` | Mechanical Saw |
| Standard shaped / shapeless crafting | Mechanical Crafter |
| `minecraft:smoking` | Fan smoking |
| `minecraft:smelting` / `minecraft:blasting` | Fan processing: checks smelting first, then blasting |

The Saw, Basin, and Mechanical Crafter obey their respective Create config toggles. All paths obey the corresponding machine's automation-exclusion rules, and referenced recipes are checked by their original IDs. Fan processing follows native media, timing, and food checks; one operation does not skip multiple graph nodes. Campfire recipes are not a source for fan smoking.

Native `result` must still be present; it is a decoding template and is not granted in addition to graph outputs. See [execution compatibility](../reference/compatibility.md#creates-vanilla-replacement-machines) for the full conditions.

### Inventory and crafting-table recipes

Standard `minecraft:crafting_shaped` and `minecraft:crafting_shapeless` recipes can be graph nodes, subject to the 2×2 inventory and 3×3 crafting-table size limits. Use full native recipe JSON or reference an existing recipe with `{ type, recipe_id }`; the primary ingredient is **not appended automatically**. The primary item occupies logical input 0 and is replaced by the graph entry ingredient or current workpiece.

- Shaped crafting numbers non-empty cells left to right, then top to bottom, in the unmirrored pattern. Repeated characters are numbered per cell. Mirroring and translation do not change role indices.
- Shapeless crafting numbers inputs in the `ingredients` array. Each non-empty input cell consumes 1 item; a stack in one cell cannot stand in for multiple recipe cells.
- Auxiliary ingredients are ordinary items by default. To use an auxiliary workpiece with progress, declare it in `workpiece_inputs`; it must belong to the same graph and be legally usable at this node. Only the primary workpiece's progress is inherited.
- Native `result` must remain so the native codec can decode the recipe. The graph workpiece and exit settlement rules determine the actual result.

For example, this node fragment requires the previous-step workpiece and a stick:

```javascript
.node('assemble', {
    type: 'minecraft:crafting_shaped',
    pattern: ['A B'],
    key: {
        A: { item: 'minecraft:iron_nugget' },
        B: { item: 'minecraft:stick' }
    },
    result: { id: 'minecraft:iron_nugget' }
})
```

Here A is primary role 0 and B is auxiliary role 1; the empty cell between them is not numbered. A three-cell-wide pattern fits only in a crafting table, not the 2×2 inventory grid.


Left- or right-click takes one batch: the first stack goes to the cursor and the rest to inventory. Shift crafts repeatedly. Returned items go back to their original slots first, then to inventory; if there is not enough room, inputs remain. Number-key / offhand swaps require an empty target slot. The graph preview does not support middle-click copying or dropping directly with Q.

Special graph steps for dyeing and repair are described below; other special crafting recipes and third-party menus are not connected.

### Vanilla Crafter

Standard `minecraft:crafting_shaped` and `minecraft:crafting_shapeless` graph nodes can also run in the vanilla Crafter; no separate recipe type is needed. Use the same full native JSON or `{ type, recipe_id }` reference described above and the same primary/auxiliary workpiece validation. Logical input 0 is the primary workpiece, not a fixed physical slot number.

Each redstone rising edge processes one batch; disabled slots follow vanilla behavior. Outputs and native returned items are sent through the Crafter's front face into a container; items that do not fit are ejected as in vanilla. Returned items are not put back into the input grid. Standard shaped/shapeless recipes and the two special recipe types below are supported. Other special recipes, custom recipe subclasses, and addon machines are out of scope.

### Vanilla dyeing and repair

The inventory, crafting table, and vanilla Crafter support the exact native `ArmorDyeRecipe` (`minecraft:crafting_special_armordye`) and `RepairItemRecipe` (`minecraft:crafting_special_repairitem`). Native matching and results are retained; the recipe definitions stay unchanged.

For dyeing, the item being dyed is the sole primary workpiece (logical input 0); each non-empty dye slot is numbered as an auxiliary input 1, 2, ... from left to right and top to bottom. For repair, the first non-empty item is the primary workpiece and the second is the auxiliary workpiece. Each is validated separately, and only primary progress is inherited. Native repair removes the name and ordinary enchantments; curses and durability follow the native result.

If an auxiliary item already carries graph progress, declare its role in `workpiece_inputs` and ensure it can enter the current node. Undeclared auxiliary inputs must be ordinary items.

A non-finish node uses the native result as the workpiece carrier and advances the graph; the graph-level default carrier does not replace it. A finish node settles graph outputs and does not additionally grant the native result. Neither recipe type allows `transitional_item`.

```javascript
.node('dye', { type: 'minecraft:crafting_special_armordye', category: 'misc' })
.node('repair', { type: 'minecraft:crafting_special_repairitem', category: 'misc' })
```

Create special graph steps are not enabled; other special crafting recipes are not enabled either.

### Brewing Stand

Brewing nodes use `graph_assembly:brewing`; potion effects are determined by native brewing rules. `primary` selects which input supplies graph progress, while `addition` constrains the other input:

| `primary` | Input 0 (primary) | Input 1 (auxiliary / `addition`) |
|---|---|---|
| `bottle` (default) | One bottle | Shared brewing ingredient |
| `ingredient` | Shared brewing ingredient | One bottle |

```js
// The bottle keeps its own progress while brewing with nether wart.
.node('brew_from_bottle', {
  type: 'graph_assembly:brewing',
  addition: { item: 'minecraft:nether_wart' }
})

// The ingredient distributes its progress to every eligible bottle.
.node('brew_from_ingredient', {
  type: 'graph_assembly:brewing', primary: 'ingredient',
  addition: { item: 'minecraft:potion' }
})
```

Each bottle advances independently and makes its own next-step or settlement draw; the shared ingredient is consumed only once. With the ingredient as primary, all bottles start from the same progress and then proceed independently. With bottles as primary, each keeps its own history.

Each bottle and any progress-bearing ingredient must have a count of one. If the auxiliary input also carries progress, declare `workpiece_inputs: [{ item_input: 1 }]`. Both inputs are checked separately, and the result inherits only primary progress.

If any progress-bearing input fails role, route, or native brewing validation, the entire batch does not process. In ingredient-primary mode, ordinary bottles not claimed by the graph remain unchanged. With a bottle primary and an ordinary ingredient, compatible ordinary bottles can still brew under native rules.

Intermediate steps retain the native potion result and components; the graph default carrier does not replace it, and the node cannot set `transitional_item`. At a finish node, each bottle may settle to nothing or one item. **Every possible result** must fit this capacity; otherwise the batch does not process, and graph outputs are neither truncated nor dropped. Fuel, duration, and returned ingredients follow vanilla behavior. The descriptor does not provide `recipe_id`, `result`, cost, or time fields.

### Anvil

Anvil graph nodes use `graph_assembly:anvil` and operate through the vanilla anvil interface. They describe an operation to perform; they do not create a native recipe and do not use `recipe_id`.

| `action` | Operation |
|---|---|
| `work` (default) | Repair or enchantment upgrade, excluding rename-only operations |
| `repair` | Repair |
| `enchant` | Enchantment upgrade |
| `rename` | Rename; the right slot must be empty |
| `combine` | A legal two-input combination supported by the host; rename-only and repair-cost-only changes do not qualify |

`addition` can use an Ingredient to constrain the right-slot material; `rename` does not use this field. `name` is only for `rename`: it specifies the exact target name; an empty string removes the name; if omitted, any actual rename is accepted.

```javascript
.node('repair', {
  type: 'graph_assembly:anvil', action: 'repair',
  addition: { item: 'minecraft:iron_ingot' }
}, { start: true })
.node('finish', {
  type: 'graph_assembly:anvil', action: 'rename', name: 'Repaired'
}, { finish: true })
.edge('repair', 'finish')
```

For example, a damaged iron sword enters the graph, is repaired with an iron ingot, then renamed to “Repaired” at the finish node. Declare graph input, default carrier, and settlement outputs as usual.

The left slot is always logical input 0, the primary workpiece; the right slot is logical input 1, the auxiliary workpiece. Progress-bearing items in both slots must be declared and validated separately. The result inherits only left-slot progress. The primary item and native result must each have a count of one; a progress-bearing auxiliary item can only be consumed one at a time. Ordinary materials are consumed under native rules.

Results, level costs, and material consumption follow host rules. Intermediate steps retain the native result, which is not replaced by the graph default carrier; node-level `transitional_item` is unavailable. A finish settles through the graph and does not additionally grant the native equipment result.

### Furnaces, smokers, and blast furnaces

Standard `minecraft:smelting`, `minecraft:smoking`, and `minecraft:blasting` recipes run in the vanilla furnace, smoker, and blast furnace, respectively. Use full native JSON or a `{ type, recipe_id }` reference. Logical input 0 is the sole primary workpiece; fuel is not an auxiliary ingredient, and a second workpiece role cannot be declared.

- `cookingtime` is the processing duration in ticks and must be greater than 0. `experience` is the XP per successfully processed batch and must be finite and non-negative. Fuel duration, cooling after extinguishing, and empty-container returns follow vanilla behavior.
- The native `ingredient` position is supplied by the graph entry input or current workpiece. `result` must remain for native decoding, but actual outputs come from graph rules; the native result is not additionally granted.
- A furnace has one output slot. **Every possible output combination must fit in full**; it is not enough that the current result fits. Items with identical item and components can stack together. Processing waits if a batch outputs different items at once or exceeds the stack limit. Different mutually exclusive outputs can settle normally when the slot is empty.
- A full output slot does not consume this batch's input or perform a draw; fuel that is already burning continues as in vanilla. Completed graph workpieces can be removed with a hopper.
- Special recipe subclasses and addon custom furnaces are not connected.

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:raw_iron' })
        .node('smelt', {
            type: 'minecraft:smelting',
            ingredient: { item: 'minecraft:raw_iron' },
            result: { id: 'minecraft:iron_ingot' },
            experience: 0.7,
            cookingtime: 200
        }, { start: true, finish: true })
        .output({ results: [{ item: 'minecraft:iron_nugget' }] });
    event.custom(graph.build()).id('my_pack:graph/raw_iron');
});
```

This example consumes raw iron and fuel and settles to an iron nugget; the iron ingot in native `result` is not granted again.

### Campfire and soul campfire

Standard `minecraft:campfire_cooking` recipes can run on a campfire or soul campfire. The node condition `campfire: "normal"` requires a regular campfire, and `campfire: "soul"` requires a soul campfire. Omit the field or use `"any"` to allow either. The condition is a sibling of `recipe`, not part of the native recipe; it affects only the graph step and does not change ordinary food recipes. Full native JSON and `{ type, recipe_id }` references retain native `cookingtime` behavior.

```js
.node("soul_cook", {
  type: "minecraft:campfire_cooking",
  ingredient: { item: "minecraft:potato" },
  result: { id: "minecraft:baked_potato" },
  cookingtime: 600
}, { campfire: "soul" })
```

The same input can have separate `normal` and `soul` entries, routing it according to the campfire used. A workpiece already in a graph must still match its route; placing it on the wrong campfire does not consume it or send it through ordinary processing.

Each of the four slots can process one item independently. Each operation has only logical input 0, the primary workpiece; auxiliary workpieces cannot be declared. When extinguished, the campfire cools as in vanilla and resumes timing when relit. On completion it drops the full graph settlement output, without the furnace's single-output-slot restriction. Campfires consume no fuel items, grant no furnace XP, and have no native hopper inventory input. Changing the item or changing the graph / referenced recipe definition restarts the timer. Special recipe subclasses and addon custom campfires are not supported by this adapter.

### Stonecutter

A standard `minecraft:stonecutting` node uses full native JSON (including `result`) or a strict `{ type, recipe_id }` reference. Logical input 0 is the sole primary workpiece; do not declare auxiliary workpieces. The native ingredient position is supplied by the graph entry input or current workpiece, and actual outputs come from graph rules.

- Select an executable graph step in the Stonecutter's native list; different graphs and nodes can be selected separately.
- Left- or right-click completes one batch: the first stack goes to the cursor and the rest to inventory. Shift processes successive batches while inputs and capacity permit. There must be room for every possible output; outputs are not dropped or redrawn to fit available space.
- Reselect after workpiece progress or recipe definitions change. Closing the menu returns inputs according to vanilla behavior.

Example node:

```javascript
.node('cut', {
  type: 'minecraft:stonecutting',
  ingredient: { item: 'minecraft:stone' },
  result: { id: 'minecraft:stone_slab' }
}, { start: true })
```

`result` is required for native decoding. This subsection describes the vanilla Stonecutter menu; the same standard node can also run on a Create Mechanical Saw.

### Smithing table

A standard `minecraft:smithing_transform` recipe can run in the vanilla Smithing Table, using full native JSON or a `{ type, recipe_id }` reference. The primary workpiece is the middle base: logical input 0 is the base, 1 is the template on the left, and 2 is the addition on the right.

Each of the three slots consumes one item. Declare a template or addition as an auxiliary workpiece with `workpiece_inputs`; both workpieces are validated against graph and route separately, and the output inherits only the primary workpiece's progress. Native `result` is required for decoding; it does not additionally grant an upgraded item alongside graph outputs.

```javascript
.node('upgrade', {
  type: 'minecraft:smithing_transform',
  template: { item: 'minecraft:netherite_upgrade_smithing_template' },
  base: { item: 'minecraft:diamond_sword' },
  addition: { item: 'minecraft:netherite_ingot' },
  result: { id: 'minecraft:netherite_sword', count: 1 }
})
```

The graph entry input or current workpiece replaces the `base` condition; the template and addition still match their native conditions. Left- or right-click takes a full batch; Shift processes successive batches subject to capacity.

Standard `minecraft:smithing_trim` recipes can also run as graph steps. Input roles and the three-slot consumption rules are the same, but the native `base` condition is **not replaced**: the current workpiece must be valid for that trim.

```js
{
  type: 'minecraft:smithing_trim',
  template: { item: 'minecraft:coast_armor_trim_smithing_template' },
  base: { item: 'minecraft:diamond_chestplate' },
  addition: { item: 'minecraft:amethyst_shard' }
}
```

- Intermediate steps retain the trimmed armor, name, durability, and enchantments, then attach the primary workpiece's new progress; graph-level `transitional_item` does not replace it with another item.
- Trim nodes cannot set node-level `transitional_item`.
- If the armor already has the same pattern and material, vanilla treats the operation as unchanged: no materials are consumed and no graph progress is made.
- A finish node must still complete the native trim operation, then settles through the graph without additionally granting the armor.

Other dynamic crafting, unknown recipe subclasses, and addon smithing menus are not enabled by this support.


### Additional processing steps without handwritten custom JSON

The complete [additional_create_steps.js](../../examples/kubejs/additional_create_steps.js) example runs crushing → polishing → washing → haunting → emptying on their corresponding machines. These steps also work in native `.sequence(...).step(...)` wrappers.

```js
// Step-mode recipe fragments: do not repeat the primary item input/output.
const crushing = {type: 'create:crushing', processing_time: 100};
const polishing = {type: 'create:sandpaper_polishing'};
const washing = {type: 'create:splashing'};
const haunting = {type: 'create:haunting'};
const emptying = {type: 'create:emptying', results: [{id: 'minecraft:water', amount: 250}]};
```

For emptying, `{id, amount, components?}` describes a native Create fluid result, not a graph exit pool. Progress stays on the item. Each round consumes one primary item, transfers fluid to the tank, and delivers the item results chosen by the graph or native parent sequence. Before consuming inputs, the machine checks tank capacity, fluid components, and whether the inputs still match the saved snapshot.

Full native emptying recipes and references must keep both item and fluid outputs. When the step completes, the primary item result is replaced with the updated workpiece or settlement output.

Sandpaper keeps its native use duration, durability, and Deployer tool consumption. Washing and haunting keep their native exposure timers and completion locks. Move the workpiece out of the airflow and back in for the next round; leaving it exposed does not repeatedly advance it.

## 5. Builder API

All authoring methods return the builder; `build()` returns JSON and `register()` returns the registered `KubeRecipe`:

| Method | Parameters and behavior |
|---|---|
| `GraphAssembly.create(transitionalItem)` | Create a graph; pass the item ID string for the default workpiece |
| `.ingredient(value)` | Set the default entry Ingredient; accepts an ID, tag, Ingredient object, or JSON. Calling it again replaces the previous value |
| `.node(id, recipe)` | Add a node with default options |
| `.node(id, recipe, options)` | Add a node; `recipe` accepts legacy JSON or a newly created upstream KJS recipe object, and `options` are node options |
| `.nativeNode(id, recipe, options)` | Embed full native recipe JSON; `options` may be omitted |
| `.edge(from, to)` | Add a directed edge with weight 1; pass `options` as the third argument, such as `{ weight: 2 }` or `{ waypoints: [[x, y]] }` |
| `.weightedEdge(from, to, weight)` | Add a directed edge with a positive weight |
| `.bidirectionalEdge(from, to)` | Add both `from → to` and `to → from` edges |
| `.output(rule)` | Append a graph-level settlement rule; also accepts an item string, ItemStack, or equally weighted candidate array. Can be called multiple times |
| `.build()` | Validate graph structure and return a JSON copy; it **does not register the recipe** |

Register directly with `graph.register(event, 'namespace:path')`. The existing `event.custom(graph.build()).id('namespace:path')` flow remains supported.

Notes:

- Node IDs must be unique within a graph; nodes referenced by edges and conditions must exist.
- Every graph needs at least one `start: true` entry; each entry needs its own ingredient or the graph-level default ingredient.
- Do not add the same directed edge between a pair of nodes more than once. Use `.edge('loop', 'loop')` for a self-loop; do not call `bidirectionalEdge` with the same node at both ends.
- Weights must be finite positive numbers, not 0, negative values, `NaN`, or `Infinity`.
- `build()` validates graph format and references. Items, native recipe decoding, machine adapters, and conflicts are also checked when the game loads recipes. Passing `build()` does not guarantee that a machine can execute the graph.
- There is no graph-wide `chance()` method; set weights and probabilities on output entries.

### Fluent registration, conditions, and exit pools

| Method | Behavior |
|---|---|
| `.register(event, id)` | Validate, register internally through KubeJS, and return its `KubeRecipe`; requires a namespaced ID |
| `.finishOutput(nodeId, results[, requirements])` | Mark an existing node as an exit and append one local settlement rule |
| `.scrap(nodeId, results)` | Append local fallback scrap entries |
| `.requirements(nodeId, requirements)` | Set requirements for logical primary item input `0`, retaining `current_nodes`, auxiliary input roles, and all other node settings |
| `.conditionalEdge(from, to, requirements[, weight])` | Add a conditional directed edge (default weight `1`) |
| `.branchMode(nodeId, mode)` | Set `choice` or `random` branching on an existing node |
| `.progressAction(nodeId, action)` | Append a standard `progress_actions` entry |

Add a node before configuring it by ID. Finish and scrap entries **append** to existing settings. At an exit, matching graph-level and current-node rules contribute to the same output pool; node rules do not override global rules. Scrap is used only when a strict exit has no matching success rule. Lenient exits do not use it.

```js
ServerEvents.recipes(function (event) {
    GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:raw_gold')
        .node('press', {type: 'create:pressing'}, {start: true, mark_amounts: {quality: 1}})
        .node('finish', {type: 'create:cutting'}, {finish_mode: 'strict'})
        .requirements('finish', {marks: {quality: 1}})
        .conditionalEdge('press', 'finish', {marks: {quality: 1}}, 2)
        .finishOutput('finish', [{item: 'minecraft:diamond', weight: 3}, {item: 'minecraft:emerald', weight: 1}])
        .scrap('finish', 'minecraft:flint')
        .register(event, 'my_pack:fluent_graph');
});
```

These methods use the same `requirements` format. Marks default to exact matching; `mode: 'partial'` allows additional marks. Checkpoint, count, and range conditions keep their usual meaning.

Edge requirements inspect the workpiece after the source node completes, including its mark grants, checkpoint, and progress actions. Random branches first filter edge requirements and check target primary-workpiece conditions and mark capacity, then draw from the remaining weighted candidates. With no eligible edge, the workpiece stops without a draw. Conditions are checked again before the target operation; auxiliary ingredients and machine state are not checked before the draw.

`.requirements()` sets primary-input conditions without changing its role or merging auxiliary progress. Multi-workpiece inputs still need explicit `workpiece_inputs`, and the result keeps only the primary workpiece's history.

### Native Create sequences

`GraphAssembly.sequence(transitionalItem)` builds a real `create:sequenced_assembly`, with Create handling the loops. It provides `.ingredient(value)`, `.step(recipe[, options])`, `.loops(positiveInteger)`, `.output(results)`, `.build()`, and `.register(event, id)`.

Each step becomes a `graph_assembly:sequence_step` wrapper; step options accept only `recipe_mode` and `campfire`. The outer Create recipe handles loops, native progress components, and final result selection. A sequence needs at least one step and one result.

```js
ServerEvents.recipes(function (event) {
    GraphAssembly.sequence('minecraft:iron_nugget')
        .ingredient('minecraft:iron_ingot')
        .step(event.smelting('minecraft:iron_nugget', 'minecraft:iron_ingot').cookingTime(60))
        .step({type: 'create:pressing'})
        .loops(2)
        .output(Item.of('minecraft:iron_pickaxe', 1, {'minecraft:damage': 5}))
        .register(event, 'my_pack:fluent_sequence');
});
```

A newly created `KubeRecipe` is captured as an independent snapshot at `.step()` and marked synthetic, so it is not registered separately. Later changes to the original object cannot affect the step. KubeJS callbacks and ingredient actions are unsupported and produce an error.

ItemStacks and components use the same normalization as graph outputs, but sequence results use Create's native `{id, count, components, chance}` format instead of graph `weight` or group rules. In a native sequence, `chance` is a relative result weight and can exceed `1`. Each `.output()` call appends results.

You can still register with `build()` and `event.custom(...)`. See [the complete fluent example](../../examples/kubejs/fluent_authoring.js).

## 6. Node options

The third argument to `.node(id, recipe, options)` supports:

| Field | Default / behavior |
|---|---|
| `start` | `false`; marks an entry |
| `recipe_mode` | Omitted or `'step'` uses the shorthand convention; `'native'` means a full native template with no primary input inserted. Upstream recipe objects and `nativeNode()` set this automatically |
| `finish` | `false`; marks an exit and may be `true` at the same time as `start` |
| `ingredient` | Entry inherits the graph-level ingredient; a different ingredient can be set per entry. Not used to match a non-entry node |
| `transitional_item` | First operation defaults to the graph workpiece; later operations keep the current item. An explicit value replaces the workpiece item after a successful operation |
| `mark_amounts` | `{}`; marks added by a successful operation, such as `{ precision: 1 }` |
| `max_mark_applications` | Unlimited when omitted; limits the number of times **this node grants marks to each workpiece**, not the number of operations. 0 means no mark grant |
| `checkpoint` | `false` by default for ordinary nodes; entries and exits always record a checkpoint, even if set to `false` |
| `branch_mode` | `'choice'`; may also be `'random'`, controlling outgoing edges after this node completes |
| `finish_mode` | `'lenient'`; may also be `'strict'`, controlling what happens when no settlement rule matches at an exit |
| `outputs` | `[]`; settlement rules added when this node is the current exit; does not replace graph-level rules |
| `scrap` | `[]`; strict-failure scrap pool, in the same format as settlement `results` |
| `workpiece_inputs` | `[]`; declares workpiece roles identified by native logical item-input index; see below |
| `progress_actions` | `[]`; modifies primary-workpiece progress in order after a successful operation; see below |
| `position` | Automatic layout; can be `[x, y]`. The plane canvas center is the origin, x increases rightward, y downward |
| `sphere_position` | Automatic sphere layout; can be a nonzero `[x, y, z]`, normalized to a direction on the sphere |

Mark names are author-defined and do not need prior registration. Mark increments and grant limits must be integers from `0..2147483647`. If accumulated marks would overflow, the step waits before consuming ingredients; values are not wrapped or truncated.

`mark_amounts` sets the amount of each mark granted by a successful step. `max_mark_applications` limits how many times that node may grant marks to each workpiece; reaching the limit does not prevent further processing. The saved `mark_grant_counts` map records those per-node counts and is not a node option. Clearing marks does not reset the counts. Granting several marks at once counts as one application. A nonempty map of zero increments still counts as one application; an empty map does not.

Layout coordinates affect only the preview, not processing order. Processing must follow a legal route, with each workpiece checked separately: a saved specified route (`pending_route`) takes precedence; without one, follow the random lock, or ordinary outgoing edges if there is no lock. A `progress_actions` route change can override ordinary edges or a random draw, but the target's own input conditions must still be met. If a lenient exit has no matching settlement rule, it retains the workpiece's post-operation progress and follows a specified route first; only without one does it continue along an outgoing edge. It cannot roll back to the pre-operation state. After a successful operation, processing order is mark grants → append checkpoint → `progress_actions` → exit settlement → route handling if unsettled.

### Dual-workpiece Basin merge example

Copy the complete [`examples/kubejs/multi_workpiece.js`](../../examples/kubejs/multi_workpiece.js) script by itself into `kubejs/server_scripts/`, then run `/reload`. It uses `.node(..., options)` and `.output(...)` without additional builder methods. The example has been loaded in a running KubeJS environment. Do not enable it alongside a graph with overlapping entry conditions.

Prepare and process two workpieces from the **same graph**:

1. Press a gold ingot to enter `body`, producing an iron nugget primary workpiece with `quality = 2`.
2. Cut an amethyst shard to enter `insert`, producing a second iron nugget auxiliary workpiece with `quality = 99`.
3. Send the primary workpiece directly to `join`, or use a Deployer with quartz to run `polish` first. Polishing records a checkpoint and changes the primary workpiece's `quality` to 4.
4. Put the primary and `insert` auxiliary workpieces into a Basin and mix them to complete `join`. Both are consumed under native rules, and the primary output inherits only primary progress; the auxiliary's 99 is not added to the primary quality.
5. Press the primary output again to reach `finish`: the unpolished route yields an emerald, while the route polished exactly once yields a diamond.

Declare merge roles in the **third argument** to `.node`, not inside `recipe`:

```javascript
.node('join', {
    type: 'create:mixing',
    ingredients: [{ item: 'minecraft:iron_nugget' }]
}, {
    checkpoint: true,
    workpiece_inputs: [
        { item_input: 0, current_nodes: ['body', 'polish'] },
        { item_input: 1, current_nodes: ['insert'],
          requirements: { mark_ranges: { quality: { min: 99, max: 99 } } } }
    ]
})
```

Here the iron nugget in `ingredients` is the auxiliary Ingredient; the system inserts the primary ingredient into the Create Basin recipe. `item_input` is the logical item Ingredient index **after inserting the primary input and decoding through the native codec**: 0 is always the primary workpiece, 1 is the first auxiliary item. It is **not a physical Basin slot index and not the original index in a mixed item/fluid JSON array**. Fluids are not counted. See “Inventory and crafting-table recipes” above for vanilla crafting indices.

- Each logical index can be declared at most once and cannot exceed the recipe's actual native item-input count. The native Ingredient must still match; a role is not an alternate item selector.
- A declared role must carry valid progress, belong to the target **same graph**, and be allowed to enter `join` by its current legal route: check a saved specified route (`pending_route`) first, then the random lock or ordinary outgoing edge if there is no specified route. `current_nodes` and conditions do not grant a route by themselves; the target must still process successfully and meet applicable input, machine, and settlement conditions.
- `current_nodes` may be omitted to add no “current node must be in this list” restriction. If provided, it must be non-empty, contain no duplicates, and reference nodes in this graph. Even when omitted, the role must follow its specified route / random lock / ordinary outgoing-edge rules.
- Role `requirements` apply to that input's own marks and complete checkpoint history. An undeclared ordinary auxiliary input must not carry graph progress; a workpiece from another graph or an ineligible workpiece cannot be substituted for an ordinary ingredient.
- Role 0 may be omitted; the primary workpiece still follows normal traversal rules. If role 0 is explicitly declared, it must be an entered workpiece, not an ordinary entry ingredient.
- Only the primary workpiece's progress after the operation is carried into the primary output. Auxiliary marks, checkpoints, mark-grant counts, and locks are not merged, and byproducts do not clone graph progress. Retention or consumption still follows the machine's native rules, such as Deployer `keep_held_item`.

The example's two exit conditions are:

| Primary route | Primary quality range | `polish` checkpoint count | Settlement |
|---|---|---|---|
| `body → join → finish` | `min: 2, max: 2` | `max: 0` | Emerald |
| `body → polish → join → finish` | `min: 4, max: 4` | `min: 1, max: 1` | Diamond |

The script uses the `checkpoints` `partial` condition to require the primary route in order. Both the ranges and route condition must match; reaching the required quality alone is not enough. See Section 9 for field rules.

### Clear progress and redirect after processing

The third node argument can contain `progress_actions`. For example, after successful rework, remove 2 defect marks and make `inspect` the next step again:

```javascript
.node('rework', {
    type: 'create:milling', processing_time: 40
}, {
    progress_actions: [
        { type: 'clear_mark', mark: 'defect', count: 2 },
        { type: 'goto_node', node: 'inspect' }
    ]
})
```

This is a builder fragment; `inspect` must be defined in the same graph. If `clear_mark.count` is omitted, the mark is cleared. A non-negative integer subtracts that amount; subtraction below zero clears the mark, and 0 leaves it unchanged.

Other actions:

- `{ type: 'clear_checkpoint', node: 'inspect' }`: remove all checkpoint records for `inspect`.
- `{ type: 'pop_checkpoint' }`: remove the most recent checkpoint record; an empty list remains empty.
- `{ type: 'enter_edge', from: 'inspect', to: 'finish' }`: select an existing `inspect → finish` edge, making `finish` the next operation. It does not run `inspect` or add its progress.

A node can have at most one `goto_node` or `enter_edge`. After successful processing, marks are granted and the current checkpoint is appended first; actions then run in list order, followed by settlement. If settlement occurs, no redirect follows. Start and finish nodes record checkpoints automatically, so `pop_checkpoint` on either removes the current operation's record.

A redirect overrides ordinary outgoing edges and random draws, but does not bypass the target's input conditions. The entry, recent processing history, and mark-grant count remain unchanged; ingredients are not refunded. For dual-workpiece recipes, only the primary workpiece is modified. A target's `current_nodes` checks the node that actually just completed, not the selected edge's source. `goto_node` specifies the next operation; it does not skip that target's processing, entry ingredient, or settlement conditions.

See [progress-actions.json](../../examples/datapack/progress-actions.json) for a complete datapack example. Place it at `data/my_pack/recipe/progress_actions.json` to test it by itself:

1. Phantom membrane pressing → cutting inspection → direct press exit: defect is 3, yielding flint scrap.
2. After inspection, mill for rework: defect drops to 1 and old inspection records plus the most recent checkpoint are cleared. Cut for inspection again, then press to obtain a diamond.
3. After inspection, use a Deployer with redstone to repair: clear defect and enter the `inspect → finish` edge; pressing directly yields a diamond.

Workpieces save their specified route; transport, filtering, and loading a save do not repeat actions. Processing waits and retains the workpiece if the target or edge is removed, or the source node's redirect configuration changes. See [recipe format](../reference/recipe-format.md#post-processing-progress-actions) for the full field definition.

## 7. Choice branches

`choice` means all successor operations are available as choices, usually determined by the machine and ingredients actually supplied; the Stonecutter uses its native recipe-list selection. The following two branches both use a Deployer and are distinguished by different auxiliary ingredients:

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:andesite' })
        .node('start', { type: 'create:pressing' }, { start: true })
        .node('iron_route', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:iron_nugget' }]
        }, { finish: true })
        .node('gold_route', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:gold_nugget' }]
        }, { finish: true })
        .edge('start', 'iron_route')
        .edge('start', 'gold_route')
        .output({
            finish: 'iron_route',
            results: [{ item: 'create:andesite_alloy' }]
        })
        .output({
            finish: 'gold_route',
            results: [{ item: 'minecraft:gold_ingot' }]
        });

    event.custom(graph.build()).id('my_pack:graph/choice');
});
```

Do not connect two operations with indistinguishable conditions to the same `choice` node, such as two pressing steps with no auxiliary ingredients. Different node names, positions, or processing times are not machine-selection conditions; statically detectable ambiguity disables the entire graph.

Entries from different graphs also conflict if they accept the same input and auxiliary conditions on the same machine. Use different entry ingredients in released examples, or enable only one of them.

## 8. Random branches, loops, and mark-grant limits

Random mode is set on the **source node of outgoing edges**. Weights determine the next step, not the probability of the final output.

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:clay_ball' })
        .node('start', { type: 'create:pressing' }, { start: true })
        .node('polish', {
            type: 'create:cutting', processing_time: 40
        }, {
            mark_amounts: { quality: 1 },
            max_mark_applications: 3,
            checkpoint: true,
            branch_mode: 'random'
        })
        .node('finish', { type: 'create:pressing' }, {
            finish: true,
            finish_mode: 'strict',
            scrap: [{ item: 'minecraft:dead_bush' }]
        })
        .edge('start', 'polish')
        .weightedEdge('polish', 'polish', 3)
        .weightedEdge('polish', 'finish', 1)
        .output({
            requirements: { mode: 'partial', marks: { quality: 3 } },
            results: [{ item: 'minecraft:diamond' }]
        });

    event.custom(graph.build()).id('my_pack:graph/random_loop');
});
```

After each `polish` operation, the next step is drawn at 3:1: 75% chance to cut again, 25% chance to go to the pressing exit. The selected target is saved on the workpiece. Viewing it in JEI, using a filter, or reloading recipes does not redraw; send the workpiece to the machine for its locked target.

Marks are granted at most three times, but the loop can continue afterward. When exit processing completes, `quality >= 3` yields a diamond; otherwise the strict failure yields a dead bush. A loop does not run automatically on a timer: a machine must complete each operation.

## 9. Completion requirements and output settlement

### Settlement rules

`.output(rule)` and node option `outputs` accept the same rules:

| Field | Purpose |
|---|---|
| `start` | Optional; restricts the entry node where this workpiece first entered |
| `finish` | Optional; restricts the current exit node |
| `requirements` | Optional; matches marks / checkpoints, mark ranges, and checkpoint-count ranges |
| `results` | Required and non-empty; candidate outputs |

Only a node completed with `finish: true` attempts settlement. The node's marks are granted and its checkpoint is recorded, then `progress_actions` run, and then rules are checked. Conditions can include the current exit and marks newly added by this operation. If an action clears related marks or checkpoints, conditions see the current retained state after the action.

### Condition syntax

These are JSON fragments for `requirements`, not standalone scripts; referenced node names must exist in your graph:

| Goal | `requirements` |
|---|---|
| Do not check conditions | Omit, or `{}` |
| Marks must match exactly | `{ "marks": { "precision": 2 } }` |
| Marks must meet a minimum | `{ "mode": "partial", "marks": { "precision": 2 } }` |
| Checkpoint history must match exactly | `{ "checkpoints": ["start", "wash", "finish"] }` |
| Visit in order, allowing extra records between | `{ "mode": "partial", "checkpoints": ["start", "finish"] }` |
| Require visits without requiring order | `{ "checkpoints": { "mode": "unordered", "nodes": ["wash", "finish"] } }` |

If both `marks` and `checkpoints` are configured, both must match.

- `exact` is the default mode. Marks cannot have extra keys or counts. Checkpoints must exactly match the currently retained sequence: no extra entries, missing entries, or order changes. `progress_actions` can clear checkpoints, so this is not an immutable audit log.
- For marks, `partial` means minimum counts; for checkpoints, it means an ordered subsequence. Repeated requirements need the corresponding number of history records.
- `unordered` is a local mode for checkpoints only. It checks that the listed nodes are present and does not use repeated entries to count visits.
- Entries and exits are automatically added to checkpoint history; an ordinary node is recorded only when `checkpoint: true`. Repeated visits in a loop produce repeated checkpoint entries.
- `requirements: {}` disables checking. By default, `requirements: { marks: {} }` requires **no marks at all**; `checkpoints: []` requires empty history and is usually impossible at an exit.

A local mode can override the shared mode, for example:

```json
{
  "mode": "partial",
  "marks": { "mode": "exact", "values": { "precision": 2 } },
  "checkpoints": { "mode": "partial", "nodes": ["start", "finish"] }
}
```

### Mark ranges, visit counts, and forbidden visits

`requirements.mark_ranges` checks the accumulated count for specified marks against a range. `requirements.checkpoint_counts` checks how many times specified nodes appear in the **complete checkpoint sequence**. These can also be used inside the role `requirements` for `workpiece_inputs`.

For example, these conditions require quality from 2 to 4, exactly one `join` checkpoint, and no `polish` checkpoint records:

```json
{
  "mark_ranges": { "quality": { "min": 2, "max": 4 } },
  "checkpoint_counts": {
    "join": { "min": 1, "max": 1 },
    "polish": { "max": 0 }
  }
}
```

- Ranges **include both endpoints**. Each range must specify at least one of `min` or `max`. An omitted lower bound is 0; an omitted upper bound is `2147483647`. Endpoints must be integers in the allowed range, and the lower bound cannot exceed the upper bound.
- `quality: { min: 3 }` means at least 3; `quality: { max: 3 }` means 0–3; `{ min: 3, max: 3 }` means exactly 3; `{ max: 0 }` means it must be 0. Missing marks and unrecorded checkpoints count as 0.
- `checkpoint_counts: { polish: { max: 0 } }` can express a “polishing route forbidden” condition, provided `polish` actually has `checkpoint: true`. Processing an ordinary non-checkpoint node does not create a checkpoint, so counts cannot tell whether it was visited.
- Counts use the currently retained complete checkpoint sequence (cleared or popped records no longer count), not the last three steps shown in a tooltip and not the per-node grant counts in `mark_grant_counts`. Entries / exits count automatically; repeated visits to checkpoints count repeatedly. Checkpoint keys must reference nodes in the graph.
- The four condition types `marks`, `checkpoints`, `mark_ranges`, and `checkpoint_counts` are combined with **AND**. Ranges constrain only the names they list; they do not change existing exact / partial / unordered semantics and are not affected by the shared `mode`.
- Omitted conditions are not checked. An empty range map such as `mark_ranges: {}` adds no restriction, but an individual empty range such as `quality: {}` is invalid. This differs from the default exact empty-mark requirement of legacy `marks: {}`.

### Weighted selection by default; nested groups for simultaneous and independent-probability outputs

The outermost entries in `results` from all matching graph-level and current-exit rules form one weighted pool. One entry is selected and executed recursively. Rules do not get separate rolls or priority based on their order. Ordinary item entries keep their behavior; groups can produce multiple items at once.

For example, if this is the only matching rule, the result is two gold ingots with 75% probability and one iron ingot with 25% probability:

```json
{
  "results": [
    { "item": "minecraft:gold_ingot", "count": 2, "weight": 3 },
    { "item": "minecraft:iron_ingot", "count": 1, "weight": 1 }
  ]
}
```

`count` and `weight` both default to 1. Count must be a positive integer that fits in one normal item stack. Optional `components` uses Minecraft 1.21.1 native item-component JSON, not a legacy `nbt` string.

To always produce A, then independently roll for B and C, put them in one `all` group; do not write them as three top-level rules:

```json
{"results": [{"mode": "all", "results": [
  {"item": "minecraft:diamond"},
  {"item": "minecraft:emerald", "count": 3, "chance": 0.5},
  {"item": "minecraft:gold_ingot", "chance": 0.25}
]}]}
```

- `all`: execute every child entry. A child's `weight` does not affect whether it executes.
- `weighted`: choose one child by its local weight; the selected child can be an item or a whole group. `all` and `weighted` groups can nest recursively and are not flattened into one global pool.
- Item `chance` follows Create's native meaning, ranges from `0..1`, and defaults to `1`. `count: 3, chance: 0.5` makes three independent rolls and produces 0–3 items; it does not give a 50% chance of a full stack of 3. The counts are not equally likely.
- A group's `chance` is rolled once for the whole group. To produce a full stack of 3 with 50% probability, write `{"mode":"all","chance":0.5,"results":[{"item":"minecraft:emerald","count":3}]}`; the result is either 0 or 3.
- A group's own `weight` is used only when its parent makes a weighted selection; each `weighted` level is normalized separately. A failed `chance` does not select a sibling or reroll.
- If a successful rule is selected but all its probability rolls miss, graph processing still ends; the scrap pool is not triggered. An empty `all` group can explicitly produce no items; an empty `weighted` group is invalid.
- `scrap` supports the same nested structure and is used only when a strict exit has no matching successful output pool.
- Item entries allow only `item/count/weight/components/chance`; groups allow only `mode/results/weight/chance`. Misspelled fields report an error with the specific JSON path.
- Operation `recipe.results` remain native byproducts **independent of settlement**; they are not cancelled when settlement fails or yields no items. Graph settlement entries use `item`, not native `id`.

Copy [nested_probability_outputs.js](../../examples/kubejs/nested_probability_outputs.js) for an example with a fixed diamond, an independent 0–3 emerald roll, a chance-based gold ingot, and an additional `weighted → all → weighted` output. Its successful rule has no conditions, so its scrap example requires adding a condition to the successful rule before scrap can trigger.

**Machine capacity:** The maximum simultaneous output combination is reserved in the Basin, Millstone, and Saw; when space is insufficient, inputs are retained rather than rerolling to avoid a large combination. The Spout uses a single-item-stack API: if the current workpiece could settle to multiple item stacks, the step does not run and consumes neither items nor fluid. Put multi-output exits on a Press, Deployer, Basin, or another machine that supports output lists. Ordinary filling steps and single-stack settlement are unaffected.

**JEI:** Candidate counts are merged for display, such as `0-3`; discrete counts from a group probability remain `0,3`, not a continuous range. A candidate icon represents an output entry, not the total of multiple outputs of the same item. The Info page lists group paths, local weights, and probability semantics; tooltips retain the group association.

### When no exit rule matches

- `lenient`: retain the workpiece after this operation, including marks, checkpoints, and `progress_actions` results. Continue along its saved specified route (`pending_route`) if present; otherwise follow a random lock or exit edge. Wait only if there is neither a specified route nor an outgoing edge. It cannot automatically return to an earlier node. The target must still process successfully and meet its conditions.
- `strict`: consume the primary workpiece and draw one entry from `scrap`; if no `scrap` is configured, there is no primary output. A lenient exit does not use `scrap`.
- Operation byproducts are retained independently; settlement failure does not undo them.

## 10. Addon Execution Adapters and Full Native Recipe Previews

### Initial release execution boundaries

See Section 4 for execution coverage. Addon recipes that use supported native types are validated by the same rules; addon-specific custom processing types can only be attempted in generic JEI previews. A preview does not mean a machine can execute a graph node.

The dedicated CCB and Fantasizing execution adapters have been removed; protection of graph-progress workpieces by those machines is no longer promised. The fallback adapter code is not included in the initial release.

### Full native recipe reference: preview and verified execution paths

`recipe_id` references a loaded native recipe. Its own CODEC reads and preserves the additional fields, without a separate JSON parser for each addon. **Execution still needs an adapter for the machine.** Recognizing a serializer, `ProcessingRecipe`, or `IAssemblyRecipe` does not enable graph processing; other types can only be previewed where a layout is available.

The complete script below references Create's built-in filling recipe and shows sweet berries plus 250 mB of chocolate as inputs:

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:paper')
        .ingredient({ item: 'minecraft:sweet_berries' })
        .node('native_filling', {
            type: 'create:filling',
            recipe_id: 'create:filling/chocolate_glazed_berries'
        }, { start: true, finish: true })
        .output({ results: [{ item: 'create:chocolate_glazed_berries' }] });

    event.custom(graph.build()).id('my_pack:preview/chocolate');
});
```

`recipe_id` points to a loaded recipe, not an item ID. `type` must match that recipe's **serializer ID**, which is not necessarily the JEI category ID.

Reference mode differs from inline mode:

- The declaration permits only `type` and `recipe_id`. Configure extra conditions in the referenced native recipe; do not merge override fields such as `ingredients` into the declaration.
- Display preserves the native JEI definition. Execution uses a separate native copy: logical item input 0 becomes the primary workpiece, and output 0 is replaced with graph progress/settlement output; all other native ingredients, fluid conditions, and secondary outputs remain. The original recipe is not modified.
- The graph uses the native JEI layout and ingredient hints. Ingredient slots can query recipes/uses, and native fluid fields retain their types and amounts. There are no addon-specific multiplier parameters.
- Settlement outputs at the bottom of the graph still come from `.output(...)`; native recipe outputs are not copied automatically.
- Adapted types must still pass machine-role, capacity, fluid-output, and other checks before a reference can execute. Any failure disables the entire graph and reports the reason. A recipe reference does not replace a machine adapter.
- Removing a native reference, replacing the recipe under the same ID, or reloading data packs invalidates old graph operations. Use a normal data-pack reload when publishing/changing recipes; directly replacing RecipeManager does not guarantee that third-party private recipe indexes are rebuilt.
- The reference cannot be created if the target recipe is missing, the serializer does not match, or the required mod is absent. If no matching native JEI layout is available, the node remains unavailable with a hint.

Generic previews read native JEI layouts and do not enable execution based on mod name. If the native layout is absent or fails to create, the node remains unavailable with a hint.

Section 4 lists execution support: twelve Create processing types; standard inventory, crafting-table, and Crafter recipes; the three vanilla furnace processes; campfires; the vanilla stonecutter; standard smithing; dyeing, repair, and trims; and vanilla anvils and brewing stands. Eligible nodes can also run on the corresponding Create replacement machines, including fan smoking and smelting.

Multi-workpiece merges need an adapted processing path and explicit roles. Fluids cannot carry graph progress. A native recipe preview does not mean its machine supports graph execution.

## 11. Direct JSON, Editing, and Removal

### Without the builder

You can also use `event.custom` directly; it uses the same format as graphs generated by the builder:

```javascript
ServerEvents.recipes(function (event) {
    event.custom({
        type: 'graph_assembly:assembly',
        ingredient: { item: 'minecraft:iron_ingot' },
        transitional_item: 'minecraft:iron_nugget',
        nodes: [{
            id: 'press',
            start: true,
            finish: true,
            recipe: { type: 'create:pressing' }
        }],
        edges: [],
        outputs: [{ results: [{ item: 'minecraft:gold_ingot' }] }]
    }).id('my_pack:graph/direct_json');
});
```

This example uses the same ingredient and pressing entry as Section 2; **enable one or the other, not both**.

### Removing an existing graph

Remove by exact recipe ID, for example, to remove the mod's example graph:

```javascript
ServerEvents.recipes(function (event) {
    event.remove({ id: 'example:script_graph' });
});
```

When editing an existing graph, update its original script. To replace a data-pack graph, remove it by ID, then register the complete graph under the same ID. Do not rely on generic `replaceInput` / `replaceOutput` to traverse and rewrite nested nodes, conditions, and settlement pools.

For data packs, the 1.21.1 path is `data/<namespace>/recipe/<path>.json`, where `recipe` is singular. Do not maintain different versions of the same ID in both data packs and KubeJS.

## 12. Reloading, Troubleshooting, and Release Checks

### Debugging order

1. Run `/reload` and confirm KubeJS reports no script-loading errors.
2. An administrator runs `/graph_assembly compatibility` to inspect execution adapters and disable reasons.
3. Check the instance/server `logs/latest.log` and `logs/kubejs/server.log`; search for the recipe ID, `Disabled graph`, `Ambiguous`, or the specific field path.
4. Test a complete machine chain using a **new entry ingredient**, and confirm the workpiece tooltip, auxiliary consumption, and finish output.
5. With JEI installed, open “Graph Assembly” from an ingredient's uses or a final product's recipes. Nodes appear as points at low zoom and expand when zoomed in. Settlement information is associated with outputs on JEI's native Info page; there is no separate Settlement button at the top of the canvas.

### Common issues

| Symptom | What to check |
|---|---|
| `GraphAssembly is not defined` | Confirm the mod and matching KubeJS version are installed and the script is server-side; check plugin-loading logs. |
| `.build()` reports a node or field-path error | Check node uniqueness, edge targets, entry ingredients, and `requirements` nesting. `graph_assembly:script_validation` is the builder's validation name, not the final recipe ID. |
| Visible in JEI, but the machine does not process it | Check whether it is a preview-only reference or an unsupported device. Use the compatibility command; visibility does not prove executability. |
| `Deploying requires exactly one auxiliary item ingredient` | In the Deployer's inline `ingredients`, list one auxiliary ingredient only; do not list the primary workpiece. |
| `Spout requires exactly one fluid ingredient...` | Put exactly one fluid in `ingredients`; do not specify a secondary output. |
| `Millstone requires processing_time > 0` | Add a positive `processing_time`. |
| `workpiece_inputs.item_input ... exceeds native item input count` | Use decoded logical item indexes, with 0 as primary. Fluids are not counted; do not use physical slot indexes. |
| Workpieces of the same item are in a Basin but do not merge | Check role declarations, graph identity, current node, and conditions. First inspect `pending_route` target and source-operation configuration (a specified edge must also exist); if no route is specified, inspect the random lock/ordinary outgoing edges. Ordinary undeclared auxiliary ingredients do not accept progress components. |
| `Ambiguous ... conditions` | Entry or choice branches cannot be distinguished. Use different auxiliary ingredients/devices/entries, or set the source node's `branch_mode: 'random'` when random selection is actually intended. |
| Workpiece reaches a finish but remains a workpiece | Check `finish`, conditions, and entry/finish restrictions; lenient failure retains the workpiece by default. |
| Checkpoint conditions never match | Entries and finishes are recorded too; ordinary nodes need `checkpoint: true`. Check whether extra loop records invalidate `exact`. |
| A prohibited-visit condition in `checkpoint_counts` does not block the route | Is checkpoint tracking enabled on that node? Counts use the retained full checkpoint sequence, not ordinary recent history. Check whether `clear_checkpoint` / `pop_checkpoint` removed the record and whether other ANDed conditions are satisfied. |
| A random branch always permits only one step | Expected: the target is locked. Queries and `/reload` do not reroll it. |
| Old workpiece route is invalid or stopped | Check the original recipe, entry, and current node. If `pending_route` is present, verify its target, specified edge (if any), and the source node's `progress_actions` are still consistent; otherwise check the random lock/ordinary outgoing edges. A specified route takes priority but does not bypass the target's actual processing or conditions. Restore the relevant IDs/structure or test the new graph with a new ingredient. |

`/reload` does not clear progress on existing workpieces. When publishing updates, changing recipe IDs, removing nodes, or changing random connections may prevent old workpieces from continuing; do not use recipe reload as a workpiece migration tool.

### Before releasing with a modpack

- [ ] Pin and document Minecraft, loader, Create, KubeJS, Rhino, and optional JEI versions.
- [ ] Include required `server_scripts`; when using custom items, also include `startup_scripts`, names, and texture resources.
- [ ] Disable demo scripts and duplicate recipes with the same entry; replace example namespaces and outputs.
- [ ] Put all settlement conditions in `requirements`.
- [ ] Check that no production graph is disabled; test each entry, branch, auxiliary ingredient, output capacity, and finish condition.
- [ ] Test both preparation routes in multi-workpiece graphs, same-graph roles, and rejection of incorrect roles; confirm only primary quality/history is inherited and range endpoints/prohibited-visit conditions behave as intended.
- [ ] Describe preview-only graphs separately from executable recipes; do not list third-party previews as machine-execution compatibility.
- [ ] Keep IDs stable when updating existing worlds and verify whether old workpieces can continue.

### One graph producing three items by route

Copy the [three-path script](../../examples/kubejs/three_path_outputs.js). See the [three-path guide](examples/three-path-outputs.md) for operation steps and independent scrap pools. One Raw Iron entry takes the cutting, Deployer, or filling route and settles at its own strict finish into an Iron Ingot, Gold Ingot, or Diamond; over-processing or insufficient visits go to that finish's scrap pool.

`scrap` belongs to a finish node, so this example uses three finishes without a conditional-scrap field. Each finish's `outputs` checks the ordered route, mark ranges, and checkpoint counts. Graph-level `outputs` stays empty so an unconditional rule cannot turn a failed route into a successful settlement.

The overview shows all successful candidates. Selecting a finish shows only its successful candidates and scrap pool; use `+N` to expand a long list. Full conditions are on JEI's native Info page. Entry candidates list only entry ingredients; check node details for auxiliary workpieces, fluids, and other inputs.

If the finish list is too long, drag the Create-native scrollbar on the right, click its track, or use the wheel; page through the candidate-item overlay with the buttons or PageUp/PageDown. Resizing the window preserves the selected finish and graph view. Items in the overlay continue to support JEI's native recipe/uses queries.

See the [Recipe Format](../reference/recipe-format.md) for complete field definitions; scripts: [Basic example](../../examples/kubejs/graph_assembly.js), [Conditional settlement](../../examples/kubejs/item_first.js), [Two workpieces + range conditions](../../examples/kubejs/multi_workpiece.js), and [Three paths + independent scrap pools](../../examples/kubejs/three_path_outputs.js). These examples are not enabled automatically; the mod also includes the following graph-filter tutorial recipe.

## 13. Built-in Graph Filter: Two-Workpiece Tutorial Recipe

No KubeJS or JEI is required; the mod supplies `graph_assembly:graph_filter` through a native data pack. Total cost: 1 Attribute Filter (`create:attribute_filter`), 1 Andesite Alloy, and 2 Infested blocks; guaranteed output: **1 unconfigured Graph Assembly Filter**. There is no random scrap, failure pool, or extra byproduct.

| Route | Machine and steps | Workpiece |
| --- | --- | --- |
| Body | Attribute Filter → Mechanical Press pressing → Mechanical Saw cutting | `incomplete_graph_filter`, first `body_start`, then `body_ready` |
| Insert | Andesite Alloy + 2 Infested blocks → Basin mixing without a heat source → Mechanical Press pressing | `infested_filter_insert`, first `insert_start`, then `insert_ready` |
| Merge | Two prepared workpieces → Basin compacting | `assemble`, outputs `graph_filter` |

Both semi-finished item IDs are in the `graph_assembly` namespace. Each workpiece keeps its own progress. The final input roles validate both the current node and the complete ordered checkpoint history: the primary must be `body_start → body_ready`, and the auxiliary must be `insert_start → insert_ready`. Final settlement inherits only the primary context; auxiliary history is not merged. Raw ingredients, unprepared semi-finished items, items without progress, and same-item workpieces from another graph cannot substitute. If output space is full, processing waits without consuming inputs.

Entry ingredients are not intermediate items: `body_start` reads the graph-level `ingredient` (Attribute Filter), while `insert_start` uses its node-level `ingredient` (Andesite Alloy). Keep `insert_start.transitional_item` as `graph_assembly:infested_filter_insert`; otherwise the first operation uses the graph-level default body carrier. This does not require an insert item as an entry ingredient. Later `insert_ready` does not need to repeat the carrier; actual processing and in-graph preview both continue to use the insert branch's carrier. Do not add the primary ingredient or carrier again to `recipe.ingredients`; that array lists only the extra auxiliary ingredients consumed in this step.

`graph_assembly:filter_infested_blocks` is an item tag that includes seven vanilla infested blocks by default: Stone, Cobblestone, Deepslate, Stone Bricks, Mossy Stone Bricks, Cracked Stone Bricks, and Chiseled Stone Bricks. The two infested inputs may use the same or different variants, or come from a stack of 2. The mixing entry's logical primary input is always Andesite Alloy; infested blocks are ordinary auxiliary ingredients. The recipe does not create a single-input graph entry for bare infested blocks and does not remove or take over Create: Enchantment Industry's four-infested-stone compacting or infested-cobblestone-crushing recipes.

### Replace with KubeJS; do not register a duplicate

To customize it, copy [filter_crafting.js](../../examples/kubejs/filter_crafting.js) to the instance's `kubejs/server_scripts/`, then run `/reload`. The script removes the built-in recipe by ID and registers the complete graph under the **same** `graph_assembly:graph_filter` ID. It adds the seven infested items to the same-named tag with `ServerEvents.tags('item', ...)`. The mod registers the two dedicated semi-finished items; no extra `startup_scripts` are needed. Do not rename this graph and enable it alongside the built-in graph, or the shared entry will be ambiguous.

Native data-pack paths: `data/graph_assembly/recipe/graph_filter.json` and `data/graph_assembly/tags/item/filter_infested_blocks.json`. Edit the tag to change the allowed infested ingredients. Two separate Ingredient entries each consume one item, for a total of two; a single tag ingredient consumes only one. Edit nested role and checkpoint rules in the graph definition rather than relying on a generic recipe-replacement function.

### Planar edge waypoints

An edge's optional `waypoints` is `[[x, y], ...]`, using the same planar coordinate system as node `position`. At most 64 coordinate pairs are allowed; each number must be finite and have an absolute value no greater than `1000000`. The displayed route runs from the node through each waypoint to the target. **Waypoints affect only planar drawing and hit testing**; the spherical view ignores them. They do not add processing steps, connectivity, checkpoints, random weights, or ingredient consumption.

The builder supports them as well:

```javascript
graph.edge('body_start', 'body_ready', {
    waypoints: [[-108.675, -231.525], [-61.425, -278.775], [203.175, -278.775], [250.425, -231.525]]
});
```

For a JSON edge with `bidirectional: true`, the reverse edge uses the waypoints in reverse order. Manually designed routes do not automatically offset bidirectional lanes; to separate the two directions, specify two edges and their coordinates independently. Older edges without waypoints retain their existing routing behavior. The tutorial graph's “sus” outline consists of only 5 real nodes and 4 real edges; every corner is a display waypoint.

When laying out a graph, leave room for the **fully expanded preview**, not just the collapsed dots: in this example, Basin mixing/compacting uses Create's native 177×103 layout, while pressing/cutting thumbnails use 49×84. The tutorial graph increases spacing between nodes and waypoints and offsets the merge and mixing areas to prevent neighboring previews and unrelated edges from overlapping. Coordinates and models use the same scale, so zooming in with the wheel cannot fix coordinates that were too dense to begin with.

When a large graph with manually routed waypoints first opens in planar view, it automatically zooms out and centers the outline (the minimum zoom remains 0.25). At zoom 1.25, native machine previews appear, and you can drag to inspect details. Reopening the screen or resizing the window afterward preserves the user's view instead of repeatedly reframing. This initialization does not change the spherical starting camera or older graphs without waypoints; switching between planar and spherical views still preserves the current zoom.
