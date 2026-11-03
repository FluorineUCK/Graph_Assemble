ServerEvents.recipes(function (event) {
    event.custom(GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({item: 'minecraft:nether_brick'})
        .node('s', {type: 'create:pressing'}, {start: true, finish: true})
        .output({results: [{item: 'minecraft:diamond'}]}).build()).id('test:kube_fixture');

    function conditionalGraph() {
        return GraphAssembly.create('minecraft:iron_nugget')
            .ingredient({item: 'minecraft:brick'})
            .node('s', {type: 'create:pressing'}, {start: true, finish: true, rewards: {x: 3}});
    }
    event.custom(conditionalGraph().output({requirements: {mode: 'partial', marks: {x: 2}}, results: [{item: 'minecraft:diamond'}]})
        .build()).id('test:kube_requirements_fixture');
    var rejected = 0;
    function invalid(action, path) {
        try { action(); } catch (error) {
            if (String(error).indexOf(path) < 0) throw error;
            rejected++;
        }
    }
    function invalidVersion(version) {
        var json = conditionalGraph().output({requirements: {}, results: [{item: 'minecraft:diamond'}]}).build();
        json.addProperty('format_version', version);
        Java.loadClass('dev.graphassembly.core.GraphCodec').parse('test:kube_invalid_version', json);
    }
    invalid(function () { invalidVersion(1); }, 'graph.format_version');
    invalid(function () { invalidVersion(2); }, 'graph.format_version');
    invalid(function () { invalidVersion(3); }, 'graph.format_version');
    invalid(function () { conditionalGraph().output({marks: {}, results: [{item: 'minecraft:diamond'}]}).build(); }, 'graph.outputs[0].marks');
    invalid(function () { conditionalGraph().output({requirements: {mode: 'unordered'}, results: [{item: 'minecraft:diamond'}]}).build(); }, 'graph.outputs[0].requirements.mode');
    invalid(function () { conditionalGraph().output({requirements: {checkpoints: ['missing']}, results: [{item: 'minecraft:diamond'}]}).build(); }, 'graph.outputs[0].requirements.checkpoints[0]');
    if (rejected !== 6) throw new Error('GraphAssembly invalid cases accepted: ' + rejected + '/6 rejected');
    event.custom(GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({item: 'minecraft:netherite_scrap'})
        .node('s', {type: 'create:pressing'}, {start: true, finish: true})
        .output({results: [{item: 'minecraft:diamond'}]}).build()).id('test:kube_invalid_cases_checked');
});
