package dev.graphassembly.integration.crafting;

/** Result container owns only an extraction guard, never an authoritative plan. */
public interface CraftingPreview {
    boolean graphAssembly$preview();
    void graphAssembly$preview(boolean preview);
}
