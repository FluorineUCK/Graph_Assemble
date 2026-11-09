# Changelog

English | [简体中文](CHANGELOG.zh_cn.md)

## 0.1.2 — 2026-11-09

### Marks and graph rules

- Unified node mark fields across JSON and KubeJS: `mark_amounts` defines the amounts granted per application, and `max_mark_applications` caps applications at each node. Workpiece state stores per-node application counts in `mark_grant_counts`.
- Clearing marks does not reset application counts. Granting several marks in one application increments the count once. Unknown node options report their field paths instead of silently changing recipe behavior.
- Edges can require marks, checkpoints, and their counts, alongside choice branches, random branches, and loops.

### Machines and sequenced assembly

- Added graph execution for crushing wheels, handheld and Deployer sandpaper polishing, fan washing and haunting, and item drains, retaining machine timing, durability, fluid, and capacity checks.
- Supported operations can run in real `create:sequenced_assembly` recipes through `graph_assembly:sequence_step`. Steps without native animations display the target block at the corresponding position.
- Consolidated shared machine bridging and grouped vanilla adapters under `vanilla`. Campfire types, configuration, parsing, and checks now belong to the campfire adapter. Existing machine compatibility is retained.
- Fixed workpiece refunds and duplicate remainders when sandpaper processing is blocked.

### Scripting, documentation, and internals

- KubeJS supports fluent graph registration, separate success and scrap pools for each finish, and native sequenced assembly builders without requiring `event.custom()` for these features.
- Split core responsibilities into marks, checkpoints, input/output, routing, and settlement, with recipe and workpiece JSON handled by dedicated codecs.
- Updated both READMEs, KubeJS guides, recipe references, and examples with field semantics, execution coverage, and machine limits.

## 0.1.1 — 2026-11-04

### Fixed

- Relaxed the optional JEI dependency from an exact version pin to `19.39.0.368`–`19.70.0.469` (inclusive), allowing modpacks to use newer compatible JEI builds.
- Recipe formats, processing behavior, and Mixin implementations are unchanged.

## 0.1.0 — 2026-11-03

### Graph rules and recipes

- `progress_actions` can change marks, checkpoints, or subsequent routes in sequence after successful processing; each target must still meet its own conditions.
- Primary and auxiliary workpieces are validated separately; merged outputs inherit only the primary workpiece's progress.
- Settlement conditions support ordered checkpoints, visit counts, and mark ranges; each finish can define its own successful outputs and scrap pool.
- Supports graph processing for standard shaped and shapeless inventory/crafting-table recipes, as well as vanilla Crafter, dyeing, and repairing recipes.
- The vanilla brewing stand supports bottle-primary mode (each bottle retains its progress) and ingredient-primary mode (consume one ingredient, distribute the same graph progress to every bottle, then advance and settle each independently).

### Machine support

- Supports graph processing for vanilla furnaces, smokers, blast furnaces, campfires, soul campfires, stonecutters, smithing tables, and anvils. Campfires support `normal`, `soul`, and `any` types, time four slots independently, and preserve native drops; stonecutters use the native selection list; smithing tables support upgrades and trims while preserving armor and its native components; anvils support repair, enchantment enhancement, explicit renaming, and valid combinations while preserving native results and costs.
- Standard vanilla recipes can run graph steps on corresponding Create replacement machines, including Basin mixing/compacting, Mechanical Saws, Mechanical Crafters, and fan-assisted smoking/smelting.
- Removed the dedicated Fantasizing graph-execution adapter; generic JEI previews remain.

### Scripting

- KubeJS supports embedding an upstream recipe object with `node(id, KubeRecipe)`, reusing native parameters and ingredient syntax; `nativeNode()` accepts complete JSON, does not insert the primary workpiece again, and does not register an ordinary recipe separately.
- KubeJS ingredient inputs support IDs, tags, Ingredient objects, and item/material shorthand; settlement supports item shorthand and preserves counts and components.
- KubeJS special input-consumption/result callbacks are unsupported and are explicitly rejected rather than silently ignored.

### Interface

- Added the project icon to the mod list and repository documentation.
- Primary and auxiliary workpiece hints for merge steps now list the other required workpiece, and the stop message for ready auxiliary workpieces is corrected.
- Graph filters use Create's native Shift summary, colors, and line wrapping; empty configurations no longer show zero-item conditions.
- Removed hover tooltips from the candidate overlay's `×` and `+N` controls; click behavior remains.
- Graph preview adds a Create-native view-reset button that restores the initial view and zoom for the current mode while preserving node and finish selection.
