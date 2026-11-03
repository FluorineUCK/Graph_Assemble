package dev.graphassembly.filter;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ConfigureFilterPayload(InteractionHand hand, String selection) implements CustomPacketPayload {
    public static final Type<ConfigureFilterPayload> TYPE = new Type<>(GraphAssembly.id("configure_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureFilterPayload> CODEC = StreamCodec.of(
        (buf, payload) -> { var json = FilterSelectionCodec.validated(payload.selection); buf.writeEnum(payload.hand); buf.writeUtf(json, FilterConfiguration.MAX_JSON_BYTES); },
        buf -> new ConfigureFilterPayload(buf.readEnum(InteractionHand.class), buf.readUtf(FilterConfiguration.MAX_JSON_BYTES)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> apply(context.player(), payload)));
    }
    public static void apply(net.minecraft.world.entity.player.Player player, ConfigureFilterPayload payload) {
            var held = player.getItemInHand(payload.hand());
            if (!held.is(GraphAssembly.FILTER.get())) return;
            FilterConfiguration selection;
            try { selection = FilterConfiguration.parse(payload.selection()); }
            catch (FilterConfiguration.Invalid e) {
                player.displayClientMessage(Component.translatable(e.getMessage()), true); return;
            }
            var graph = GraphIndex.get(player.level()).graphs().get(selection.graph());
            if (graph == null || selection.targets().isEmpty() || !graph.nodes().keySet().containsAll(selection.targets())) {
                player.displayClientMessage(Component.translatable("graph_assembly.filter.invalid"), true); return;
            }
            held.set(GraphAssembly.FILTER_SELECTION.get(), selection.toJson());
            player.getInventory().setChanged();

    }
}
