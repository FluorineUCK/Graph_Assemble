package dev.graphassembly.integration.furnace;

/** Implemented by the shared vanilla furnace target, never a Mixin-class runtime type. */
public interface CookingStateAccess {
    CookingState graphAssembly$cookingState();
    void graphAssembly$cookProgress(int value);
    void graphAssembly$cookTime(int value);
    void graphAssembly$abortUnpaidIgnition();
}
