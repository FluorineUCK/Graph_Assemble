// Minecraft 1.21.1 / Create 6.0.10 / KubeJS 7.
// Copy only this example into kubejs/server_scripts/; do not enable overlapping copper-press Starts.
// A copper ingot on a depot/belt under a mechanical press settles this one-node graph.
// Fixed diamond + independent emerald/gold rolls + nested weighted bundle.
ServerEvents.recipes(event => {
  event.custom({
    "type": "graph_assembly:assembly",
    "ingredient": {"item": "minecraft:copper_ingot"},
    "transitional_item": "minecraft:iron_nugget",
    "nodes": [{
      "id": "finish", "start": true, "finish": true, "finish_mode": "strict",
      "recipe": {"type": "create:pressing"},
      "scrap": [{"mode": "all", "results": [
        {"item": "minecraft:iron_nugget"},
        {"mode": "weighted", "results": [
          {"item": "minecraft:gravel", "weight": 3},
          {"item": "minecraft:flint", "weight": 1, "chance": 0.5}
        ]}
      ]}]
    }],
    "edges": [],
    "outputs": [{"results": [{"mode": "all", "results": [
      {"item": "minecraft:diamond"},
      {"item": "minecraft:emerald", "count": 3, "chance": 0.5},
      {"item": "minecraft:gold_ingot", "chance": 0.25},
      {"mode": "weighted", "results": [
        {"item": "minecraft:redstone", "weight": 3},
        {"mode": "all", "weight": 1, "chance": 0.5, "results": [
          {"item": "minecraft:quartz", "count": 2},
          {"mode": "weighted", "results": [
            {"item": "minecraft:lapis_lazuli", "weight": 2},
            {"item": "minecraft:amethyst_shard", "weight": 1}
          ]}
        ]}
      ]}
    ]}]}]
  }).id('example:nested_probability_outputs');
});
// The success rule is unconditional, so scrap is intentionally unreachable until you add requirements.
// A chance miss is still a successful settlement; it does not fall through into scrap.
