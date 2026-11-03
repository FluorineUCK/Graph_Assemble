package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagRegistry;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Test-only feature universe: GameTest enables every vanilla feature, so BUNDLE is unsuitable. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class CampfireFeatureFixture {
    public static Item ITEM;
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM,GraphAssembly.id("campfire_disabled_fixture"),()->{
            var flags=new FeatureFlagRegistry.Builder("graph_assembly_campfire_fixture");
            var disabled=flags.create(GraphAssembly.id("campfire_fixture_only"));
            ITEM=new Item(new Item.Properties()) {
                @Override public FeatureFlagSet requiredFeatures(){return FeatureFlagSet.of(disabled);}
            };
            return ITEM;
        });
    }
}
