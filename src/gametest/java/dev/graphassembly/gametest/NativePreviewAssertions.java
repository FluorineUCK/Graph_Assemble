package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.graphassembly.core.GraphCodec;
import dev.graphassembly.integration.NativeSteps;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;

/** Data-only server boundary. Actual subcategory slots and models require the client acceptance run. */
final class NativePreviewAssertions {
    static void verify(GameTestHelper helper) {
        String[] processing = {
            "{\"type\":\"create:pressing\"}",
            "{\"type\":\"create:cutting\",\"processing_time\":20}",
            "{\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],\"keep_held_item\":true}",
            "{\"type\":\"create:filling\",\"ingredients\":[{\"type\":\"neoforge:compound\",\"ingredients\":[{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\"},{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:lava\"}],\"amount\":250}]}",
            "{\"type\":\"create:milling\",\"processing_time\":20}"
        };
        for (int i = 0; i < processing.length; i++) {
            var graph = GraphCodec.parse("test:native_preview", JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":%s}],"edges":[]}
                """.formatted(processing[i])).getAsJsonObject());
            var input = NativeSteps.stack(graph.transitionalItem()); var before = input.copy();
            var recipe = NativeSteps.create(helper.getLevel(), graph, graph.nodes().get("s"), input);
            if (i < 4) check(new SequencedRecipe<>(recipe).getAsAssemblyRecipe().supportsAssembly(), "Expected native sequence step");
            else check(!(recipe instanceof IAssemblyRecipe), "Millstone must not use sequence cast");
            if (i == 2) {
                check(((DeployerApplicationRecipe) recipe).shouldKeepHeldItem(), "Native no-consumption flag lost");
                check(recipe.getIngredients().get(1).test(Items.STICK.getDefaultInstance()), "Auxiliary item lost");
            }
            if (i == 3) {
                var fluids = recipe.getFluidIngredients().getFirst().getFluids();
                check(fluids.length == 2, "Native fluid alternatives lost");
                for (var fluid : fluids) check(fluid.getAmount() == 250, "Fluid amount lost");
            }
            check(net.minecraft.world.item.ItemStack.matches(before, input), "Preview decoding mutated the workpiece");
        }
        helper.succeed();
    }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
