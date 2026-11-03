# Graph Assembly

English | [简体中文](README.zh_cn.md)

<img src="src/main/resources/logo.png" alt="Graph Assembly icon" width="128" height="128">

Graph Assembly adds directed-graph processing recipes for Minecraft 1.21.1, NeoForge 21.1.234, and Create 6.0.10. Graph nodes represent processing steps; workpieces carry progress and are settled when they reach a finish node. Java 21 is required.

Version: **[0.1.0](https://github.com/FluorineUCK/Graph_Assemble/releases/tag/v0.1.0)**.

## Supported features

- Choice branches, random branches, and loops; finish outputs and scrap pools can depend on marks, ordered checkpoints, and visit counts.
- Primary and auxiliary workpieces are validated separately; merged outputs inherit the primary workpiece's progress. Successful processing can clear marks or checkpoints and specify the next step.
- Seven Create processing types: pressing, cutting, deploying, filling, milling, mixing, and compacting.
- Standard shaped and shapeless graph crafting in inventories, crafting tables, and the vanilla Crafter; supports dyeing and repairing recipes while preserving native results. The Crafter processes one batch per redstone rising edge.
- The vanilla brewing stand supports two primary-workpiece modes: bottles as primary workpieces retain their individual progress; with the ingredient as the primary workpiece, only one ingredient is consumed and its graph progress is distributed to every bottle, which then advances and settles independently.
- Standard graph processing for vanilla furnaces, smokers, blast furnaces, campfires, soul campfires, stonecutters, smithing tables, and anvils. Campfire slots are timed independently; anvils support repair, enchantment enhancement, explicit renaming, and valid combinations.
- Vanilla recipes can run graph steps on corresponding Create replacement machines, including Basins, Mechanical Saws, Mechanical Crafters, and fan-assisted smoking/smelting.
- KubeJS recipe authors can use `node(id, KubeRecipe)` to use a native recipe as a node, or `nativeNode()` to provide recipe JSON. Ingredient inputs support item/material shorthand.
- Graph progress and settled outputs are items; fluids are only used for native auxiliary consumption in adapted steps. KubeJS and JEI are optional.
- Each filter supports up to 256 unique targets. See the [Player Guide](docs/guide/player.md).

Addon custom machines do not automatically support graph recipe execution. See the full [Compatibility Reference](docs/reference/compatibility.md).

## Installation

Install this mod, Create, and their dependencies on both client and server. Requirements: Minecraft 1.21.1, NeoForge 21.1.234 or later in the 21.1 line, and Create 6.0.10. JEI 19.39.0.368 and KubeJS NeoForge 2101.7.2-build.379 are optional dependencies.

## Documentation

- Players: [Player Guide](docs/guide/player.md)
- Recipe authors: [KubeJS Guide](docs/guide/kubejs.md), [Recipe Format Reference](docs/reference/recipe-format.md)
- Resource-pack authors: [Resource Pack Replacement Guide](docs/guide/resource-packs.md), [Atlas Coordinates](docs/reference/art/atlas.md)
- [Documentation Index](docs/README.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Examples Index](examples/README.md)

## Build

- Windows: `./gradlew.bat test build`
- Linux: `bash ./gradlew test build`

## License

This project is licensed under the [MIT License](LICENSE). The derivative canvas-background asset retains its [upstream MIT notice](src/main/resources/META-INF/licenses/create-factory-controller.txt); see [Asset Sources](docs/reference/art/sources.md) for attribution.
