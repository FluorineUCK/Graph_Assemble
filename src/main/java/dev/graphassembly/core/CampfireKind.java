package dev.graphassembly.core;

/** Device condition on a campfire graph node, independent of its native recipe. */
public enum CampfireKind {
    ANY, NORMAL, SOUL;

    static final String RECIPE_TYPE = "minecraft:campfire_cooking";
}
