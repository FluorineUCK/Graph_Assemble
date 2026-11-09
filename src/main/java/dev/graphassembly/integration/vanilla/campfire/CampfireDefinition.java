package dev.graphassembly.integration.vanilla.campfire;

import com.google.gson.JsonObject;

import static dev.graphassembly.codec.JsonValidation.invalid;
import static dev.graphassembly.codec.JsonValidation.requiredText;

/** Owns campfire applicability, placement and device configuration in graph JSON. */
public final class CampfireDefinition {
    public static final String RECIPE_TYPE = "minecraft:campfire_cooking";

    private CampfireDefinition() {}

    public static boolean isCampfire(JsonObject recipe) {
        return RECIPE_TYPE.equals(recipe.get("type").getAsString());
    }

    public static void validateGraphPlacement(JsonObject graph) {
        if (graph.has("campfire"))
            throw invalid("graph.campfire", "must be on a campfire node");
    }

    public static void validateRecipePlacement(JsonObject recipe, String nodePath) {
        if (recipe.has("campfire"))
            throw invalid(nodePath + ".recipe.campfire", "must be beside recipe on the node");
    }

    /** Null means this operation is not a campfire; omission on a campfire means ANY. */
    public static CampfireKind parse(JsonObject node, String recipeType, String nodePath) {
        boolean campfire = RECIPE_TYPE.equals(recipeType);
        if (!node.has("campfire")) return campfire ? CampfireKind.ANY : null;
        if (!campfire)
            throw invalid(nodePath + ".campfire", "requires a minecraft:campfire_cooking node");
        return switch (requiredText(node, "campfire", nodePath)) {
            case "any" -> CampfireKind.ANY;
            case "normal" -> CampfireKind.NORMAL;
            case "soul" -> CampfireKind.SOUL;
            default -> throw invalid(nodePath + ".campfire", "must be normal, soul, or any");
        };
    }
}
