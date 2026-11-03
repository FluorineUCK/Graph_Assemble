package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.*;
import dev.graphassembly.filter.*;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.filter.FilterConfiguration.Mode;
import dev.graphassembly.recipe.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class FilterRuleGameTests {
    private static final String ID = "test:filter_modes";
    private static GraphRecipe recipe(boolean random) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"branch_mode":"%s","recipe":{"type":"create:pressing"}},
                      {"id":"a","rewards":{"x":1},"recipe":{"type":"create:cutting"}},
                      {"id":"b","rewards":{"x":1},"recipe":{"type":"create:filling","ingredients":[{"type":"neoforge:single","fluid":"minecraft:water","amount":250}]}}],
             "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]}
            """.formatted(random ? "random" : "choice")).getAsJsonObject());
    }
    private static GraphRecipe recipeWithoutB() {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"branch_mode":"choice","recipe":{"type":"create:pressing"}},
                      {"id":"a","rewards":{"x":1},"recipe":{"type":"create:cutting"}}],
             "edges":[{"from":"s","to":"a"}]}
            """).getAsJsonObject());
    }
    private static FilterSelection rule(Mode mode, List<String> targets, String... inverted) {
        return new FilterSelection(ID, targets, Set.of(inverted), mode);
    }
    private static ItemStack filter(FilterSelection selection) {
        var stack = GraphAssembly.FILTER.get().getDefaultInstance();
        stack.set(GraphAssembly.FILTER_SELECTION.get(), selection.toJson()); return stack;
    }
    private static boolean matches(ServerLevel level, ItemStack input, FilterSelection selection) {
        return FilterItemStack.of(filter(selection)).test(level, input);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void scopedSnapshotsFollowComponentsAndCurrentRecipes(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), recipe(false)))); GraphIndex.invalidate();
            var initialIndex = GraphIndex.get(level);
            check(GraphIndex.get(level) == initialIndex, "Unchanged recipe holders must reuse the current GraphIndex");
            var graph = initialIndex.graphs().get(ID);
            var initial = GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> 0).state();
            var piece = Workpiece.write(new ItemStack(Items.IRON_NUGGET), initial);
            var cached = Workpiece.read(level, piece);
            check(cached == Workpiece.read(level, piece), "Same immutable component must return the same parsed snapshot");
            var changed = new Progress(ID, "s", "s", Map.of(), List.of("s", "s"), Map.of(), null, List.of("s"));
            piece.set(GraphAssembly.PROGRESS.get(), changed.toJson().toString());
            check(Workpiece.read(level, piece).equals(changed), "Component replacement must be visible immediately");
            piece.set(GraphAssembly.PROGRESS.get(), initial.toJson().toString());
            check(Workpiece.read(level, piece) == cached, "Restored equal content may reuse its immutable snapshot");
            var longState = new Progress(ID, "s", "s", Map.of(), Collections.nCopies(100000, "s"), Map.of(), null, List.of("s"));
            var longPiece = Workpiece.write(new ItemStack(Items.IRON_NUGGET), longState);
            var longCached = Workpiece.read(level, longPiece);
            check(longCached.checkpoints().size() == 100000 && longCached == Workpiece.read(level, longPiece), "100000 checkpoints remain complete and fit the snapshot budget");
            var filter = filter(rule(Mode.ALL, List.of("a", "b")));
            var cachedFilter = GraphFilterItem.selection(level, filter);
            check(cachedFilter == GraphFilterItem.selection(level, filter), "Filter component must reuse parsed selection");
            check(FilterItemStack.of(filter).test(level, piece), "Current target choice must match");
            var invertedNone = filter(rule(Mode.NONE, List.of("b"), "b"));
            var cachedInvertedNone = GraphFilterItem.selection(level, invertedNone);
            check(FilterItemStack.of(invertedNone).test(level, piece), "Inverted NONE fixture is valid before reload");
            filter.set(GraphAssembly.FILTER_SELECTION.get(), rule(Mode.NONE, List.of("a")).toJson());
            check(!FilterItemStack.of(filter).test(level, piece), "Changed filter component must take effect");
            filter.set(GraphAssembly.FILTER_SELECTION.get(), cachedFilter.toJson());
            check(GraphFilterItem.selection(level, filter) == cachedFilter, "Restored equal filter String must reuse the warm snapshot");
            var corrupt = piece.copy(); corrupt.set(GraphAssembly.PROGRESS.get(), "broken");
            check(GraphRuntime.diagnostic(level, corrupt).key().equals("graph_assembly.stop.invalid"), "Malformed progress diagnosis remains visible");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), recipeWithoutB())));
            // Do not manually invalidate: GraphIndex must observe changed holders on its normal read path.
            var reloadedIndex = GraphIndex.get(level);
            check(reloadedIndex != initialIndex, "Same-ID new recipe instance must rebuild GraphIndex");
            var reloaded = reloadedIndex.graphs().get(ID);
            check(reloaded != null && !reloaded.nodes().containsKey("b"), "Same-ID recipe replacement must become current without explicit invalidation; nodes="
                + (reloaded == null ? "<disabled> " + GraphIndex.get(level).disabledReason(ID) : reloaded.nodes().keySet())
                + " holders=" + manager.getAllRecipesFor(GraphAssembly.RECIPE_TYPE.get()).stream().map(h -> h.value().definition(h.id().toString()).nodes().keySet()).toList());
            check(GraphEngine.incompatibility(reloaded, cached) == null && GraphEngine.allowed(reloaded, cached).contains("a"),
                "Reloaded graph must retain an actionable domain for the warm workpiece");
            var reloadedProgress = Workpiece.read(level, piece);
            check(reloadedProgress.equals(cached) && reloadedProgress == Workpiece.read(level, piece),
                "Reload releases old cache; reparsed progress preserves contents and warms the new cache");
            var reloadedFilter = GraphFilterItem.selection(level, filter);
            check(reloadedFilter.equals(cachedFilter) && GraphFilterItem.selection(level, invertedNone).equals(cachedInvertedNone)
                    && reloadedFilter == GraphFilterItem.selection(level, filter),
                "Reloaded filter snapshots preserve contents and reuse the new cache");
            check(!FilterItemStack.of(filter).test(level, piece), "Warm ALL selection cannot admit a removed target");
            check(!FilterItemStack.of(invertedNone).test(level, piece), "Warm inverted NONE selection cannot admit a removed target");
            check(matches(level, piece, rule(Mode.ANY, List.of("a"))), "Current graph remains actionable for a valid target");
            var invalidFilter = filter(rule(Mode.ANY, List.of("a")));
            invalidFilter.set(GraphAssembly.FILTER_SELECTION.get(), "broken");
            check(GraphFilterItem.selection(level, invalidFilter) == FilterSelection.EMPTY && !FilterItemStack.of(invalidFilter).test(level, piece),
                "Malformed selection String must fail closed on Level-aware selection and wrapper");
            invalidFilter.set(GraphAssembly.FILTER_SELECTION.get(), rule(Mode.ANY, List.of("a")).toJson() + " ".repeat(FilterConfiguration.MAX_JSON_BYTES));
            check(GraphFilterItem.selection(level, invalidFilter) == FilterSelection.EMPTY && !FilterItemStack.of(invalidFilter).test(level, piece),
                "Over-capacity selection String must fail closed, not truncate to an actionable prefix");
            manager.replaceRecipes(List.of());
            check(GraphRuntime.diagnostic(level, piece).key().equals("graph_assembly.stop.missing"), "Warm parsed progress must not hide missing graph");
            check(!FilterItemStack.of(filter).test(level, piece), "Same warm filter/progress cannot reuse stale graph match");
            helper.succeed();
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); RuntimeSnapshots.release(manager); }
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void nativeModesRespectGraphDomainAndPersist(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), recipe(false)))); GraphIndex.invalidate();
            var graph = GraphIndex.get(level).graphs().get(ID);
            check(graph != null, "Fixture must be a supported non-conflicting graph");
            var raw = new ItemStack(Items.IRON_INGOT);
            var state = GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> 0).state();
            var piece = Workpiece.write(new ItemStack(Items.IRON_NUGGET), state);
            check(matches(level, raw, rule(Mode.ANY, List.of("a"), "a")), "Negative condition should match a valid entry where a is not available");
            check(matches(level, piece, rule(Mode.ALL, List.of("a", "b"))), "ALL matches both choice successors");
            check(!matches(level, piece, rule(Mode.ALL, List.of("a", "b"), "b")), "A negative successor cannot be simultaneously available");
            check(!matches(level, piece, rule(Mode.NONE, List.of("a", "b"))), "NONE rejects any true condition");
            check(matches(level, piece, rule(Mode.NONE, List.of("a", "b"), "a", "b")), "NONE combines already-inverted conditions like native Create");
            check(matches(level, piece, rule(Mode.ANY, List.of("s"), "s")), "Completed start is no longer a next operation");
            check(!matches(level, new ItemStack(Items.STICK), rule(Mode.ANY, List.of("a"), "a")), "Negation cannot admit unrelated raw items");
            var other = Workpiece.write(new ItemStack(Items.IRON_NUGGET), new Progress("test:other", "s", "s", Map.of(), List.of("s"), Map.of(), null, List.of("s")));
            check(!matches(level, other, rule(Mode.NONE, List.of("a"))), "Other graph is outside the filter domain");
            var corrupt = piece.copy(); corrupt.set(GraphAssembly.PROGRESS.get(), "broken");
            check(!matches(level, corrupt, rule(Mode.NONE, List.of("s"))), "Malformed progress stays rejected");
            for (var mode : Mode.values()) {
                check(!matches(level, piece, rule(mode, List.of("gone"), "gone")), "Stale negative target is never a wildcard");
                check(!matches(level, piece, rule(mode, List.of())), "Empty config must fail closed in all modes");
            }
            var stopped = Workpiece.write(new ItemStack(Items.IRON_NUGGET), new Progress(ID, "s", "s", Map.of("x", Integer.MAX_VALUE), List.of("s"), Map.of(), null, List.of("s")));
            check(!matches(level, stopped, rule(Mode.ANY, List.of("a"), "a")), "Overflow cannot be bypassed by inversion");
            var end = Workpiece.write(new ItemStack(Items.IRON_NUGGET), GraphEngine.complete(graph, state, "a", "minecraft:iron_nugget", () -> 0).state());
            check(!matches(level, end, rule(Mode.NONE, List.of("s"))), "No actionable next step must not pass an exclusion filter");
            check(state.equals(Workpiece.read(piece)), "Matching cannot consume or mutate progress");

            var selection = rule(Mode.ALL, List.of("a", "s"), "s");
            var saved = filter(selection).save(level.registryAccess());
            check(selection.equals(GraphFilterItem.selection(ItemStack.parse(level.registryAccess(), saved).orElseThrow())), "Signed conditions and mode survive item storage");
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            var previous = player.getMainHandItem();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, GraphAssembly.FILTER.get().getDefaultInstance());
                ConfigureFilterPayload.apply(player, new ConfigureFilterPayload(InteractionHand.MAIN_HAND, selection.toJson()));
                check(selection.equals(GraphFilterItem.selection(player.getMainHandItem())), "Server confirmation persists rule, not only targets");
                ConfigureFilterPayload.apply(player, new ConfigureFilterPayload(InteractionHand.MAIN_HAND, rule(Mode.NONE, List.of("gone")).toJson()));
                check(selection.equals(GraphFilterItem.selection(player.getMainHandItem())), "Stale rule must not overwrite confirmed rule");
            } finally { player.setItemInHand(InteractionHand.MAIN_HAND, previous); }

            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(ID), recipe(true)))); GraphIndex.invalidate();
            graph = GraphIndex.get(level).graphs().get(ID);
            var locked = GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> 0).state();
            piece = Workpiece.write(new ItemStack(Items.IRON_NUGGET), locked);
            check("a".equals(locked.lockedNext()), "Deterministic lock fixture");
            check(matches(level, piece, rule(Mode.ALL, List.of("a", "b"), "b")), "Only locked successor is available; not all graph edges");
            check(locked.equals(Workpiece.read(piece)), "Filter never rerolls random target");
            helper.succeed();
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); }
    }
}
