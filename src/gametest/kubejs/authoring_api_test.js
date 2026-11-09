// Real Rhino coverage: custom() remains an implementation detail of register().
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:netherite_ingot')
        .node('start', {type: 'create:pressing'}, {start: true, mark_amounts: {ready: 1}})
        .node('good', {type: 'create:mixing', ingredients: [{item: 'minecraft:iron_nugget'}]}, {
            workpiece_inputs: [{item_input: 0, current_nodes: ['start']}, {item_input: 1, requirements: {}}]
        })
        .node('other', {type: 'create:pressing'})
        .branchMode('start', 'random')
        .requirements('good', {marks: {ready: 1}})
        .conditionalEdge('start', 'good', {marks: {ready: 1}}, 3)
        .conditionalEdge('start', 'other', {marks: {ready: 2}}, 1)
        .output({results: [{item: 'minecraft:iron_nugget', weight: 1}]})
        .finishOutput('good', [{item: 'minecraft:diamond', weight: 3}, {item: 'minecraft:emerald', weight: 1}], {marks: {ready: 1}})
        .finishOutput('other', 'minecraft:gold_nugget')
        .scrap('good', 'minecraft:flint');
    var registered = graph.register(event, 'authoring:fluent_graph');
    if (String(registered.getId()) !== 'authoring:fluent_graph') throw new Error('register must return the registered KubeRecipe');

    var heat = event.smelting('minecraft:gold_nugget', 'minecraft:iron_ingot').cookingTime(31)
        .id('authoring:nested_heat');
    var sequence = GraphAssembly.sequence('minecraft:iron_nugget')
        .ingredient('minecraft:iron_ingot').step(heat).loops(2)
        .output(Item.of('minecraft:iron_pickaxe', 1, {'minecraft:damage': 5}))
        .output({id: 'minecraft:flint', chance: 0.25});
    heat.cookingTime(99);
    sequence.register(event, 'authoring:fluent_sequence');

    function rejected(action) {
        var caught = false;
        try { action(); } catch (error) { caught = true; }
        if (!caught) throw new Error('Invalid fluent authoring input was accepted');
    }
    rejected(function () { sequence.loops(0); });
    rejected(function () { sequence.loops(1.5); });
    rejected(function () { sequence.loops(Infinity); });
    rejected(function () { sequence.loops(2147483648); });
    rejected(function () { GraphAssembly.sequence('minecraft:iron_nugget').ingredient('minecraft:iron_ingot').step({type: 'create:pressing'}).build(); });
    rejected(function () { graph.requirements('missing', {}); });
    rejected(function () { graph.register(event, 'Bad ID'); });
    var callback = event.shapeless('minecraft:flint', ['minecraft:iron_ingot']).modifyResult('authoring:unused_callback');
    rejected(function () { GraphAssembly.sequence('minecraft:iron_nugget').step(callback); });
    callback.remove();
});
