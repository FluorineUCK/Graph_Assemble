ServerEvents.recipes(function (event) {
    event.custom(GraphAssembly.create('minecraft:iron_nugget')
        .ingredient(Ingredient.of('#c:ingots/copper'))
        .node('heat', event.smelting('minecraft:iron_ingot', 'minecraft:copper_ingot')
            .xp(0.7).cookingTime(60).id('test:graph_nested_heat'), {start: true, finish: true})
        .output('2x minecraft:gold_nugget').build()).id('test:kube_native_heat');

    function graph(id, ingredient, recipe) {
        var result = GraphAssembly.create('minecraft:iron_nugget').ingredient(ingredient)
            .node('s', recipe, {start: true, finish: true})
            .output({results: [Item.of('minecraft:iron_sword', {'minecraft:damage': 5})]});
        event.custom(result.build()).id('test:kube_native_' + id);
        return result;
    }
    var mutable = event.smelting('minecraft:gold_ingot', 'minecraft:raw_gold').cookingTime(31).id('test:graph_nested_mutable');
    var frozen = graph('snapshot', 'minecraft:raw_gold', mutable);
    mutable.cookingTime(99);
    if (frozen.build().getAsJsonArray('nodes').get(0).getAsJsonObject().getAsJsonObject('recipe').get('cookingtime').getAsInt() !== 31)
        throw new Error('Native node snapshot changed with its upstream builder');
    graph('reuse', 'minecraft:raw_iron', mutable);
    graph('shaped', 'minecraft:bone', event.shaped('minecraft:iron_ingot', ['AB'], {A: 'minecraft:bone', B: 'minecraft:stick'})
        .id('test:graph_nested_shaped'));
    graph('shapeless', 'minecraft:feather', event.shapeless('minecraft:iron_ingot', ['minecraft:feather', 'minecraft:stick'])
        .id('test:graph_nested_shapeless'));
    graph('stonecutting', 'minecraft:calcite', event.stonecutting('2x minecraft:stone', 'minecraft:calcite').id('test:graph_nested_stonecutting'));
    graph('campfire', 'minecraft:porkchop', event.campfireCooking('minecraft:cooked_porkchop', 'minecraft:porkchop').cookingTime(45)
        .id('test:graph_nested_campfire'));
    graph('smithing', 'minecraft:diamond_boots', event.smithing('minecraft:netherite_boots', 'minecraft:netherite_upgrade_smithing_template', 'minecraft:diamond_boots', 'minecraft:netherite_ingot')
        .id('test:graph_nested_smithing'));
    graph('trim', 'minecraft:diamond_chestplate', event.smithingTrim('minecraft:coast_armor_trim_smithing_template', 'minecraft:diamond_chestplate', 'minecraft:gold_ingot')
        .id('test:graph_nested_trim'));
    event.custom(GraphAssembly.create('minecraft:iron_nugget').ingredient('minecraft:clay_ball')
        .nativeNode('s', {type: 'create:pressing', ingredients: [{item: 'minecraft:clay_ball'}], results: [{id: 'minecraft:iron_nugget'}]},
            {start: true, finish: true, ingredient: Ingredient.of('minecraft:clay_ball'), outputs: [{results: ['2x minecraft:gold_nugget']}], scrap: [Item.of('minecraft:flint')]})
        .build()).id('test:kube_native_raw');

    function rejects(recipe, message) {
        var caught = false;
        try { GraphAssembly.create('minecraft:iron_nugget').ingredient('minecraft:dirt').node('s', recipe, {start: true}); }
        catch (error) { if (String(error).indexOf(message) < 0) throw error; caught = true; }
        recipe.remove();
        if (!caught) throw new Error('Accepted unsupported behavior ' + message);
    }
    rejects(event.shapeless('minecraft:diamond', ['minecraft:dirt', 'minecraft:stick']).keepIngredient('minecraft:stick'), 'kubejs:ingredient_actions');
    rejects(event.shapeless('minecraft:diamond', ['minecraft:dirt', 'minecraft:stick']).damageIngredient('minecraft:stick'), 'kubejs:ingredient_actions');
    rejects(event.shapeless('minecraft:diamond', ['minecraft:dirt']).ingredientAction(0, null), 'kubejs:ingredient_actions');
    rejects(event.shapeless('minecraft:diamond', ['minecraft:dirt']).customIngredientAction(0, 'test:unused_callback'), 'kubejs:ingredient_actions');
    rejects(event.shapeless('minecraft:diamond', ['minecraft:dirt']).modifyResult('test:unused_callback'), 'kubejs:modify_result');
    rejects(event.shaped('minecraft:diamond', ['A'], {A: 'minecraft:dirt'}).noMirror(), 'kubejs:mirror');
    rejects(event.shaped('minecraft:diamond', [' A'], {A: 'minecraft:dirt'}).noShrink(), 'kubejs:shrink');
    var removed = event.smelting('minecraft:diamond', 'minecraft:dirt'); removed.remove(); rejects(removed, 'removed');
    var rawRejected = 0;
    for (var rawMode = 0; rawMode < 2; rawMode++) {
        var raw = {type: 'minecraft:crafting_shapeless', ingredients: [{item: 'minecraft:dirt'}], result: {id: 'minecraft:diamond'}, 'kubejs:modify_result': 'test:unused_callback'};
        try {
            var b = GraphAssembly.create('minecraft:iron_nugget').ingredient('minecraft:dirt');
            if (rawMode === 0) b.nativeNode('s', raw, {start: true});
            else b.node('s', raw, {start: true, recipe_mode: 'native'});
        } catch (error) {
            if (String(error).indexOf('kubejs:modify_result') < 0) throw error;
            rawRejected++;
        }
    }
    if (rawRejected !== 2) throw new Error('Native raw entry bypassed behavior validation');
    event.forEachRecipe({id: 'minecraft:iron_ingot_from_smelting_raw_iron'}, function (existing) {
        var caught = false;
        try { GraphAssembly.create('minecraft:iron_nugget').ingredient('minecraft:dirt').node('s', existing, {start: true}); }
        catch (error) { if (String(error).indexOf('recipe_id') < 0) throw error; caught = true; }
        if (!caught) throw new Error('Took ownership of an existing native recipe');
    });
    graph('checked', 'minecraft:chorus_fruit', event.smelting('minecraft:popped_chorus_fruit', 'minecraft:chorus_fruit').id('test:graph_nested_checked'));

    if (Platform.isLoaded('kubejs_create')) {
        graph('deploy', 'minecraft:glow_berries', event.recipes.create.deploying('minecraft:iron_nugget', ['minecraft:glow_berries', 'minecraft:stick'])
            .keepHeldItem().id('test:graph_nested_deploy'));
        graph('cut', 'minecraft:nether_star', event.recipes.create.cutting(['minecraft:iron_nugget', CreateItem.of('minecraft:flint', 0.25)], 'minecraft:nether_star')
            .processingTime(37).id('test:graph_nested_cut'));
        graph('mix', 'minecraft:lapis_lazuli', event.recipes.create.mixing('minecraft:iron_nugget', [Fluid.of('minecraft:water', 250), 'minecraft:lapis_lazuli', '2x minecraft:sugar'])
            .heated().id('test:graph_nested_mix'));
        graph('compact', 'minecraft:quartz', event.recipes.create.compacting('minecraft:iron_nugget', ['minecraft:quartz', 'minecraft:sugar'])
            .superheated().id('test:graph_nested_compact'));
        graph('fill', 'minecraft:glass_bottle', event.recipes.create.filling('minecraft:iron_nugget', [Fluid.of('minecraft:water', 250), 'minecraft:glass_bottle'])
            .id('test:graph_nested_fill'));
        graph('mill', 'minecraft:cocoa_beans', event.recipes.create.milling('minecraft:iron_nugget', 'minecraft:cocoa_beans')
            .processingTime(51).id('test:graph_nested_mill'));
    }
});
