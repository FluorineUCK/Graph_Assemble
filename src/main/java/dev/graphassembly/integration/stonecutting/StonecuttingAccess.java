package dev.graphassembly.integration.stonecutting;

import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.*;
import java.util.List;

/** Plain interface: transformed menu implementation types never escape into runtime descriptors. */
public interface StonecuttingAccess {
    StonecuttingTransaction.Context graphAssembly$stonecutting();
    void graphAssembly$stonecutting(StonecuttingTransaction.Context context);
    void graphAssembly$list(List<RecipeHolder<StonecutterRecipe>> recipes);
    int graphAssembly$selected();
    void graphAssembly$selected(int index);
    ResultContainer graphAssembly$result();
    void graphAssembly$sound();
    void graphAssembly$resetNativeInput();
    void graphAssembly$notifyChoices();
}
