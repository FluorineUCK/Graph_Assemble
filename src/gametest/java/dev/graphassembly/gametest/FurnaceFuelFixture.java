package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Real registered fuel extension callback, excluded from release jars with all gametest fixtures. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class FurnaceFuelFixture extends Item {
    public static FurnaceFuelFixture ITEM;
    public static Runnable duration=()->{},remainder=()->{};
    private FurnaceFuelFixture(){super(new Item.Properties());}
    @Override public int getBurnTime(ItemStack stack,RecipeType<?> type){duration.run();return 100;}
    @Override public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack){remainder.run();return new ItemStack(Items.BUCKET);}
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.ITEM,GraphAssembly.id("furnace_fuel_fixture"),()->{ITEM=new FurnaceFuelFixture();return ITEM;});}
}
