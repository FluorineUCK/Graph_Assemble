// Minecraft 1.21.1 / Create / Graph Assembly / KubeJS server_scripts.
// One graph, three chosen routes, three strict finishes with separate scrap pools.
// Demonstration only: item transformations are not intended as balanced production recipes.
// Put this file in kubejs/server_scripts/, then /reload. No client script is needed.
// See ../three-path-outputs.md for routes, failure cases and pool probabilities.
// Keep the event.custom payload valid JSON so it can also be copied into a datapack.
ServerEvents.recipes(function (event) {
    event.custom({
        "type": "graph_assembly:assembly",
        "ingredient": {"item": "minecraft:raw_iron"},
        "transitional_item": "minecraft:iron_nugget",
        "nodes": [
            {
                "id": "blank", "start": true,
                "recipe": {"type": "create:pressing"}
            },
            {
                "id": "cut", "checkpoint": true, "rewards": {"quality": 1},
                "recipe": {"type": "create:cutting", "processing_time": 40}
            },
            {
                "id": "wire", "checkpoint": true, "rewards": {"quality": 1},
                "recipe": {"type": "create:deploying", "ingredients": [{"item": "minecraft:redstone"}]}
            },
            {
                "id": "fill", "checkpoint": true, "rewards": {"quality": 1},
                "recipe": {
                    "type": "create:filling",
                    "ingredients": [{"type": "neoforge:single", "fluid": "minecraft:water", "amount": 250}]
                }
            },
            {
                "id": "finish_iron", "finish": true, "finish_mode": "strict",
                "recipe": {"type": "create:pressing"},
                "outputs": [{
                    "requirements": {
                        "checkpoints": {"mode": "partial", "nodes": ["blank", "cut", "finish_iron"]},
                        "mark_ranges": {"quality": {"min": 1, "max": 2}},
                        "checkpoint_counts": {"cut": {"min": 1, "max": 2}}
                    },
                    "results": [{"item": "minecraft:iron_ingot"}]
                }],
                "scrap": [
                    {"item": "minecraft:iron_nugget", "count": 3, "weight": 3},
                    {"item": "minecraft:gravel", "count": 1, "weight": 1}
                ]
            },
            {
                "id": "finish_gold", "finish": true, "finish_mode": "strict",
                "recipe": {"type": "create:pressing"},
                "outputs": [{
                    "requirements": {
                        "checkpoints": {"mode": "partial", "nodes": ["blank", "wire", "finish_gold"]},
                        "mark_ranges": {"quality": {"min": 2, "max": 3}},
                        "checkpoint_counts": {"wire": {"min": 2, "max": 3}}
                    },
                    "results": [{"item": "minecraft:gold_ingot"}]
                }],
                "scrap": [
                    {"item": "minecraft:gold_nugget", "count": 2, "weight": 2},
                    {"item": "minecraft:redstone", "count": 1, "weight": 1}
                ]
            },
            {
                "id": "finish_diamond", "finish": true, "finish_mode": "strict",
                "recipe": {"type": "create:pressing"},
                "outputs": [{
                    "requirements": {
                        "checkpoints": {"mode": "partial", "nodes": ["blank", "fill", "finish_diamond"]},
                        "mark_ranges": {"quality": {"min": 1, "max": 1}},
                        "checkpoint_counts": {"fill": {"min": 1, "max": 1}}
                    },
                    "results": [{"item": "minecraft:diamond"}]
                }],
                "scrap": [
                    {"item": "minecraft:quartz", "count": 1, "weight": 1},
                    {"item": "minecraft:flint", "count": 2, "weight": 3}
                ]
            }
        ],
        "edges": [
            {"from": "blank", "to": "cut"},
            {"from": "blank", "to": "wire"},
            {"from": "blank", "to": "fill"},
            {"from": "cut", "to": "cut"},
            {"from": "cut", "to": "finish_iron"},
            {"from": "wire", "to": "wire"},
            {"from": "wire", "to": "finish_gold"},
            {"from": "fill", "to": "fill"},
            {"from": "fill", "to": "finish_diamond"}
        ],
        "outputs": []
    }).id('example:three_path_outputs');
});
