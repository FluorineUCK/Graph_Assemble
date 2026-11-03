package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.filter.*;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class FilterCapacityGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void componentAndPayloadShareCapacity(GameTestHelper helper) {
        var type = GraphAssembly.FILTER_SELECTION.get();
        String oversized = "{\"graph\":\"test:graph\",\"targets\":[\"" + "x".repeat(32768) + "\"]}";
        check(type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(oversized)).error().isPresent(), "Saved component must reject oversized config");
        check(type.codec().encodeStart(JsonOps.INSTANCE, oversized).error().isPresent(), "Saving must reject oversized config too");
        var ids = java.util.stream.IntStream.range(0,256).mapToObj(i -> "工序_🧭_" + i).toList();
        String json = new FilterSelection("test:graph", ids, Set.copyOf(ids), FilterConfiguration.Mode.NONE).toJson();
        var stack = GraphAssembly.FILTER.get().getDefaultInstance(); stack.set(type, json);
        var saved = stack.save(helper.getLevel().registryAccess());
        check(json.equals(ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow().get(type)), "NBT round trip");
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            check(json.equals(ItemStack.STREAM_CODEC.decode(buf).get(type)), "Component sync round trip");
            var payload = new ConfigureFilterPayload(InteractionHand.OFF_HAND, json);
            ConfigureFilterPayload.CODEC.encode(buf, payload);
            check(payload.equals(ConfigureFilterPayload.CODEC.decode(buf)), "Payload round trip");
            check(buf.readableBytes()==0, "No trailing bytes");
            boolean rejected=false;
            try { type.streamCodec().encode(buf, oversized); } catch (RuntimeException e) { rejected=true; }
            check(rejected, "Component wire must reject oversized config");
            buf.clear();
            String incoming="{\"graph\":\"test:graph\",\"targets\":[\""+"中".repeat(6000)+"\"]}";
            // Fits the wire's UTF-16 framing, but violates the shared byte/ID capacity.
            buf.writeEnum(InteractionHand.MAIN_HAND); buf.writeUtf(incoming, FilterConfiguration.MAX_JSON_BYTES);
            var received=ConfigureFilterPayload.CODEC.decode(buf);
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
            var previous=player.getMainHandItem();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                ConfigureFilterPayload.apply(player,received);
                check(json.equals(player.getMainHandItem().get(type)), "Server rejection must not overwrite the old selection");
            } finally { player.setItemInHand(InteractionHand.MAIN_HAND,previous); }
        } finally { buf.release(); }
        helper.succeed();
    }
    private static void check(boolean c,String message) { if(!c) throw new AssertionError(message); }
}
