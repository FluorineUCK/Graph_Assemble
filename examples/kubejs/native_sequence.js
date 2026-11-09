// Standalone example; enable either this script or the equivalent datapack, not both.
ServerEvents.recipes(function (event) {
    event.custom({
  "type": "create:sequenced_assembly",
  "ingredient": {
    "item": "minecraft:iron_ingot"
  },
  "transitional_item": {
    "id": "minecraft:iron_nugget"
  },
  "loops": 2,
  "sequence": [
    {
      "type": "graph_assembly:sequence_step",
      "step": {
        "recipe": {
          "type": "minecraft:smelting",
          "ingredient": {
            "item": "minecraft:iron_ingot"
          },
          "result": {
            "id": "minecraft:iron_nugget"
          },
          "cookingtime": 200,
          "experience": 0.0
        }
      }
    },
    {
      "type": "create:pressing",
      "ingredients": [
        {
          "item": "minecraft:iron_nugget"
        }
      ],
      "results": [
        {
          "id": "minecraft:iron_nugget"
        }
      ]
    }
  ],
  "results": [
    {
      "id": "minecraft:gold_ingot",
      "chance": 0.8
    },
    {
      "id": "minecraft:iron_nugget",
      "chance": 0.2
    }
  ]
}).id('example:native_sequence');
});
