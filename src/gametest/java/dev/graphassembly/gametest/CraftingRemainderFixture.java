package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/** Real item extension callback; the entire gametest package is excluded from release jars. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class CraftingRemainderFixture extends Item {
    public static CraftingRemainderFixture ITEM;
    public static Consumer<ItemStack> callback=stack->{};
    private CraftingRemainderFixture(){super(new Item.Properties());}
    @Override public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack){callback.accept(stack);return new ItemStack(Items.GLASS_BOTTLE);}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM,GraphAssembly.id("crafting_remainder_fixture"),()-> {
            ITEM=new CraftingRemainderFixture();
            return ITEM;
        });
    }
}
