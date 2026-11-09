// Five additional real Create hosts; no event.custom is needed.
// Enable intentionally: this registers a raw iron -> diamond/gold processing chain.
ServerEvents.recipes(function (event) {
    GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:raw_iron')
        .node('crush', {type: 'create:crushing', processing_time: 100}, {start: true, mark_amounts: {quality: 1}})
        .node('polish', {type: 'create:sandpaper_polishing'})
        .node('wash', {type: 'create:splashing'}, {checkpoint: true})
        .node('haunt', {type: 'create:haunting'})
        .node('drain', {type: 'create:emptying', results: [{id: 'minecraft:water', amount: 250}]}, {finish_mode: 'strict'})
        .edge('crush', 'polish').edge('polish', 'wash').edge('wash', 'haunt')
        .conditionalEdge('haunt', 'drain', {mode: 'partial', marks: {quality: 1}, checkpoints: ['crush', 'wash']})
        .finishOutput('drain', [{item: 'minecraft:diamond', weight: 3}, {item: 'minecraft:gold_ingot', weight: 1}])
        .register(event, 'example:additional_create_steps');
});
