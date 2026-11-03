# Examples Index

English | [简体中文](README.zh_cn.md)

Each example is enabled independently; these are not a complete modpack loaded automatically. Check the entry ingredients and processing steps before starting, and avoid enabling graphs with conflicting entry conditions at the same time.

## Data-pack recipes

Place files in the corresponding data pack's `data/<namespace>/recipe/` directory, then reload. Minecraft 1.21.1 uses the singular directory name `recipe`.

| File | Contents |
|---|---|
| [`datapack/branching.json`](datapack/branching.json) | Branching graph recipe |
| [`datapack/item-first.json`](datapack/item-first.json) | Item-first condition example |
| [`datapack/progress-actions.json`](datapack/progress-actions.json) | Mark/checkpoint/route actions after processing |

## KubeJS scripts

Copy the required files to `kubejs/server_scripts/`, then run `/reload`.

| File | Contents |
|---|---|
| [`kubejs/graph_assembly.js`](kubejs/graph_assembly.js) | Basic graph |
| [`kubejs/native_recipes.js`](kubejs/native_recipes.js) | Upstream KubeJS recipe objects, complete native templates, and item shorthand; KubeJS Create is not required |
| [`kubejs/item_first.js`](kubejs/item_first.js) | Conditional settlement |
| [`kubejs/multi_workpiece.js`](kubejs/multi_workpiece.js) | Two workpieces and range conditions |
| [`kubejs/three_path_outputs.js`](kubejs/three_path_outputs.js) | Three paths and independent finish scrap pools |
| [`kubejs/nested_probability_outputs.js`](kubejs/nested_probability_outputs.js) | Nested probability settlement |
| [`kubejs/filter_crafting.js`](kubejs/filter_crafting.js) | Tutorial graph for a custom built-in filter recipe |

`filter_crafting.js` removes and replaces the built-in tutorial recipe using the same `graph_assembly:graph_filter` ID. Do not use a new ID and enable it alongside the built-in graph, or entry conditions will conflict. See the full [KubeJS Guide](../docs/guide/kubejs.md).
