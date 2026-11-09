# Graph Assembly

English | [简体中文](README.zh_cn.md)

<img src="src/main/resources/logo.png" alt="Graph Assembly icon" width="128" height="128">

Graph Assembly adds graph-based processing recipes to Create! Give your world some more interesting (or downright bizarre) recipes.
As with Create's native assembly recipes, workpieces carry their progress and produce the final results when they reach an exit. Graphs give you more freedom to experiment and take your recipe designs further.

Version: **[0.1.2](https://github.com/FluorineUCK/Graph_Assemble/releases/tag/v0.1.2)**.

## What can you do?

- Choice branches, random branches, conditional routes, and loops: plenty of room for creativity (or cruelty).
- Combine workpieces: multiple workpieces from the same graph can take part in a processing step. Their progress is checked separately, but the output inherits only the primary workpiece's progress. Auxiliary marks and history aren't added to it.
- Clearable marks and checkpoints: a new mechanic! Change an item's marks to get different outputs or send it through different steps. You can clear them again, too.
- Create processing support: pressing, cutting, deploying, filling, milling, mixing, compacting, crushing, sandpaper_polishing, splashing, haunting, and emptying.
- Vanilla machines too? Your inventory, crafting tables, and the vanilla Crafter support shaped and shapeless graph recipes, including dyeing and repair recipes.
  Brewing stands support two primary-workpiece modes. When bottles are the primary workpieces, each keeps its own progress. When the ingredient is the primary workpiece, only one ingredient is consumed. Each bottle starts from that same progress, then advances and produces its results independently, so random outcomes may differ.
  Vanilla furnaces, smokers, blast furnaces, campfires, soul campfires, stonecutters, smithing tables, and anvils also support standard graph processing. Anvils support repairs, enchantment upgrades, renaming, and valid combinations of those operations.
- Tired of doing everything by hand? Run vanilla recipe steps on their Create machine counterparts, including Basins, Mechanical Saws, Mechanical Crafters, and fan-assisted smoking or smelting.
- KubeJS-friendly: use `node(id, KubeRecipe)` to turn a native recipe into a node, or supply recipe JSON with `nativeNode()`.
- Prefer Create's native sequenced assembly? You can register supported processing steps as real Create sequenced assemblies. (If a design issue is making graph recipes a pain to write, please tell me :-( )

Custom machines from Create addons or other mods generally need their own integration to execute graph recipes. Maybe you'd like to add one? PRs are welcome! See the [Compatibility Reference](docs/reference/compatibility.md) for the full scope.

## Getting started

Install this mod, Create, and their dependencies on both client and server. Requirements: Minecraft 1.21.1, NeoForge 21.1.234 or later in the 21.1 line, and Create 6.0.10. JEI 19.39.0.368–19.70.0.469 and KubeJS NeoForge 2101.7.2-build.379 are optional dependencies.

## Documentation and guides

- Players: [Player Guide](docs/guide/player.md)
- Recipe authors: [KubeJS Guide](docs/guide/kubejs.md), [Recipe Format Reference](docs/reference/recipe-format.md)
- Resource-pack authors: [Resource Pack Replacement Guide](docs/guide/resource-packs.md), [Atlas Coordinates](docs/reference/art/atlas.md)
- [Documentation Index](docs/README.md)
- [Contributing Guide](CONTRIBUTING.md)

## Build

- Windows: `./gradlew.bat test build`
- Linux: `bash ./gradlew test build`

## License

This project is licensed under the [MIT License](LICENSE). The derivative canvas-background asset retains its [upstream MIT notice](src/main/resources/META-INF/licenses/create-factory-controller.txt); see [Asset Sources](docs/reference/art/sources.md) for attribution.
