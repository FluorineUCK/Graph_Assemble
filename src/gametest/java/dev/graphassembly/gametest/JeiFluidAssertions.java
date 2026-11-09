package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.compat.jei.GraphRecipeIngredients;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.integration.NativeSteps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Loaded only by the optional JEI GameTest. Records the external JEI builder API boundary. */
final class JeiFluidAssertions {
    private record Entry(RecipeIngredientRole role, Object type, List<?> ingredients) {}

    static void verify(GameTestHelper helper) {
        assertFluids(helper, """
            {"type":"neoforge:single","fluid":"minecraft:water","amount":250}
            """);
        assertFluids(helper, """
            {"type":"neoforge:tag","tag":"minecraft:water","amount":500}
            """);
        assertFluids(helper, """
            {"type":"neoforge:compound","ingredients":[
              {"type":"neoforge:single","fluid":"minecraft:water"},
              {"type":"neoforge:single","fluid":"minecraft:lava"}],"amount":750}
            """);
        assertFluids(helper, """
            {"type":"neoforge:components","fluids":["minecraft:water","minecraft:lava"],
             "components":{"minecraft:custom_data":{"variant":"jei-test"}},"strict":true,"amount":375}
            """);
        var deploy = recipe(helper, """
            {"type":"create:deploying","ingredients":[{"item":"minecraft:stick"}],"results":[{"id":"minecraft:flint"}]}
            """);
        List<Entry> items = new ArrayList<>();
        GraphRecipeIngredients.addProcessing(builder(items), deploy);
        check(items.size() == 2 && items.stream().allMatch(e -> e.type() == VanillaTypes.ITEM_STACK),
            "Auxiliary item and byproduct indexes must remain intact, without indexing the transitional main input/output");
        check(items.stream().anyMatch(e -> e.role() == RecipeIngredientRole.INPUT && ((ItemStack)e.ingredients().getFirst()).is(Items.STICK)), "Missing auxiliary input");
        check(items.stream().anyMatch(e -> e.role() == RecipeIngredientRole.OUTPUT && ((ItemStack)e.ingredients().getFirst()).is(Items.FLINT)), "Missing native byproduct");
        helper.succeed();
    }

    private static ProcessingRecipe<?, ?> recipe(GameTestHelper helper, String processing) {
        var graph = GraphCodec.parse("test:jei_fluid", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":%s}],"edges":[]}
            """.formatted(processing)).getAsJsonObject());
        return NativeSteps.create(helper.getLevel(), graph, graph.nodes().get("s"), NativeSteps.stack(graph.defaults().transitionalItem()));
    }

    private static void assertFluids(GameTestHelper helper, String fluid) {
        var recipe = recipe(helper, "{\"type\":\"create:filling\",\"ingredients\":[" + fluid + "]}");
        List<Entry> registered = new ArrayList<>();
        GraphRecipeIngredients.addProcessing(builder(registered), recipe);
        var fluids = registered.stream().filter(e -> e.role() == RecipeIngredientRole.INPUT && e.type() == NeoForgeTypes.FLUID_STACK)
            .flatMap(e -> e.ingredients().stream()).map(FluidStack.class::cast).toList();
        var expected = recipe.getFluidIngredients().getFirst().getFluids();
        check(expected.length > 0 && fluids.size() == expected.length,
            "JEI input lookup must receive every native FluidStack candidate; got " + fluids + " for " + fluid);
        for (int i = 0; i < expected.length; i++)
            check(fluids.get(i).getAmount() == expected[i].getAmount() && FluidStack.isSameFluidSameComponents(expected[i], fluids.get(i)),
                "JEI must preserve native fluid type, required amount and components");
        if (fluid.contains("neoforge:components")) check(!fluids.getFirst().getComponentsPatch().isEmpty(), "Component fixture must actually have components");
    }

    private static IRecipeLayoutBuilder builder(List<Entry> entries) {
        return (IRecipeLayoutBuilder) Proxy.newProxyInstance(JeiFluidAssertions.class.getClassLoader(), new Class<?>[]{IRecipeLayoutBuilder.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("addInvisibleIngredients")) throw new AssertionError("Unexpected layout call: " + method);
                var role = (RecipeIngredientRole) args[0];
                return Proxy.newProxyInstance(JeiFluidAssertions.class.getClassLoader(), new Class<?>[]{IIngredientAcceptor.class},
                    (acceptor, call, values) -> {
                        if (call.isDefault()) return InvocationHandler.invokeDefault(acceptor, call, values);
                        if (call.getName().equals("addIngredients")) entries.add(new Entry(role, values[0], List.copyOf((List<?>) values[1])));
                        else if (call.getName().equals("addIngredient")) entries.add(new Entry(role, values[0], List.of(values[1])));
                        else throw new AssertionError("Unexpected ingredient call: " + call);
                        return acceptor;
                    });
            });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
