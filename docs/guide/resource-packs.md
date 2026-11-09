# PNG Atlas Replacement Guide

English | [简体中文](resource-packs.zh_cn.md)

## Routine art delivery

Open and edit these files directly with Aseprite, Krita, Photoshop, or similar software:

1. `src/main/resources/assets/graph_assembly/textures/gui/graph_gui.png`
2. `src/main/resources/assets/graph_assembly/textures/item/graph_filter.png`
3. `src/main/resources/assets/graph_assembly/textures/gui/canvas_background.png` (the graph canvas's separate tiled background)

The original GUI image is **1024×512 RGBA**. Keep the position and size of each region and its transparent padding when editing the PNG. See the [asset inventory](../reference/art/atlas.md) for UV coordinates. If you move an atlas region, update the Java UV coordinates as well.

- The atlas contains panels, button bases, and icon regions. Icons are transparent; do not draw text into the PNG.
- Create's native controls draw button bases and interaction states such as normal, hovered, pressed, and disabled. This atlas provides only the corresponding transparent icons; it has no separate four-state button or selection-indicator regions.
- Java draws nine-slice panels; preserve the atlas edges because only the center stretches.
- You may deliver a full **2048×1024**, 3072×1536, or other integer-scale high-resolution image. Scale all content and UV regions proportionally; no Java changes are needed. Incorrect aspect ratios or non-integer scales fall back to the original.
- Keep UV regions and control positions/hitboxes unchanged.

## External resource pack: replacement PNGs only

```text
my-art-pack/
  pack.mcmeta
  assets/graph_assembly/textures/gui/graph_gui.png
  assets/graph_assembly/textures/gui/canvas_background.png
  assets/graph_assembly/textures/item/graph_filter.png
```

The root `pack.mcmeta` describes the Minecraft resource pack:

```json
{"pack":{"pack_format":34,"description":"Graph Assembly PNG atlas"}}
```

The filter item model is defined in the Minecraft-standard `graph_filter_appearance.json`. Its `layer0` references `graph_assembly:item/graph_filter`, so the model file selects the texture and the PNG provides its appearance.

The right side of the filter screen displays the actual item stack being edited, rendered with Create's native `GuiGameElement`. Replacing the item PNG therefore updates the held item, inventory icon, and right-side preview together. For a three-dimensional appearance, use a reference to a Minecraft-standard item model; do not draw a fixed preview PNG.

In the resource-pack screen, place the art pack **above Mod Resources**. The GUI atlas replaces the image as a whole, so preserve regions you do not intend to modify.

## Reload and inspect

### Movable canvas background

`canvas_background.png` is a separate **16×16 PNG**. It can be replaced with an integer-scale square such as 32×32 or 48×48; its display period remains 16 logical pixels and changes with canvas zoom. Keep all four edges seamless.

In planar mode, the background and nodes share the pan and zoom origin; the frame, buttons, and footer stay in place. In spherical mode, the background scales with zoom but does not pan as the sphere rotates. Settlement information is on JEI's native Info page, with no separate settlement canvas. If the background is missing or has an invalid size, the original panel fill remains.

A shader registered in Java handles background tiling and pixel-center sampling. The dynamic overlay opacity is set in `GuiColors` under `canvas.background`.

See [asset sources](../reference/art/sources.md) for background references and license records. The current background PNG is a derivative of that reference.

After returning to the world, press F3+T to reload resources, then reopen the screen to inspect the result.

Check GUI scales 2 and 3, window resizing, normal/hovered/pressed/disabled states, transparent icon edges, and item appearance. PNGs determine static appearance; default dynamic node, edge, and text colors are centralized in `GuiColors`. This version does not provide an external color JSON.

Create's native animations and JEI ingredient slots are still drawn by the game.
