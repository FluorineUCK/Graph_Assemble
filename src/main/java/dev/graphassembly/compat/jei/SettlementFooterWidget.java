package dev.graphassembly.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.graphassembly.client.gui.GraphGuiRenderer;
import dev.graphassembly.client.gui.GraphGuiAssets.Sprite;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import dev.graphassembly.client.view.SettlementFooterLayout;
import dev.graphassembly.client.view.SettlementGroups;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.FinishMode;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Unscaled 22px footer and bounded overlays. All hit targets are prepared before lookup or drawing. */
final class SettlementFooterWidget {
    // GraphWidget clears preview depth before this later-drawn overlay. Native items add 150,
    // counts/slot overlays add 200; both must remain strictly below GuiGraphics tooltip z=400.
    static final int POPUP_Z = 100;
    private record Control(Bounds bounds, Component label, Runnable action, boolean green, boolean enabled,
                           SettlementSlots.Entry preview, int candidateCount, Component tooltip) {}
    private record Visible(SettlementSlots.Entry entry, Bounds bounds) {}
    private record Status(Bounds bounds, Component text, String color) {}
    private final GraphDefinition graph;
    private final GraphViewState view;
    private final SettlementGroups groups;
    private final SettlementSlots slots;
    private final SettlementFooterLayout layout;
    private final List<Control> controls = new ArrayList<>();
    private final List<Visible> visible = new ArrayList<>();
    private final List<Status> statuses = new ArrayList<>();
    private List<Control> backgroundControls = List.of();
    private List<Visible> backgroundSlots = List.of();
    private List<Status> backgroundStatuses = List.of();
    private final SettlementPopupState popup = new SettlementPopupState();
    private SettlementFooterLayout.Popup popupLayout;
    private List<SettlementSlots.Entry> popupEntries = List.of();
    private Component popupTitle;
    private int pressedControl = -1;
    private double pressX, pressY;
    private boolean dragged;
    private int keyboardControl = -1;
    private boolean keyboardCandidates;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    SettlementFooterWidget(GraphDefinition graph, GraphViewState view, int width, int footerY) {
        this.graph = graph; this.view = view;
        groups = SettlementGroups.from(graph);
        if (view.selectedFinish != null && groups.finishes().stream().noneMatch(f -> f.id().equals(view.selectedFinish))) view.overview();
        slots = new SettlementSlots(graph, groups);
        layout = SettlementFooterLayout.of(width, footerY);
        SettlementPopupEvents.install();
    }
    List<IRecipeSlotDrawable> allSlots() { return slots.all; }
    boolean popupOpen() { return popup.open(); }
    void closePopup() { popup.close(); }
    private SettlementGroups.Finish finish() {
        return groups.finishes().stream().filter(f -> f.id().equals(view.selectedFinish)).findFirst().orElse(null);
    }
    private static Component text(String key, Object... args) { return SettlementInfo.text("footer." + key, args); }
    private void control(Bounds b, Component label, Runnable action, boolean green, boolean enabled) {
        control(b, label, action, green, enabled, label);
    }
    private void control(Bounds b, Component label, Runnable action, boolean green, boolean enabled, Component tooltip) {
        if (b.right() > b.left() && b.bottom() > b.top()) controls.add(new Control(b, label, action, green, enabled, null, 0, tooltip));
    }
    private void group(Bounds area, Component label, List<SettlementSlots.Entry> entries, Component empty) {
        int width = (int)(area.right() - area.left());
        if (width <= 0) return;
        if (layout.mode() == SettlementFooterLayout.Mode.AGGREGATE) {
            var b = new Bounds(area.left(), area.top() + 2, area.left() + 18, area.top() + 20);
            controls.add(new Control(b, text("group_count", label, entries.size()),
                () -> openCandidates(entries, area, entries.isEmpty() ? empty : label), false, true,
                entries.isEmpty() ? null : entries.getFirst(), entries.size(), text("group_count", label, entries.size())));
            return;
        }
        var font = Minecraft.getInstance().font;
        int labelWidth = font.width(label);
        int cellWidth = Math.min(tileWidth(entries), Math.max(18, width));
        if (SettlementFooterLayout.capacity(width, labelWidth, cellWidth) < Math.min(entries.size(), 1)) labelWidth = 0;
        String role = area.equals(layout.input()) ? "footer.input" : area.equals(layout.failure()) ? "footer.scrap" : "footer.success";
        if (entries.isEmpty()) { statuses.add(new Status(area, empty, role)); return; }
        if (labelWidth > 0) statuses.add(new Status(new Bounds(area.left(), area.top(), area.left() + labelWidth, area.bottom()), label, role));
        var positions = layout.slots(area, labelWidth, entries.size(), cellWidth);
        var overflow = SettlementFooterLayout.overflow(entries.size(), positions.size());
        for (int i = 0; i < overflow.visibleCount(); i++) visible.add(new Visible(entries.get(i), positions.get(i)));
        if (overflow.hiddenCount() > 0 && !positions.isEmpty()) {
            var anchor = positions.getLast();
            control(anchor, Component.literal("+" + overflow.hiddenCount()), () -> openCandidates(entries, area, label), false, true, null);
        }
    }
    private void prepare() {
        controls.clear(); visible.clear(); statuses.clear();
        var finish = finish();
        if (layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY) {
            control(layout.overview(), text("results"), () -> openCandidates(allResults(), layout.overview(), text("results")), popup.open(), true, text("results_hint"));
        } else {
            group(layout.input(), text("input"), slots.inputs, text("no_input"));
            if (finish == null) group(layout.overview(), text("success"), slots.overview,
                text(groups.finishes().isEmpty() ? "no_finishes" : "no_success"));
            else {
                group(layout.success(), text("success"), slots.success.getOrDefault(finish.id(), List.of()), text("no_success"));
                group(layout.failure(), text("failure"), slots.scrap.getOrDefault(finish.id(), List.of()),
                    text(finish.mode() == FinishMode.LENIENT ? "continue" : "consumed"));
            }
            int index = finish == null ? 0 : groups.finishes().indexOf(finish) + 1;
            Component label = groups.finishes().isEmpty() ? text("no_finishes") : finish == null ? text("all")
                : Component.literal(index + "/" + groups.finishes().size() + " ▾");
            if (finish == null && !groups.finishes().isEmpty() && Minecraft.getInstance().font.width(label) > layout.selector().right() - layout.selector().left() - 6)
                label = text("all_short");
            control(layout.selector(), label, this::openSelector, popup.open() || finish != null, !groups.finishes().isEmpty());
        }
        backgroundControls = List.copyOf(controls); backgroundSlots = List.copyOf(visible); backgroundStatuses = List.copyOf(statuses);
        if (popup.open()) {
            visible.clear(); controls.clear(); statuses.clear();
            control(popupLayout.close(), Component.literal("×"), popup::close, false, true, null);
            if (popup.kind == SettlementPopupState.Kind.CANDIDATES) {
                if (layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY && popupLayout.bounds().right() - popupLayout.bounds().left() >= 90)
                    control(popupLayout.back(), text("exits"), this::openSelector, false, !groups.finishes().isEmpty());
                for (int i = 0; i < popupLayout.pageCapacity() && popup.offset + i < popupEntries.size(); i++)
                    visible.add(new Visible(popupEntries.get(popup.offset + i), popupLayout.cell(i)));
                if (popupLayout.pages() > 1) {
                    control(popupLayout.previous(), Component.literal("‹"), () -> popup.scroll(-1), false, popup.offset > 0, text("previous_page"));
                    control(popupLayout.next(), Component.literal("›"), () -> popup.scroll(1), false,
                        popup.offset + popup.capacity < popup.count, text("next_page"));
                }
            }
        }
        for (var item : visible) item.entry().slot().setPosition(itemX(item.bounds()), (int)item.bounds().top() + 1);
    }
    private List<SettlementSlots.Entry> allResults() {
        List<SettlementSlots.Entry> all = new ArrayList<>(slots.inputs);
        var finish = finish();
        all.addAll(finish == null ? slots.overview : slots.success.getOrDefault(finish.id(), List.of()));
        if (finish != null) all.addAll(slots.scrap.getOrDefault(finish.id(), List.of()));
        return all;
    }
    private void openSelector() {
        popupLayout = layout.selectorPopup(groups.finishes().size() + 1);
        int selected = 0;
        for (int i = 0; i < groups.finishes().size(); i++) if (groups.finishes().get(i).id().equals(view.selectedFinish)) selected = i + 1;
        popup.open(SettlementPopupState.Kind.SELECTOR, groups.finishes().size() + 1, popupLayout.visibleRows(), selected);
        popupTitle = text("choose");
    }
    private void openCandidates(List<SettlementSlots.Entry> entries, Bounds anchor, Component title) {
        popupEntries = entries;
        int headerWidth = 2 * SettlementFooterLayout.POPUP_INSET + 18 + 6 + Minecraft.getInstance().font.width(title)
            + (layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY ? 46 : 0);
        popupLayout = layout.candidatePopup(anchor, entries.size(), tileWidth(entries), Math.max(96, headerWidth));
        popup.open(SettlementPopupState.Kind.CANDIDATES, entries.size(), popupLayout.pageCapacity(), 0);
        popupTitle = title;
        keyboardCandidates = false;
    }
    private void select(int index) {
        if (index == 0) view.overview();
        else if (index <= groups.finishes().size()) view.selectFinish(graph, groups.finishes().get(index - 1).id());
        popup.close();
    }
    Optional<RecipeSlotUnderMouse> slotUnderMouse(double x, double y) {
        prepare();
        for (var item : visible) if (item.entry().slot().isMouseOver(x, y))
            return Optional.of(new RecipeSlotUnderMouse(item.entry().slot(), 0, 0));
        return Optional.empty();
    }
    void draw(GuiGraphics g, double x, double y) {
        prepare();
        var font = Minecraft.getInstance().font;
        if (popup.open()) {
            drawElements(g, -1, -1, backgroundControls, backgroundSlots, backgroundStatuses, false);
            g.pose().pushPose(); g.pose().translate(0, 0, POPUP_Z);
            GraphGuiRenderer.sprite(g, Sprite.GRAPH_PANEL, popupLayout.bounds());
            drawText(g, popupTitle, popupLayout.title(layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY
                && popup.kind == SettlementPopupState.Kind.CANDIDATES));
            if (popup.kind == SettlementPopupState.Kind.SELECTOR) {
                for (int i = 0; i < popupLayout.visibleRows() && popup.offset + i < popup.count; i++) {
                    int index = popup.offset + i;
                    var row = popupLayout.row(i);
                    boolean selected = index == 0 ? view.selectedFinish == null : groups.finishes().get(index - 1).id().equals(view.selectedFinish);
                    Component label = index == 0 ? text("all") : Component.literal(index + ". " + groups.finishes().get(index - 1).id());
                    var button = GraphGuiRenderer.textButton(row, Component.empty());
                    button.green = selected || index == popup.cursor;
                    button.doRender(g, (int)x, (int)y, 0);
                    if (selected) drawText(g, Component.literal("✓"), new Bounds(row.left() + 4, row.top(), row.left() + 14, row.bottom()));
                    Bounds labelArea = popupLayout.rowLabel(i);
                    if (index > 0) {
                        var entries = slots.success.getOrDefault(groups.finishes().get(index - 1).id(), List.of());
                        var preview = popupLayout.rowPreview(i);
                        if (!entries.isEmpty()) {
                            g.renderItem(entries.getFirst().stack(), (int)preview.left() + 1, (int)preview.top() + 1);
                            g.renderItemDecorations(font, entries.getFirst().stack(), (int)preview.left() + 1, (int)preview.top() + 1);
                        }
                        if (entries.size() > 1) {
                            String extra = "+" + (entries.size() - 1);
                            double badgeLeft = Math.max(labelArea.left(), preview.left() - 4 - font.width(extra));
                            drawText(g, Component.literal(extra), new Bounds(badgeLeft, row.top(), preview.left() - 4, row.bottom()));
                            labelArea = new Bounds(labelArea.left(), row.top(), Math.max(labelArea.left(), badgeLeft - 6), row.bottom());
                        }
                    }
                    drawText(g, label, labelArea);
                }
                GraphGuiRenderer.scrollbar(g, popupLayout.scrollThumb(popup.count, popup.offset));
            } else if (popupLayout.pages() > 1) {
                Component page = Component.literal((popup.offset / popup.capacity + 1) + "/" + popupLayout.pages());
                var area = popupLayout.pageLabel(font.width(page));
                drawText(g, page, area);
            }
        }
        drawElements(g, x, y, controls, visible, statuses, popup.open());
        if (popup.open()) g.pose().popPose();
        SettlementPopupEvents.rendered(this, g, layout.width(), layout.footerY() + 22);
    }
    private void drawElements(GuiGraphics g, double x, double y, List<Control> buttons, List<Visible> items,
                              List<Status> texts, boolean overlay) {
        for (var status : texts) drawText(g, status.text(), status.bounds(), GraphGuiRenderer.color(status.color()));
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            int itemX = itemX(item.bounds()), itemY = (int)item.bounds().top() + 1;
            item.entry().slot().setPosition(itemX, itemY);
            // Only the tile spacing grows; the artist's 18px slot and native 16px item are not stretched.
            GraphGuiRenderer.sprite(g, Sprite.FILTER_ICON_SLOT, itemX - 1, itemY - 1, 18, 18);
            item.entry().slot().draw(g);
            if (item.entry().slot().isMouseOver(x, y) || overlay && popup.kind == SettlementPopupState.Kind.CANDIDATES && popup.offset + i == popup.cursor)
                item.entry().slot().drawHoverOverlays(g);
            drawQuantity(g, item.entry().quantityLabel(), item.bounds());
        }
        for (int i = 0; i < buttons.size(); i++) {
            var c = buttons.get(i); var button = GraphGuiRenderer.textButton(c.bounds(), c.preview() == null ? c.label() : Component.empty());
            button.active = c.enabled(); button.green = c.green() || !popup.open() && keyboardControl == i;
            button.doRender(g, (int)x, (int)y, 0);
            if (c.preview() != null) {
                g.renderItem(c.preview().stack(), (int)c.bounds().left() + 1, (int)c.bounds().top() + 1);
                String count = c.candidateCount() > 99 ? "99+" : Integer.toString(c.candidateCount());
                g.pose().pushPose(); g.pose().translate(c.bounds().right() - 1, c.bounds().bottom() - 6, 200);
                g.pose().scale(.65f, .65f, 1);
                g.drawString(Minecraft.getInstance().font, count, -Minecraft.getInstance().font.width(count), 0,
                    GraphGuiRenderer.color("text.primary"), true);
                g.pose().popPose();
            }
        }
    }
    private static int itemX(Bounds tile) {
        return (int)tile.left() + Math.max(1, ((int)(tile.right() - tile.left()) - 16) / 2);
    }
    private static int tileWidth(List<SettlementSlots.Entry> entries) {
        var font = Minecraft.getInstance().font;
        return entries.stream().mapToInt(entry -> Math.max(18, Math.min(48, font.width(entry.quantityLabel()) + 4))).max().orElse(18);
    }
    private static void drawQuantity(GuiGraphics g, String label, Bounds tile) {
        if (label.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int available = Math.max(0, (int)(tile.right() - tile.left()) - 2);
        String shown = font.width(label) <= available ? label : font.plainSubstrByWidth(label, Math.max(0, available - font.width("…"))) + "…";
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 200); // Same layer as native counts; below JEI tooltips even inside a popup.
            g.drawString(font, shown, (int)tile.right() - 1 - font.width(shown), (int)tile.bottom() - font.lineHeight,
                GraphGuiRenderer.color("text.primary"), true);
        } finally { g.pose().popPose(); }
    }
    private static void drawText(GuiGraphics g, Component text, Bounds b) {
        drawText(g, text, b, GraphGuiRenderer.color("text.primary"));
    }
    private static void drawText(GuiGraphics g, Component text, Bounds b, int color) {
        int available = Math.max(0, (int)(b.right() - b.left()));
        var font = Minecraft.getInstance().font;
        String full = text.getString();
        String shown = font.width(full) <= available ? full : available < font.width("…") ? ""
            : font.plainSubstrByWidth(full, available - font.width("…")) + "…";
        g.drawString(font, shown, (int)b.left(), (int)b.top() + Math.max(0, ((int)(b.bottom() - b.top()) - font.lineHeight) / 2),
            color, false);
    }
    void tooltip(ITooltipBuilder tooltip, double x, double y) {
        prepare();
        // Slot tooltip is owned by JEI's slotted-widget path, not duplicated here.
        if (slotUnderMouse(x, y).isPresent()) return;
        // A wide quantity label can extend outside the native 16px slot; keep its explanation accessible.
        for (var item : visible) if (item.bounds().contains(x, y)) {
            item.entry().slot().getTooltip(tooltip); return;
        }
        for (var c : controls) if (c.bounds().contains(x, y)) {
            // Symbol-only close/overflow controls intentionally have no tooltip, including no fallback hint.
            if (c.tooltip() == null) return;
            tooltip.add(c.tooltip());
            if (c.bounds().equals(layout.selector())) {
                tooltip.add(text("choose"));
                if (view.selectedFinish != null) tooltip.add(Component.literal(view.selectedFinish));
                tooltip.add(text("selector_hint"));
            }
            return;
        }
        for (var status : statuses) if (status.bounds().contains(x, y)) { tooltip.add(status.text()); return; }
        if (popup.open() && popupLayout.bounds().contains(x, y) && y < popupLayout.content().top()) {
            tooltip.add(popupTitle);
            if (layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY) tooltip.add(text("results_hint"));
        }
        if (popup.open() && popup.kind == SettlementPopupState.Kind.SELECTOR) {
            int index = rowUnderMouse(x, y);
            if (index >= 0) {
                tooltip.add(index == 0 ? text("all") : Component.literal(groups.finishes().get(index - 1).id()));
                tooltip.add(text("selector_hint"));
            }
        }
    }
    private int rowUnderMouse(double x, double y) {
        if (!popup.open() || popup.kind != SettlementPopupState.Kind.SELECTOR) return -1;
        for (int i = 0; i < popupLayout.visibleRows(); i++) if (popupLayout.row(i).contains(x, y) && popup.offset + i < popup.count) return popup.offset + i;
        return -1;
    }
    boolean input(double x, double y, IJeiUserInput input) {
        prepare();
        boolean mouse = input.getKey().getType() == InputConstants.Type.MOUSE;
        if (!mouse) {
            if (popup.open() && slotUnderMouse(x, y).isPresent() && GraphJeiPlugin.runtime() != null
                && (input.is(GraphJeiPlugin.runtime().getKeyMappings().getShowRecipe()) || input.is(GraphJeiPlugin.runtime().getKeyMappings().getShowUses()))) return false;
            if (input.isSimulate()) return popup.open() || input.getKey().getValue() == GLFW.GLFW_KEY_TAB;
            boolean wasOpen = popup.open();
            return key(input.getKey().getValue(), input.getModifiers()) || wasOpen;
        }
        if (input.getKey().getValue() != 0) return popup.open() && slotUnderMouse(x, y).isEmpty();
        if (popup.open()) {
            if (input.isSimulate() && popup.beginScrollbar(popupLayout, x, y)) return true;
            if (!input.isSimulate() && popup.scrolling()) { popup.endScrollbar(popupLayout, y); return true; }
        }
        int row = rowUnderMouse(x, y);
        int control = -1;
        for (int i = 0; i < controls.size(); i++) if (controls.get(i).bounds().contains(x, y)) { control = i; break; }
        if (popup.open()) {
            if (slotUnderMouse(x, y).isPresent()) return false;
            int target = row >= 0 ? row : control >= 0 ? popup.count + control : popupLayout.bounds().contains(x, y) ? -2 : -1;
            if (input.isSimulate()) popup.press(target, x, y);
            else {
                int chosen = popup.release(target, x, y);
                if (chosen >= popup.count) { var c = controls.get(chosen - popup.count); if (c.enabled()) c.action().run(); }
                else if (chosen >= 0) select(chosen);
            }
            return true;
        }
        if (y < layout.footerY()) return false;
        if (slotUnderMouse(x, y).isPresent()) return false;
        if (input.isSimulate()) { pressedControl = control; pressX = x; pressY = y; dragged = false; }
        else {
            if (!dragged && control >= 0 && control == pressedControl && Math.hypot(x - pressX, y - pressY) <= 4) {
                var c = controls.get(control); if (c.enabled()) c.action().run();
            }
            pressedControl = -1;
        }
        return true;
    }
    void moved(double x, double y) {
        if (popup.open()) popup.dragScrollbar(popupLayout, y);
        popup.moved(x, y);
        if (x != lastMouseX || y != lastMouseY) keyboardCandidates = false;
        lastMouseX = x; lastMouseY = y;
        if (Math.hypot(x - pressX, y - pressY) > 4) dragged = true;
    }
    boolean scroll(double amount) {
        if (!popup.open()) return false;
        popup.scroll(amount > 0 ? -1 : 1); return true;
    }
    boolean nativeQueryKey(InputConstants.Key key) {
        var runtime = GraphJeiPlugin.runtime();
        return popup.open() && popup.kind == SettlementPopupState.Kind.CANDIDATES && !keyboardCandidates && runtime != null
            && (runtime.getKeyMappings().getShowRecipe().isActiveAndMatches(key) || runtime.getKeyMappings().getShowUses().isActiveAndMatches(key));
    }
    boolean queryKey(InputConstants.Key key) {
        var runtime = GraphJeiPlugin.runtime();
        if (!popup.open() || popup.kind != SettlementPopupState.Kind.CANDIDATES || !keyboardCandidates || runtime == null) return false;
        boolean recipe = runtime.getKeyMappings().getShowRecipe().isActiveAndMatches(key);
        boolean uses = runtime.getKeyMappings().getShowUses().isActiveAndMatches(key);
        if (!recipe && !uses) return false;
        queryFocused(recipe); return true;
    }
    private void queryFocused(boolean recipe) {
        var runtime = GraphJeiPlugin.runtime();
        if (runtime == null || popup.cursor < 0 || popup.cursor >= popupEntries.size()) return;
        popupEntries.get(popup.cursor).slot().getDisplayedIngredient().ifPresent(ingredient -> {
            popup.close(); // navigation can reuse the same RecipesGui Screen; never retain an active old overlay.
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(
                recipe ? mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT : mezz.jei.api.recipe.RecipeIngredientRole.INPUT, ingredient));
        });
    }
    boolean key(int key, int modifiers) {
        prepare();
        if (popup.open()) {
            boolean previousKeyboardFocus = keyboardCandidates;
            keyboardCandidates = true;
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> popup.close();
                case GLFW.GLFW_KEY_UP -> popup.move(popup.kind == SettlementPopupState.Kind.SELECTOR ? -1 : -Math.max(1, popupLayout.columns()));
                case GLFW.GLFW_KEY_DOWN -> popup.move(popup.kind == SettlementPopupState.Kind.SELECTOR ? 1 : Math.max(1, popupLayout.columns()));
                case GLFW.GLFW_KEY_LEFT -> popup.move(-1);
                case GLFW.GLFW_KEY_RIGHT -> popup.move(1);
                case GLFW.GLFW_KEY_PAGE_UP -> popup.scroll(-1);
                case GLFW.GLFW_KEY_PAGE_DOWN -> popup.scroll(1);
                case GLFW.GLFW_KEY_TAB -> {
                    if (popup.kind == SettlementPopupState.Kind.CANDIDATES && layout.mode() == SettlementFooterLayout.Mode.RESULTS_ONLY
                        && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !groups.finishes().isEmpty()) openSelector();
                    else popup.move((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    if (popup.kind == SettlementPopupState.Kind.SELECTOR) select(popup.cursor);
                    else queryFocused(true);
                }
                default -> { keyboardCandidates = previousKeyboardFocus; return false; }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && !controls.isEmpty()) {
            keyboardControl = Math.floorMod(keyboardControl + ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1), controls.size());
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && keyboardControl >= 0 && keyboardControl < controls.size()) {
            var c = controls.get(keyboardControl); if (c.enabled()) c.action().run(); return true;
        }
        return false;
    }
}
