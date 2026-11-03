// Optional KubeJS 7 / MC 1.21.1 replacement of the bundled teaching recipe.
// Copy to kubejs/server_scripts/filter_crafting.js. No startup item registration needed.
// Use the SAME recipe ID: do not register another graph with these entrances.
ServerEvents.tags('item', function (event) {
    [
        'minecraft:infested_stone', 'minecraft:infested_cobblestone',
        'minecraft:infested_deepslate', 'minecraft:infested_stone_bricks',
        'minecraft:infested_mossy_stone_bricks', 'minecraft:infested_cracked_stone_bricks',
        'minecraft:infested_chiseled_stone_bricks'
    ].forEach(function (item) { event.add('graph_assembly:filter_infested_blocks', item); });
});

ServerEvents.recipes(function (event) {
    event.remove({ id: 'graph_assembly:graph_filter' });
    // Two independent carriers, five real operations, four real edges.
    // Edge waypoints are display-only plane coordinates, never additional steps.
    // Leave room for full 177x103 basin previews, not just collapsed node markers.
    event.custom({
  "type": "graph_assembly:assembly",
  "ingredient": { "item": "create:attribute_filter" },
  "transitional_item": "graph_assembly:incomplete_graph_filter",
  "nodes": [
    {
      "id": "body_start",
      "start": true,
      "transitional_item": "graph_assembly:incomplete_graph_filter",
      "position": [-108.675, -193.725],
      "recipe": { "type": "create:pressing" }
    },
    {
      "id": "body_ready",
      "checkpoint": true,
      "position": [250.425, 99.225],
      "recipe": { "type": "create:cutting", "processing_time": 40 }
    },
    {
      "id": "insert_start",
      "start": true,
      "ingredient": { "item": "create:andesite_alloy" },
      "transitional_item": "graph_assembly:infested_filter_insert",
      "position": [-210, 127.575],
      "recipe": {
        "type": "create:mixing",
        "ingredients": [
          { "tag": "graph_assembly:filter_infested_blocks" },
          { "tag": "graph_assembly:filter_infested_blocks" }
        ],
        "heat_requirement": "none"
      }
    },
    {
      "id": "insert_ready",
      "checkpoint": true,
      "position": [28, -61.425],
      "recipe": { "type": "create:pressing" }
    },
    {
      "id": "assemble",
      "finish": true,
      "finish_mode": "strict",
      "position": [-108.675, -42.525],
      "recipe": {
        "type": "create:compacting",
        "ingredients": [{ "item": "graph_assembly:infested_filter_insert" }],
        "heat_requirement": "none"
      },
      "workpiece_inputs": [
        {
          "item_input": 0,
          "current_nodes": ["body_ready"],
          "requirements": { "checkpoints": { "mode": "exact", "nodes": ["body_start", "body_ready"] } }
        },
        {
          "item_input": 1,
          "current_nodes": ["insert_ready"],
          "requirements": { "checkpoints": { "mode": "exact", "nodes": ["insert_start", "insert_ready"] } }
        }
      ],
      "outputs": [
        {
          "start": "body_start",
          "finish": "assemble",
          "requirements": { "checkpoints": { "mode": "exact", "nodes": ["body_start", "body_ready", "assemble"] } },
          "results": [{ "item": "graph_assembly:graph_filter", "count": 1 }]
        }
      ]
    }
  ],
  "edges": [
    {
      "from": "body_start", "to": "body_ready",
      "waypoints": [[-108.675, -231.525], [-61.425, -278.775], [203.175, -278.775], [250.425, -231.525]]
    },
    {
      "from": "body_ready", "to": "assemble",
      "waypoints": [[250.425, 278.775], [118.125, 278.775], [118.125, 165.375], [14.175, 165.375], [14.175, 278.775], [-108.675, 278.775]]
    },
    {
      "from": "insert_start", "to": "insert_ready",
      "waypoints": [[-250.425, 127.575], [-250.425, -108.675], [-174.825, -108.675], [-174.825, -137.025], [-4.725, -137.025], [-4.725, -174.825], [165.375, -174.825], [203.175, -137.025], [203.175, -61.425]]
    },
    {
      "from": "insert_ready", "to": "assemble",
      "waypoints": [[-70.875, -61.425]]
    }
  ],
  "outputs": []
}
    ).id('graph_assembly:graph_filter');
});
