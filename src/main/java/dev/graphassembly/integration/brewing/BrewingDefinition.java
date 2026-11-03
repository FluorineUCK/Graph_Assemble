package dev.graphassembly.integration.brewing;

import dev.graphassembly.core.Node;
import dev.graphassembly.integration.NativeSteps;
import net.minecraft.world.level.Level;

/** Describes the graph roles; the native brewing registry supplies the conversion. */
public final class BrewingDefinition {
    public static final String SERIALIZER = "graph_assembly:brewing";
    private BrewingDefinition() {}

    public static BrewingRecipe create(Level level, Node node) {
        if (node.transitionalItem() != null)
            throw new IllegalArgumentException("Brewing uses its native carrier, not node transitional_item");
        for (var role : node.workpieceInputs()) if (role.itemInput() > 1)
            throw new IllegalArgumentException("Brewing has only primary0 and auxiliary1");
        var recipe = new BrewingRecipe(node.recipe());
        var json = recipe.json();
        return json.has("addition") ? new BrewingRecipe(json, NativeSteps.ingredient(level, json.get("addition"))) : recipe;
    }

    public static BrewingRecipe preview(Level level, Node node) {
        return create(level, node);
    }
}
