// Standalone fluent graph and native Create sequence; no event.custom call needed.
ServerEvents.recipes(function (event) {
    GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:raw_gold')
        .node('press', {type: 'create:pressing'}, {start: true, mark_amounts: {quality: 1}})
        .node('finish', {type: 'create:cutting'}, {finish_mode: 'strict'})
        .requirements('finish', {marks: {quality: 1}})
        .conditionalEdge('press', 'finish', {marks: {quality: 1}}, 2)
        .finishOutput('finish', [{item: 'minecraft:diamond', weight: 3}, {item: 'minecraft:emerald', weight: 1}])
        .scrap('finish', 'minecraft:flint')
        .register(event, 'example:fluent_graph');

    GraphAssembly.sequence('minecraft:iron_nugget')
        .ingredient('minecraft:iron_ingot')
        .step(event.smelting('minecraft:iron_nugget', 'minecraft:iron_ingot').cookingTime(60))
        .step({type: 'create:pressing'})
        .loops(2)
        .output({id: 'minecraft:gold_ingot', chance: 3})
        .output({id: 'minecraft:iron_nugget', chance: 1})
        .register(event, 'example:fluent_sequence');
});
