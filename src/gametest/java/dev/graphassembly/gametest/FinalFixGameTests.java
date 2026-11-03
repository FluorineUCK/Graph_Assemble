package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.client.StepDescription;
import dev.graphassembly.core.Progress;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Final integration regressions: diagnostics and presentation, not new machine behavior. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class FinalFixGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void stopDiagnosticDistinguishesRoleConditionsAndRewardCapacity(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            for (int mode=0; mode<4; mode++) {
                String edges = switch (mode) {
                    case 0 -> "{\"from\":\"body\",\"to\":\"condition\"}";
                    case 1 -> "{\"from\":\"body\",\"to\":\"overflow\"}";
                    case 2 -> "{\"from\":\"body\",\"to\":\"condition\"},{\"from\":\"body\",\"to\":\"overflow\"}";
                    default -> "{\"from\":\"body\",\"to\":\"condition\"},{\"from\":\"body\",\"to\":\"overflow\"},{\"from\":\"body\",\"to\":\"safe\"}";
                };
                var graph = new GraphRecipe(JsonParser.parseString("""
                  {"ingredient":{"item":"minecraft:copper_ingot"},"transitional_item":"minecraft:iron_nugget",
                   "nodes":[{"id":"body","start":true,"recipe":{"type":"create:pressing"}},
                     {"id":"condition","recipe":{"type":"create:cutting","processing_time":20},
                      "workpiece_inputs":[{"item_input":0,"current_nodes":["body"],"requirements":{"mark_ranges":{"quality":{"min":2}}}}]},
                     {"id":"overflow","recipe":{"type":"create:pressing"},"rewards":{"load":1}},
                     {"id":"safe","recipe":{"type":"create:milling","processing_time":20}}],"edges":[%s]}
                  """.formatted(edges)).getAsJsonObject());
                manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:diagnostic"), graph))); GraphIndex.invalidate();
                var state = new Progress("test:diagnostic", "body", "body", Map.of("quality",1,"load",Integer.MAX_VALUE),
                    List.of("body"), Map.of(), null, List.of("body"));
                var diagnostic = GraphRuntime.diagnostic(helper.getLevel(), state);
                if (mode==3) check(diagnostic==null, "An allowed sibling operation must not stop the workpiece");
                else {
                    String expected = switch (mode) {
                        case 0 -> "graph_assembly.stop.conditions";
                        case 1 -> "graph_assembly.stop.capacity";
                        default -> "graph_assembly.stop.blocked";
                    };
                    check(diagnostic!=null && diagnostic.key().equals(expected), "Wrong diagnostic classification for mode " + mode + ": " + diagnostic);
                }
            }
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
        helper.succeed();
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void nonAssemblyMachinePresentationIsNotAlwaysMillstone(GameTestHelper helper) {
        var types = new ArrayList<>(List.of("create:mixing", "create:compacting", "create:milling", "create:crushing"));
        for (String type : types) {
            String duration = ",\"processing_time\":20";
            var recipe = (ProcessingRecipe<?, ?>)Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"type\":\"" + type + "\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"results\":[{\"id\":\"minecraft:iron_nugget\"}]" + duration + "}")).getOrThrow();
            String machine = switch(type) {
                case "create:mixing" -> "create:mechanical_mixer";
                case "create:compacting" -> "create:mechanical_press";
                case "create:milling" -> "create:millstone";
                default -> null;
            };
            var icon = StepDescription.machine(recipe);
            if(machine==null) check(icon.isEmpty(), "Unknown non-assembly operation must not invent a millstone");
            else check(!icon.isEmpty() && BuiltInRegistries.ITEM.getKey(icon.getItem()).toString().equals(machine), "Wrong machine for " + type + ": " + icon);
            String key = switch(type) {
                case "create:mixing" -> "graph_assembly.operation.mixing";
                case "create:compacting" -> "graph_assembly.operation.compacting";
                case "create:milling" -> "graph_assembly.operation.milling";
                default -> "graph_assembly.operation.unavailable";
            };
            check(StepDescription.describe(recipe).getContents() instanceof TranslatableContents text && text.getKey().equals(key), "Wrong operation description for " + type);
        }
        helper.succeed();
    }
    private static void check(boolean ok, String reason) { if(!ok) throw new AssertionError(reason); }
}
