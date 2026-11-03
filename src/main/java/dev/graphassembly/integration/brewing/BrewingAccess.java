package dev.graphassembly.integration.brewing;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public interface BrewingAccess {
    NonNullList<ItemStack> graphAssembly$items();
    int graphAssembly$time();
    void graphAssembly$time(int time);
    int graphAssembly$fuel();
    BrewingTransaction.State graphAssembly$brewingState();
}
