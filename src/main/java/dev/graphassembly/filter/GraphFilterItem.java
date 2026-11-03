package dev.graphassembly.filter;

import com.simibubi.create.content.logistics.filter.*;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.RuntimeSnapshots;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.List;
import java.util.function.Consumer;

public final class GraphFilterItem extends FilterItem {
    public static Consumer<InteractionHand> openScreen = hand -> {};
    // Client presentation is installed at client setup; no Minecraft/JEI linkage on a server.
    public static java.util.function.Function<ItemStack,List<Component>> summary = stack -> {
        var selection = selection(stack);
        return List.of(Component.translatable("graph_assembly.filter.summary." + selection.mode().id(), selection.targets().size()));
    };
    public GraphFilterItem(Properties properties) { super(properties); }
    public static FilterSelection selection(ItemStack stack) {
        String json = stack.get(GraphAssembly.FILTER_SELECTION.get());
        return json == null ? FilterSelection.EMPTY : FilterSelection.parse(json);
    }
    /** Level-scoped snapshots parse current selections; without a level, read the current format without caching. Invalid data fails closed. */
    public static FilterSelection selection(Level level, ItemStack stack) {
        if (level == null) return selection(stack);
        String json = stack.get(GraphAssembly.FILTER_SELECTION.get());
        if (json == null) return FilterSelection.EMPTY;
        try { return RuntimeSnapshots.filter(level.getRecipeManager(), json); }
        catch (RuntimeException e) { return FilterSelection.EMPTY; }
    }
    @Override public List<Component> makeSummary(ItemStack stack) {
        return summary.apply(stack);
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return null; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) openScreen.accept(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public DataComponentType<?> getComponentType() { return GraphAssembly.FILTER_SELECTION.get(); }
    @Override public ItemStack[] getFilterItems(ItemStack stack) { return new ItemStack[0]; }
    @Override public FilterItemStack makeStackWrapper(ItemStack filter) { return new Wrapper(filter); }
    private static final class Wrapper extends FilterItemStack {
        private Wrapper(ItemStack stack) { super(stack); }
        @Override public boolean test(Level level, ItemStack input, boolean matchNBT) { return selection(level, item()).matches(level, input); }
        @Override public boolean test(Level level, FluidStack input, boolean matchNBT) { return false; }
    }
}
