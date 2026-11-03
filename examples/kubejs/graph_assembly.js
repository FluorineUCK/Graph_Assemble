// Optional example: copy into kubejs/server_scripts/ to enable; not installed automatically.
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:iron_ingot' })

        .node('start', { type: 'create:pressing' }, { start: true })
        .node('finish', { type: 'create:cutting', processing_time: 40 }, { finish: true })

        .edge('start', 'finish')

        .output({ requirements: { marks: {}, checkpoints: ['start', 'finish'] },
                  results: [{ item: 'minecraft:gold_ingot' }] });
    event.custom(graph.build()).id('example:script_graph');
});
