package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/** Optional-addon tests use real belt APIs, not synthetic progress advancement. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class ForeignGraphGameTests {
    private static final String GRAPH = "test:foreign_inline";

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void withdrawnTunnelsRemainPreviewOnly(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("create_fantasizing")) { helper.succeed(); return; }
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var graph = new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:gold_ingot",
                 "nodes":[{"id":"expose","start":true,"recipe":{"type":"create_fantasizing:exposing"}},
                          {"id":"shadow","finish":true,"recipe":{"type":"create_fantasizing:shadow_plating"}}],
                 "edges":[{"from":"expose","to":"shadow"}],
                 "outputs":[{"results":[{"item":"minecraft:echo_shard"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(GRAPH), graph)));
            GraphIndex.invalidate();
            check(!GraphIndex.get(helper.getLevel()).graphs().containsKey(GRAPH), "Withdrawn tunnel graph must not execute");
            check(GraphIndex.get(helper.getLevel()).disabledReason(GRAPH).contains("no verified device adapter"), "Missing execution adapter is diagnosed");
            check(dev.graphassembly.integration.GraphPreviewRecipes.graphs(helper.getLevel()).stream()
                .anyMatch(g -> g.id().equals(GRAPH)), "Withdrawal must preserve graph previews");
            Object expose = machine(helper, "create_fantasizing:refined_radiance_tunnel", 1);
            var outputs = new ArrayList<ItemStack>();
            check(!(boolean)call(expose, "tryProcessOnBelt", new TransportedItemStack(new ItemStack(Items.IRON_INGOT)), outputs, false),
                "Native tunnel must not find a graph-only step");
            check(outputs.isEmpty(), "Unsupported graph does not fabricate output");
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
        helper.succeed();
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void ordinaryTunnelStillProcessesWholeStacks(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("create_fantasizing")) { helper.succeed(); return; }
        var manager = helper.getLevel().getRecipeManager();
        var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var recipe = net.minecraft.world.item.crafting.Recipe.CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                JsonParser.parseString("""
                {"type":"create_fantasizing:exposing","ingredients":[{"item":"minecraft:iron_ingot"}],"results":[{"id":"minecraft:gold_ingot"}]}
                """)).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:ordinary_exposure"), recipe)));
            GraphIndex.invalidate();
            Object expose = machine(helper, "create_fantasizing:refined_radiance_tunnel", 1);
            var result = new ArrayList<ItemStack>();
            check((boolean)call(expose, "tryProcessOnBelt", new TransportedItemStack(new ItemStack(Items.IRON_INGOT, 7)), result, false), "Ordinary tunnel processes whole stack");
            check(result.stream().allMatch(stack -> stack.is(Items.GOLD_INGOT) && !Workpiece.has(stack)), "Ordinary results remain untagged");
            check(result.stream().mapToInt(ItemStack::getCount).sum() == 7, "Whole stack count retained");
        } finally { manager.replaceRecipes(saved); GraphIndex.invalidate(); }
        helper.succeed();
    }
    static Object machine(GameTestHelper helper, String id, int x) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        var state = block.defaultBlockState();
        check(block instanceof EntityBlock, "Expected native machine block " + id);
        var machine = ((EntityBlock)block).newBlockEntity(helper.absolutePos(new BlockPos(x, 2, 1)), state);
        machine.setLevel(helper.getLevel());
        return machine;
    }
    static ItemStack tunnel(Object machine, ItemStack stack) throws Exception {
        var transported = new TransportedItemStack(stack.copy());
        var before = transported.stack.copy();
        check((boolean)call(machine, "tryProcessOnBelt", transported, null, true), "Tunnel simulation accepts graph");
        check(ItemStack.matches(before, transported.stack), "Simulation must not mutate input");
        var results = new ArrayList<ItemStack>();
        check((boolean)call(machine, "tryProcessOnBelt", transported, results, false), "Native belt processing succeeds");
        check(results.size() == 1 && results.getFirst().getCount() == 1, "One primary output");
        return results.getFirst();
    }
    static Object call(Object receiver, String name, Object... args) throws Exception { return invoke(receiver.getClass(), receiver, name, args); }
    private static Object invoke(Class<?> type, Object receiver, String name, Object[] args) throws Exception {
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            boolean matches = true;
            for (int i = 0; i < args.length; i++) if (args[i] != null && !method.getParameterTypes()[i].isInstance(args[i])
                && !(method.getParameterTypes()[i] == boolean.class && args[i] instanceof Boolean)
                && !(method.getParameterTypes()[i] == int.class && args[i] instanceof Integer)
                && !(method.getParameterTypes()[i] == long.class && args[i] instanceof Long)) matches = false;
            if (matches) { method.setAccessible(true); return method.invoke(receiver, args); }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
