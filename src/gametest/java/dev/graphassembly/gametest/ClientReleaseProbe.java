package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.compat.jei.GraphJeiPlugin;
import dev.graphassembly.compat.jei.NativeStepPreview;
import dev.graphassembly.core.GraphCodec;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Opt-in rendered-client assertions. Like every gametest class, absent from the release jar. */
@JeiPlugin
public final class ClientReleaseProbe implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return GraphAssembly.id("release_probe"); }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (!Boolean.getBoolean("graph_assembly.clientVerification")) return;
        var minecraft = Minecraft.getInstance();
        GraphAssembly.LOGGER.info("RELEASE_PROBE registration level={} connectionRecipes={} levelRecipes={} graphs={}",
            minecraft.level != null, minecraft.getConnection() == null ? -1 : minecraft.getConnection().getRecipeManager().getRecipes().size(),
            minecraft.level == null ? -1 : minecraft.level.getRecipeManager().getRecipes().size(),
            minecraft.level == null ? "no level" : GraphIndex.get(minecraft.level).graphs().keySet());
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("graph_assembly.clientVerification")) return;
        try {
            var level = java.util.Objects.requireNonNull(Minecraft.getInstance().level, "client level");
            GraphAssembly.LOGGER.info("RELEASE_PROBE JEI indexed graphs={}", runtime.getRecipeManager().createRecipeLookup(GraphJeiPlugin.TYPE).get().map(g -> g.id()).toList());
            var graph = GraphCodec.parse("test:client_slots", JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"deploy","start":true,"recipe":{"type":"create:deploying","ingredients":[{"item":"minecraft:stick"}],"keep_held_item":true}},
                          {"id":"fill","recipe":{"type":"create:filling","ingredients":[{"type":"neoforge:single","fluid":"minecraft:water","amount":250}]}}],"edges":[]}
                """).getAsJsonObject());
            for (var node : graph.nodes().values()) {
                var nativeRecipe = NativeSteps.create(level, graph, node, NativeSteps.stack(graph.transitionalItem()));
                var preview = NativeStepPreview.create(runtime, runtime.getJeiHelpers().getGuiHelper(), nativeRecipe, 0,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                var slots = preview.layout().getRecipeSlotsView().getSlotViews();
                if (node.id().equals("deploy")) check(slots.stream().flatMap(slot -> slot.getItemStacks()).anyMatch(s -> s.is(Items.STICK)), "native builder stick slot");
                else check(slots.stream().flatMap(slot -> slot.getIngredients(NeoForgeTypes.FLUID_STACK)).anyMatch(f -> f.getAmount() == 250), "native builder fluid amount");
                boolean hit = false;
                for (int x = 0; x < preview.layout().getRect().getWidth(); x++)
                    for (int y = 0; y < preview.layout().getRect().getHeight(); y++)
                        if (preview.layout().getSlotUnderMouse(x, y).isPresent()) hit = true;
                check(hit, "native layout slot hit area");
                GraphAssembly.LOGGER.info("RELEASE_PROBE client builder PASS node={} slots={}", node.id(), slots.size());
            }
        } catch (RuntimeException | AssertionError failure) {
            GraphAssembly.LOGGER.error("RELEASE_PROBE FAILED", failure);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
