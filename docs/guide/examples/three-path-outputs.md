# One Graph, Three Path Outputs, Three Scrap Pools

English | [简体中文](three-path-outputs.zh_cn.md)

Script: [kubejs/three_path_outputs.js](../../../examples/kubejs/three_path_outputs.js). Recipe ID: `example:three_path_outputs`.

This example has **one graph, one entry ingredient, and three strict settlement finishes**. It deliberately uses recognizable vanilla items to demonstrate the rules; it is not a balanced production recipe. Graph Assembly, Create, and KubeJS are required.

## Installation

1. Copy the script to `kubejs/server_scripts/three_path_outputs.js` in your instance.
2. Run `/reload` and check for script errors. An administrator can run `/graph_assembly compatibility` to check whether the graph is executable.
3. Use new **Raw Iron `minecraft:raw_iron`**. Do not hand-write progress onto a workpiece. If another custom graph also uses raw iron with a pressing entry step, avoid enabling both at the same time.

## Routes in practice

First press one piece of raw iron on a Depot/belt to pass through `blank` and obtain an iron nugget workpiece with graph progress. Then choose any one of these routes:

| Route | Step and extra consumption | Successful visit range | Settlement step | Successful output |
|---|---|---|---|---|
| Cutting | Mechanical Saw, 40 ticks of recipe processing time per operation | `cut` 1–2 times | Press once more to enter `finish_iron` | Iron Ingot ×1 |
| Assembly | Deployer, consumes Redstone ×1 per operation | `wire` 2–3 times | Press once more to enter `finish_gold` | Gold Ingot ×1 |
| Filling | Spout, consumes 250 mB Water per operation | `fill` exactly once | Press once more to enter `finish_diamond` | Diamond ×1 |

The visit count includes the first entry into that node; it is not the number of additional loops. A self-loop allows further processing, and the final press settles the workpiece. Do not replace the final press with Basin compacting: this example uses `create:pressing`.

```text
Raw Iron → blank (pressing)
                 ├─ cut  ↺ (cut 1–2 times) ──→ finish_iron    → Iron Ingot
                 ├─ wire ↺ (Deployer 2–3 times) → finish_gold    → Gold Ingot
                 └─ fill ↺ (fill with water once) → finish_diamond → Diamond
```

The player's choice of machine selects the branch. All branches use the default `choice` behavior, with no random route selection. Once a route is entered, only that step's loop or its corresponding finish is available; there are no cross-route connections.

Each route step adds `quality: 1` and records a checkpoint. A finish checks all of the following:

- The workpiece must pass through `blank → corresponding step → corresponding finish` in order (`partial` ordered subsequence, allowing valid repetitions).
- `quality` must be within that route's `mark_ranges`.
- The cumulative count of the corresponding checkpoint must be within `checkpoint_counts`.

Ranges include both endpoints, and the conditions are ANDed.

## Per-finish scrap pools

| Finish | Reproducible failure route | Scrap pool drawn only by this finish |
|---|---|---|
| `finish_iron` | Cut 3 or more times, then press | Iron Nugget ×3: weight 3 (75%); Gravel ×1: weight 1 (25%) |
| `finish_gold` | Deployer operation only once, or 4 or more times, then press | Gold Nugget ×2: weight 2 (2/3); Redstone ×1: weight 1 (1/3) |
| `finish_diamond` | Fill 2 or more times, then press | Quartz ×1: weight 1 (25%); Flint ×2: weight 3 (75%) |

**These ratios choose which scrap is drawn after a failure; they are not success probabilities.** A qualifying route always grants its corresponding successful item. A non-qualifying route that reaches its strict finish always fails, then draws once from that finish's pool. The pool does not award both scrap items together.

A strict failure ends graph progress. Even if failed output looks like the intermediate iron nugget, it is an ordinary nugget and no longer carries graph progress. Successful outputs do not carry progress either.

An operation with no legal edge is a stop, not a settlement failure. For example, pressing immediately after `blank` has no legal successor, so a machine cannot choose one of the three finishes arbitrarily; after `cut`, the workpiece also cannot jump directly to `finish_gold`. Do not mistake a stop for a scrap draw.

### Why use three finish nodes?

`scrap` belongs to a finish node, not to a success rule. Three strict finishes therefore provide three separate scrap pools. All three can use pressing: the current branch allows only its own finish, so the machine never has to choose between three matching candidates.

Successful rules are defined in each finish's `outputs`, while graph-level `outputs` remains an empty array. Do not add an unconditional graph-level success rule: graph-level and node-level rules both participate in settlement, so that would also grant a successful output to workpieces that should fail, bypassing the scrap pool.

The current format cannot choose different scrap pools at the same finish based on the failure route. Use separate finishes for separate pools, as this example does.

## JEI finish-linked display

- All three successful outputs and all six scrap items can be associated with this graph; changing the selected finish does not change JEI's complete index.
- The default “All finishes” view shows all three successful candidates. It does not award all three outputs or predict the current workpiece's success probability.
- Choose a finish with the lower-right selector or a finish node in the graph; the successful candidates and scrap pool at the bottom switch together. For example, the gold finish shows only Gold Ingot and Gold Nugget/Redstone, not Quartz.
- Click `+N` to expand a long candidate list. The finish list can scroll and the candidate layer can paginate. Actual item slots retain JEI recipe/uses interactions; navigation buttons are not item slots.
- Esc closes the expanded layer first. Changing finishes does not reset graph zoom, panning, or spherical orientation.
- The native Info page still lists the full successful conditions and scrap pool for each finish. The footer shows candidates and a summary; it does not combine all possible rules into one condition or a fixed probability.
- The entry shows entry-ingredient candidates, not the complete material list. Check the relevant node preview/details for auxiliary workpieces, Redstone, Water, and other inputs.
- The footer uses existing PNGs and Create-native buttons without replacing the hand-authored atlas. Crossing edges and clipped models need to be addressed in the graph layout.

## Minimum verification checklist

- [ ] `blank → cut → finish_iron`, including two cuts, yields an Iron Ingot.
- [ ] `blank → wire → wire → finish_gold`, including three Deployer operations, yields a Gold Ingot.
- [ ] `blank → fill → finish_diamond` yields a Diamond.
- [ ] Three cuts, one/four Deployer operations, and two fills each enter only their own scrap pool.
- [ ] Successful and failed outputs carry no graph progress, and item counts match the table.

Verify the three successful routes and their corresponding scrap pools against the checklist, one item at a time.
