package dev.graphassembly.integration.smithing;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ResultContainer;

public interface SmithingAccess {
    SmithingTransaction.Context graphAssembly$smithing();
    void graphAssembly$smithing(SmithingTransaction.Context context);
    Container graphAssembly$inputs();
    ResultContainer graphAssembly$result();
    void graphAssembly$clearRecipe();
    void graphAssembly$sound();
}
