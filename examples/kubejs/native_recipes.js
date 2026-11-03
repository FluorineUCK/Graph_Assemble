// Only KubeJS is required; Create processing below uses explicit native JSON.
// Enable this example independently of graphs with the same input and entrance machine.
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:raw_copper')
        .node('smelt', event.smelting('minecraft:iron_nugget', 'minecraft:raw_copper')
            .xp(0.1).cookingTime(80), { start: true })
        .nativeNode('press', {
            type: 'create:pressing',
            ingredients: [{ item: 'minecraft:iron_nugget' }],
            results: [{ id: 'minecraft:iron_nugget' }]
        }, { finish: true })
        .edge('smelt', 'press')
        .output(Item.of('minecraft:copper_ingot', 2));
    event.custom(graph.build()).id('example:native_recipes');
});
