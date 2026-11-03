package dev.graphassembly.mixin.create;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(GroupedItems.class)
public interface GroupedItemsAccess {
    @Accessor("grid") Map<Pair<Integer,Integer>,ItemStack> graphAssembly$grid();
}
