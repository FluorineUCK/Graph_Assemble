ServerEvents.recipes(function (event) {
    event.custom(GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({item: 'minecraft:copper_ingot'})
        .node('start', {type: 'create:pressing'}, {start: true, mark_amounts: {precision: 2}})
        .node('finish', {type: 'create:cutting', processing_time: 40}, {finish: true})
        .edge('start', 'finish')
        .output({requirements: {mode: 'partial', marks: {precision: 1}, checkpoints: ['start', 'finish']},
                 results: [{item: 'minecraft:diamond'}]}).build()).id('example:item_first');
});
