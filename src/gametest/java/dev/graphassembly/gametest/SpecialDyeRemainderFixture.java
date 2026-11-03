package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Native DyeItem callback seam; never included in release jars. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class SpecialDyeRemainderFixture extends DyeItem {
    public static SpecialDyeRemainderFixture ITEM;
    public static Runnable callback=()->{};
    private SpecialDyeRemainderFixture(){super(DyeColor.RED,new Item.Properties());}
    @Override public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack){callback.run();return new ItemStack(Items.GLASS_BOTTLE);}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM,GraphAssembly.id("special_dye_remainder_fixture"),()->{ITEM=new SpecialDyeRemainderFixture();return ITEM;});
    }
}
