package dev.graphassembly.integration.anvil;

import dev.graphassembly.core.Node;
import dev.graphassembly.integration.NativeSteps;
import net.minecraft.world.level.Level;

/** Validated menu-only descriptor admission; host validity is checked by the native transaction. */
public final class AnvilDefinition {
    public static final String SERIALIZER="graph_assembly:anvil";
    private AnvilDefinition() {}
    public static AnvilRecipe preview(Level level,Node node) {
        var recipe=validate(node);
        var json=recipe.json();
        return json.has("addition") ? new AnvilRecipe(json,NativeSteps.ingredient(level,json.get("addition"))) : recipe;
    }
    public static AnvilRecipe create(Level level,Node node) {
        var recipe=validate(node);
        var json=recipe.json();
        if(json.has("addition"))NativeSteps.ingredient(level,json.get("addition"));
        return recipe;
    }
    private static AnvilRecipe validate(Node node) {
        if(node.transitionalItem()!=null)throw new IllegalArgumentException("Anvil uses its native carrier, not node transitional_item");
        var recipe=new AnvilRecipe(node.recipe());
        for(var role:node.workpieceInputs())if(role.itemInput()>1)throw new IllegalArgumentException("Anvil has only primary0 and auxiliary1");
        if(recipe.action()==AnvilRecipe.Action.RENAME&&node.workpieceInput(1)!=null)throw new IllegalArgumentException("Rename does not accept auxiliary workpieces");

        return recipe;
    }
}
