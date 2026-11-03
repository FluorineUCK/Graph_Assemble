package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registered NeoForge Item extension: capacity queries are arbitrary external callbacks. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class StonecuttingCapacityFixture extends Item {
    public static Item ITEM;
    public static Runnable query=()->{};
    public static int limit=64;
    public static java.util.function.IntUnaryOperator perCount=count->limit;
    private StonecuttingCapacityFixture(){super(new Item.Properties());}
    @Override public int getMaxStackSize(ItemStack stack){query.run();return perCount.applyAsInt(stack.getCount());}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ITEM,GraphAssembly.id("stonecutting_capacity_fixture"),()->{ITEM=new StonecuttingCapacityFixture();return ITEM;});
    }
}
