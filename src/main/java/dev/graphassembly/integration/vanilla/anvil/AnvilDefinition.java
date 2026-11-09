package dev.graphassembly.integration.vanilla.anvil;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.NativeSteps;
import net.minecraft.world.level.Level;

/** Validated menu-only descriptor admission; host validity is checked by the native transaction. */
public final class AnvilDefinition {
    public static final String SERIALIZER="graph_assembly:anvil";
    private AnvilDefinition() {}
    public static AnvilRecipe preview(Level level,NodeDefinition<OperationSpec> node) {
        var recipe=validate(node);
        var json=recipe.json();
        return json.has("addition") ? new AnvilRecipe(json,NativeSteps.ingredient(level,json.get("addition"))) : recipe;
    }
    public static AnvilRecipe create(Level level,NodeDefinition<OperationSpec> node) {
        var recipe=validate(node);
        var json=recipe.json();
        if(json.has("addition"))NativeSteps.ingredient(level,json.get("addition"));
        return recipe;
    }
    private static AnvilRecipe validate(NodeDefinition<OperationSpec> node) {
        if(node.effects().carrierOverride()!=null)throw new IllegalArgumentException("Anvil uses its native carrier, not node transitional_item");
        var recipe=new AnvilRecipe(node.operation().recipe());
        for(var role:node.inputs().workpieceInputs())if(role.itemInput()>1)throw new IllegalArgumentException("Anvil has only primary0 and auxiliary1");
        if(recipe.action()==AnvilRecipe.Action.RENAME&&node.inputs().workpieceInput(1)!=null)throw new IllegalArgumentException("Rename does not accept auxiliary workpieces");

        return recipe;
    }
}
