package dev.graphassembly.integration.anvil;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;

/** Narrow native menu boundary; no native result/cost calculation is reproduced. */
public interface AnvilAccess {
    AnvilTransaction.Context graphAssembly$anvil();
    void graphAssembly$anvil(AnvilTransaction.Context context);
    Container graphAssembly$anvilInputs();
    ResultContainer graphAssembly$anvilResult();
    String graphAssembly$anvilName();
    int graphAssembly$anvilMaterials();
    void graphAssembly$anvilCost(int cost,int materials);
    boolean graphAssembly$anvilPickup(Player player);
    void graphAssembly$anvilTake(Player player,ItemStack nativeResult);
}
