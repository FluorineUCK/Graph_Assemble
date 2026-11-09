package dev.graphassembly.integration.vanilla.crafting;

/** State is owned by each native menu, never by a global plan cache. */
public interface CraftingMenuState {
    CraftingTransaction.Context graphAssembly$crafting();
    void graphAssembly$crafting(CraftingTransaction.Context context);
}
