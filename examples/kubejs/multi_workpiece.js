// Separate input routes make two workpieces of the SAME graph.
// Keep this example separate from other graphs with overlapping entrance conditions.
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:gold_ingot' })
        .node('body', { type: 'create:pressing' }, {
            start: true, rewards: { quality: 2 }
        })
        .node('insert', { type: 'create:cutting', processing_time: 40 }, {
            start: true, ingredient: { item: 'minecraft:amethyst_shard' },
            rewards: { quality: 99 }
        })
        .node('polish', {
            type: 'create:deploying', ingredients: [{ item: 'minecraft:quartz' }]
        }, { checkpoint: true, rewards: { quality: 2 } })
        .node('join', {
            type: 'create:mixing', ingredients: [{ item: 'minecraft:iron_nugget' }]
        }, {
            checkpoint: true,
            workpiece_inputs: [
                { item_input: 0, current_nodes: ['body', 'polish'] },
                { item_input: 1, current_nodes: ['insert'],
                    requirements: { mark_ranges: { quality: { min: 99, max: 99 } } } }
            ]
        })
        .node('finish', { type: 'create:pressing' }, { finish: true, finish_mode: 'strict' })
        .edge('body', 'polish').edge('body', 'join').edge('polish', 'join')
        .edge('insert', 'join').edge('join', 'finish')
        .output({
            requirements: {
                checkpoints: { mode: 'partial', nodes: ['body', 'join', 'finish'] },
                mark_ranges: { quality: { min: 2, max: 2 } },
                checkpoint_counts: { polish: { max: 0 }, join: { min: 1, max: 1 } }
            }, results: [{ item: 'minecraft:emerald' }]
        })
        .output({
            requirements: {
                checkpoints: { mode: 'partial', nodes: ['body', 'polish', 'join', 'finish'] },
                mark_ranges: { quality: { min: 4, max: 4 } },
                checkpoint_counts: { polish: { min: 1, max: 1 } }
            }, results: [{ item: 'minecraft:diamond' }]
        });
    event.custom(graph.build()).id('example:multi_workpiece');
});
