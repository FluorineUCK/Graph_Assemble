package dev.graphassembly.integration;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.util.ArrayList;
import java.util.List;

/** Server-side, on-demand observations. Discovery never changes NativeSteps' execution allow-list. */
@EventBusSubscriber(modid = GraphAssembly.ID)
public final class AssemblyDiagnostics {
    private AssemblyDiagnostics() {}

    public static List<String> report(Level level) {
        var observations = AssemblyDiscovery.scan(level.getRecipeManager().getRecipes());
        List<String> lines = new ArrayList<>();
        lines.add("Assembly discovery: " + observations.size() + " observed capabilities. Observed recipes/sequence steps only; discovery is not execution support.");
        observations.forEach(found -> lines.add(found.capability().diagnostic() + "; sample=" + found.source()));
        var verified=new java.util.TreeSet<>(NativeSteps.EXECUTION_SERIALIZERS);
        lines.add("Verified Create processing-device serializers: " + verified);
        lines.add("Verified native crafting-menu serializers (exact standard classes; InventoryMenu/CraftingMenu only): "
            + new java.util.TreeSet<>(dev.graphassembly.integration.crafting.CraftingDefinition.SERIALIZERS));
        GraphIndex.get(level).disabledReasons().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> lines.add("Disabled graph " + entry.getKey() + ": " + entry.getValue()));
        return List.copyOf(lines);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(GraphAssembly.ID).requires(source -> source.hasPermission(2))
            .then(Commands.literal("compatibility").executes(context -> {
                report(context.getSource().getLevel()).forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                return 1;
            })));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) { log(event.getServer().overworld()); }

    @SubscribeEvent
    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        GraphIndex.invalidate();
        log(event.getPlayerList().getServer().overworld());
    }

    private static void log(Level level) {
        report(level).forEach(line -> GraphAssembly.LOGGER.info("{}", line));
    }
}
