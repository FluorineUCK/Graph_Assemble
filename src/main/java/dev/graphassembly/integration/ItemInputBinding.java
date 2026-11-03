package dev.graphassembly.integration;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/** Logical item-input roles mapped to physical slots; never mutates the supplied stacks. */
public final class ItemInputBinding {
    private ItemInputBinding() {}

    public static InputAssignment.Result bind(List<Ingredient> ingredients, List<ItemStack> stacks,
                                              BiPredicate<Integer, ItemStack> roleMatches, IntPredicate significantRole) {
        int roles=ingredients.size(), slots=stacks.size();
        boolean[][] matrix=new boolean[roles][slots]; boolean[] significant=new boolean[roles];
        int[] capacities=new int[slots], identities=new int[slots];
        for(int slot=0;slot<slots;slot++) {
            var stack=stacks.get(slot); capacities[slot]=stack.getCount(); identities[slot]=slot;
            for(int prior=0;prior<slot;prior++) if(ItemStack.isSameItemSameComponents(stack,stacks.get(prior))) {
                identities[slot]=identities[prior];break;
            }
            for(int role=0;role<roles;role++) matrix[role][slot]=!stack.isEmpty()
                && ingredients.get(role).test(stack) && roleMatches.test(role,stack);
        }
        for(int role=0;role<roles;role++) significant[role]=significantRole.test(role);
        return InputAssignment.solve(matrix,capacities,identities,significant);
    }
}
