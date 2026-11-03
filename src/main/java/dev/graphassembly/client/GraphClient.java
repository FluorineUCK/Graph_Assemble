package dev.graphassembly.client;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.filter.GraphFilterItem;
import dev.graphassembly.recipe.RuntimeSnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = GraphAssembly.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GraphClient {
    @SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        dev.graphassembly.client.gui.GraphCanvasBackground.registerShader(event);
    }
    @SubscribeEvent public static void reloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new dev.graphassembly.client.gui.GraphGuiReloadListener());
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            GraphFilterItem.openScreen = hand -> Minecraft.getInstance().setScreen(new GraphFilterScreen(hand));
            GraphFilterItem.summary = PlayerTooltips::filter;
            com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(GraphAssembly.FILTER.get(),
                new com.simibubi.create.foundation.item.ItemDescription.Modifier(GraphAssembly.FILTER.get(),
                    net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));
            NeoForge.EVENT_BUS.addListener(GraphClient::tooltip);
            // End our session before JEI's NORMAL-priority logout stops its runtime callback.
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GraphClient::loggedOut);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GraphClient::shuttingDown);
            NeoForge.EVENT_BUS.addListener(GraphClient::recipesUpdated);
        });
    }
    private static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(GraphAssembly.INFESTED_INSERT.get())) {
            event.getToolTip().add(Component.translatable("item.graph_assembly.infested_filter_insert.tooltip")
                .withStyle(ChatFormatting.GRAY));
        }
        PlayerTooltips.workpiece(event);
    }
    private static void loggedOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        dev.graphassembly.client.view.PreviewReloads.sessionEnded();
        var level = Minecraft.getInstance().level;
        if (level != null) RuntimeSnapshots.release(level.getRecipeManager());
    }
    private static void recipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
        dev.graphassembly.client.view.PreviewReloads.recipesChanged();
    }
    private static void shuttingDown(net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        dev.graphassembly.client.view.PreviewReloads.sessionEnded();
    }
}
