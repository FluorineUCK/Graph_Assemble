package dev.graphassembly.gametest;

import com.mojang.serialization.Codec;
import dev.graphassembly.GraphAssembly;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** A registered real output component codec, not a production test callback. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class FurnaceCodecFixture {
    public static Runnable decode=()->{};
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.DATA_COMPONENT_TYPE,GraphAssembly.id("furnace_codec_fixture"),()->DataComponentType.<String>builder()
            .persistent(Codec.STRING.xmap(value->{decode.run();return value;},value->value)).build());
    }
}
