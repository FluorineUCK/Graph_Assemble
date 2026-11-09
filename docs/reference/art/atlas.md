# PNG Atlas Asset Inventory

English | [简体中文](atlas.zh_cn.md)

`GraphGuiAssets.java` defines the GUI atlas regions. Coordinates start at the top left and use logical pixels at 1× scale. The atlas is 1024×512 RGBA and supports high-resolution versions scaled by an integer factor.

## Current Atlas Regions

| Region | X,Y | Size | Nine-slice border |
|---|---:|---:|---:|
| `GRAPH_PANEL` | 0,0 | 320×224 | 11 |
| `FILTER_PANEL` header | 336,0 | 232×95 | — |
| `FILTER_PANEL_UPPER` | 336,95 | 232×20 | — |
| `FILTER_PANEL_MIDDLE` | 336,116 | 232×20 | — |
| `FILTER_PANEL_LOWER` | 336,137 | 232×19 | — |
| `FILTER_PANEL_FOOTER` | 336,156 | 241×30 | — |
| `ADD` | 592,16 | 16×16 | — |
| `REMOVE` / `CLEAR` | 608,16 | 16×16 | — |
| `CONFIRM` | 624,16 | 16×16 | — |
| `SPHERE` | 640,16 | 16×16 | — |
| `PLANE` | 656,16 | 16×16 | — |
| `FILTER_CHOICE` | 592,47 | 115×18 | 8 |
| `FILTER_ICON_SLOT` | 351,33 | 18×18 | — |

`GraphGuiLayout` maintains the canvas, mode controls, and detail area. `FilterScreenLayout` maintains the 241×226 filter panel, preview, and control geometry. The filter's + / !+ controls are at `(179,63)` / `(197,63)`; its three mode buttons are at Y=202, Clear at `(179,202)`, and Confirm at `(208,202)`. These are screen-layout coordinates, not atlas UVs.

## Item Assets

| Asset | Purpose / model reference |
|---|---|
| `textures/item/graph_filter.png` | Graph Assembly Filter; referenced by `models/item/graph_filter_appearance.json` as `graph_assembly:item/graph_filter` |
| `textures/item/processed_filter.png` | Referenced by `models/item/incomplete_graph_filter.json`; Incomplete Graph Filter (primary workpiece) |
| `textures/item/processed_sus.png` | Referenced by `models/item/infested_filter_insert.json`; SUS Powder (auxiliary workpiece) |
| `textures/item/junk.png` | Retained user asset; no production-code or model reference found |

## Usage Boundaries

GUI rendering directly blits `textures/gui/graph_gui.png`; there is no separate Settlement canvas or Settlement button. Machine animations, JEI slots, text, and tooltips are drawn by the game/Java code, not baked into the atlas. No art JSON, GUI `.mcmeta`, or runtime GUI sprite atlas is required. See the [resource-pack guide](../../guide/resource-packs.md) for replacement instructions and [asset sources](sources.md) for the background source.
