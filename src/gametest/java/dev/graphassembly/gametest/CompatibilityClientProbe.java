package dev.graphassembly.gametest;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.compat.jei.NativeStepPreview;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Test-only screen: discovery/reuse feasibility, NOT the normal GraphWidget or an execution adapter. */
@JeiPlugin
public final class CompatibilityClientProbe implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return GraphAssembly.id("compatibility_client_probe"); }
    private record Tile(String label, String route, IRecipeLayoutDrawable<?> layout) {}

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("graph_assembly.compatibilityVerification")) return;
        try {
            List<Tile> tiles = new ArrayList<>();
            var manager = runtime.getRecipeManager();
            var targets = Set.of("create:fan_blasting", "create:fan_smoking", "create:fan_washing", "create:fan_haunting",
                "create_dragons_plus:freezing", "create_dragons_plus:ending", "createaddition:charging", "createaddition:rolling",
                "create_fantasizing:exposing", "create_fantasizing:shadow_plating");
            var found = new HashSet<String>();
            var categories = manager.createRecipeCategoryLookup().get()
                .sorted(java.util.Comparator.comparing(c -> c.getRecipeType().getUid().toString())).toList();
            for (var category : categories) {
                String id = category.getRecipeType().getUid().toString();
                if (!targets.contains(id)) continue;
                found.add(id);
                addNative(runtime, category, tiles);
            }
            check(found.equals(targets), "Missing native categories: " + difference(targets, found));
            GraphAssembly.LOGGER.info("COMPAT_ANIMATION discovered={} tiles={}", found, tiles.size());
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new ProbeScreen(tiles)));
        } catch (RuntimeException | AssertionError failure) {
            GraphAssembly.LOGGER.error("COMPAT_ANIMATION FAILED", failure);
        }
    }

    private static Set<String> difference(Set<String> expected, Set<String> found) {
        var missing = new HashSet<>(expected); missing.removeAll(found); return missing;
    }

    private static <T> void addNative(IJeiRuntime runtime, IRecipeCategory<T> category, List<Tile> tiles) {
        var type = category.getRecipeType();
        var sample = runtime.getRecipeManager().createRecipeLookup(type).get().findFirst().orElseThrow();
        var focuses = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, sample, focuses).orElseThrow();
        var recipe = sample instanceof RecipeHolder<?> holder ? holder.value() : sample instanceof Recipe<?> r ? r : null;
        var id = type.getUid().toString();
        var catalysts = runtime.getRecipeManager().createRecipeCatalystLookup(type).get().toList();
        GraphAssembly.LOGGER.info("COMPAT_ANIMATION native={} class={} recipe={} slots={} catalysts={}",
            id, category.getClass().getName(), recipe == null ? sample.getClass().getName() : recipe.getClass().getName(),
            layout.getRecipeSlotsView().getSlotViews().size(), catalysts);
        tiles.add(new Tile(id, "Native JEI category", layout));
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            var preview = NativeStepPreview.create(runtime, runtime.getJeiHelpers().getGuiHelper(), processing, 0, focuses);
            String factory = recipe instanceof IAssemblyRecipe assembly && assembly.supportsAssembly()
                ? assembly.getJEISubCategory().get().get().getClass().getName() : "none";
            GraphAssembly.LOGGER.info("COMPAT_ANIMATION graphPreview={} serializer={} assemblyFactory={}",
                preview.isPresent(), BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()), factory);
            preview.ifPresent(p -> tiles.add(new Tile(id, "Graph native assembly subcategory", p.layout())));
        }
    }

    private static final class ProbeScreen extends Screen {
        private final List<Tile> tiles;
        private int page;
        private int ticks;
        private int frames;
        private final Set<String> hitsChecked = new HashSet<>();
        ProbeScreen(List<Tile> tiles) { super(Component.literal("Compatibility animation probe")); this.tiles = List.copyOf(tiles); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void tick() {
            ticks++;
            for (int i = page * 6; i < Math.min(tiles.size(), (page + 1) * 6); i++) tiles.get(i).layout.tick();
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == 262 || key == 263) {
                page = Math.floorMod(page + (key == 262 ? 1 : -1), (tiles.size() + 5) / 6);
                ticks = 0; frames = 0; return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xff30343a);
            g.drawString(font, "Native addon animations - TEST HARNESS, not graph execution", 12, 8, 0xffffff);
            g.drawString(font, "Left/Right: page " + (page + 1) + "/" + ((tiles.size() + 5) / 6) + "  Esc: close  tick=" + ticks, 12, 21, 0xffffff);
            for (int i = page * 6; i < Math.min(tiles.size(), (page + 1) * 6); i++) {
                var tile = tiles.get(i);
                int slot = i % 6;
                int x = 12 + (slot % 3) * ((width - 24) / 3);
                int y = 48 + (slot / 3) * ((height - 60) / 2);
                g.fill(x - 4, y - 4, x + (width - 24) / 3 - 8, y + (height - 60) / 2 - 8, 0xffa0a0a0);
                g.drawString(font, tile.label, x, y, 0x111111, false);
                g.drawString(font, tile.route, x, y + 12, 0x111111, false);
                tile.layout.setPosition(x + 10, y + 36);
                tile.layout.drawRecipe(g, mx, my);
                if (hitsChecked.add(tile.label + tile.route)) {
                    var rect = tile.layout.getRect();
                    var hitSlots = new HashSet<>();
                    for (int hx = rect.getX(); hx < rect.getX() + rect.getWidth(); hx++)
                        for (int hy = rect.getY(); hy < rect.getY() + rect.getHeight(); hy++)
                            tile.layout.getSlotUnderMouse(hx, hy).ifPresent(h -> hitSlots.add(h.slot()));
                    int count = tile.layout.getRecipeSlotsView().getSlotViews().size();
                    check(hitSlots.size() == count, "Translated native slots unreachable: " + tile.label);
                    GraphAssembly.LOGGER.info("COMPAT_ANIMATION translatedHits PASS id={} route={} slots={} x={} y={}", tile.label, tile.route, count, rect.getX(), rect.getY());
                }
            }
            frames++;
            if (frames == 1 || frames == 60 || frames == 180)
                GraphAssembly.LOGGER.info("COMPAT_ANIMATION rendered page={} frames={} ticks={}", page + 1, frames, ticks);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
