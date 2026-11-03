package dev.graphassembly.integration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Conservative union for foreign planners accepting only one preview list, never used to roll output. */
public final class OutputEnvelope {
    private final List<ItemStack> maxima = new ArrayList<>();

    public void include(List<ItemStack> simultaneous) {
        var totals = new ArrayList<ItemStack>();
        for (var item : simultaneous) {
            if (item.isEmpty()) continue;
            var previous = find(totals, item);
            if (previous == null) totals.add(item.copy());
            else previous.setCount(Math.addExact(previous.getCount(), item.getCount()));
        }
        for (var total : totals) {
            var previous = find(maxima, total);
            if (previous == null) maxima.add(total);
            else previous.setCount(Math.max(previous.getCount(), total.getCount()));
        }
    }

    public List<ItemStack> stacks() {
        var result = new ArrayList<ItemStack>();
        for (var item : maxima) {
            int left = item.getCount();
            while (left > 0) {
                int count = Math.min(left, item.getMaxStackSize());
                result.add(item.copyWithCount(count));
                left -= count;
            }
        }
        return List.copyOf(result);
    }

    private static ItemStack find(List<ItemStack> items, ItemStack target) {
        return items.stream().filter(item -> ItemStack.isSameItemSameComponents(item, target)).findFirst().orElse(null);
    }
}
