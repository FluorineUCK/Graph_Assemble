package dev.graphassembly.client;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.*;
import dev.graphassembly.client.gui.GraphGuiRenderer;
import dev.graphassembly.client.gui.GraphGuiAssets.Sprite;
import dev.graphassembly.client.gui.GraphGuiAssets;
import dev.graphassembly.client.gui.FilterScreenWidget;
import static dev.graphassembly.client.gui.GraphGuiRenderer.filterColor;
import dev.graphassembly.client.view.FilterDraft;
import dev.graphassembly.client.view.FilterScreenLayout;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import dev.graphassembly.filter.*;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Create controls with a local draft, independent of JEI and container menus. */
public final class GraphFilterScreen extends Screen {
    private static final int ROWS = FilterScreenLayout.ROWS;
    private final InteractionHand hand;
    private final ItemStack editingStack, original;
    private final FilterDraft draft;
    private List<GraphDefinition<OperationSpec>> graphs = List.of();
    private final Map<String, net.minecraft.world.item.crafting.Recipe<?>> steps = new LinkedHashMap<>();
    private List<NodeDefinition<OperationSpec>> nodes = List.of();
    private int nodeIndex, listOffset;
    private FilterScreenLayout layout;
    private final List<AbstractSimiWidget> nativeWidgets = new ArrayList<>();
    private SelectionScrollInput graphSelector, nodeSelector;
    private Label graphLabel, nodeLabel;
    private IconButton add, addInverted, confirm;
    public FilterScreenLayout screenLayout() { return layout; }
    public GraphFilterScreen(InteractionHand hand) {
        super(text("title")); this.hand = hand;
        editingStack = Minecraft.getInstance().player.getItemInHand(hand); original = editingStack.copy();
        var saved = GraphFilterItem.selection(Minecraft.getInstance().level, editingStack);
        draft = new FilterDraft(saved.configuration());
    }
    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("graph_assembly.filter." + key, args);
    }
    private GraphDefinition<OperationSpec> graph() { return graphs.stream().filter(g -> g.id().equals(draft.graph())).findFirst().orElse(null); }
    private ItemStack representative(GraphDefinition<OperationSpec> graph) {
        var outputs = graph.outputRules().stream().flatMap(r -> r.leaves().stream()).toList();
        if (outputs.isEmpty()) outputs = graph.nodes().values().stream().flatMap(n -> n.settlement().outputRules().stream()).flatMap(r -> r.leaves().stream()).toList();
        if (!outputs.isEmpty()) return NativeSteps.output(minecraft.level, outputs.getFirst());
        if (graph.defaults().ingredient() != null) {
            var items = NativeSteps.ingredient(minecraft.level, graph.defaults().ingredient()).getItems();
            if (items.length > 0) return items[0];
        }
        return NativeSteps.stack(graph.defaults().transitionalItem());
    }
    private Component graphName(GraphDefinition<OperationSpec> graph) { return representative(graph).getHoverName(); }
    private Component nodeName(NodeDefinition<OperationSpec> node) {
        var recipe = steps.get(node.id());
        if (recipe == null && !node.operation().isCampfire()) return text("unavailable_node", node.id());
        var result = Component.literal((nodes.indexOf(node) + 1) + ". ").append(node.operation().isCampfire()
            ? StepDescription.describeCampfire(node) : StepDescription.describe(recipe));
        var materials = recipe == null ? List.<Component>of() : StepDescription.materials(recipe);
        if (!materials.isEmpty()) result.append(" · ").append(materials.getFirst());
        return result;
    }
    @Override protected void init() {
        layout = FilterScreenLayout.fit(width, height);
        nativeWidgets.clear();
        graphs = GraphIndex.get(minecraft.level).graphs().values().stream().sorted(Comparator.comparing(GraphDefinition::id)).toList();
        graphLabel = label(layout.choiceLabel(layout.graphSelector()));
        graphSelector = selector(layout.graphSelector());
        List<Component> options = new ArrayList<>();
        options.add(text(graphs.isEmpty() ? "empty" : "choose"));
        graphs.forEach(g -> options.add(graphName(g)));
        graphSelector.forOptions(options); graphSelector.active = !graphs.isEmpty();
        int selectedGraph = 0;
        for (int i = 0; i < graphs.size(); i++) if (graphs.get(i).id().equals(draft.graph())) selectedGraph = i + 1;
        graphSelector.setState(selectedGraph);
        graphSelector.calling(i -> {
            draft.chooseGraph(i == 0 ? "" : graphs.get(i - 1).id()); nodeIndex = 0; listOffset = 0; rebuild();
        });
        choiceText(graphLabel, graph() == null ? text(draft.graph().isEmpty() ? "choose" : "stale") : graphName(graph()), layout.graphSelector());
        graphSelector.titled(text("graph"));
        if (graph() != null) graphSelector.addHint(Component.literal(graph().id()));
        nativeWidget(graphSelector);
        addRenderableOnly(new FilterScreenWidget(graphLabel, layout));
        steps.clear();
        var graph = graph(); nodes = graph == null ? List.of() : new ArrayList<>(graph.nodes().values());
        for (var node : nodes) {
            try { steps.put(node.id(), NativeSteps.template(minecraft.level, graph, node, NativeSteps.stack(graph.defaults().transitionalItem()))); }
            catch (RuntimeException ignored) { /* Invalid display data remains visible and cannot be added. */ }
        }
        nodeIndex = Math.max(0, Math.min(nodeIndex, nodes.size() - 1));
        nodeLabel = label(layout.choiceLabel(layout.nodeSelector()));
        nodeSelector = selector(layout.nodeSelector());
        nodeSelector.forOptions(nodes.isEmpty() ? List.of(text("no_nodes")) : nodes.stream().map(this::nodeName).toList());
        nodeSelector.active = !nodes.isEmpty(); nodeSelector.setState(nodeIndex); nodeSelector.titled(text("nodes"));
        nodeSelector.calling(i -> { nodeIndex = i; updateNodeChoice(); });
        nativeWidget(nodeSelector);
        addRenderableOnly(new FilterScreenWidget(nodeLabel, layout));
        add = button(layout.addButton(), AllIcons.I_ADD, "add", () -> {
            if (!nodes.isEmpty()) draft.add(nodes.get(nodeIndex).id(), false); rebuild();
        });
        addInverted = button(layout.addInvertedButton(), AllIcons.I_ADD_INVERTED_ATTRIBUTE, "add_inverted", () -> {
            if (!nodes.isEmpty()) draft.add(nodes.get(nodeIndex).id(), true); rebuild();
        });
        updateNodeChoice();
        listOffset = Math.max(0, Math.min(listOffset, draft.targets().size() - ROWS));
        for (int row = 0; row < ROWS && listOffset + row < draft.targets().size(); row++) {
            String id = draft.targets().get(listOffset + row);
            button(layout.removeButton(row), Sprite.REMOVE, "remove", () -> { draft.remove(id); rebuild(); });
        }
        button(layout.clearButton(), Sprite.CLEAR, "clear", () -> { draft.clear(); listOffset = 0; rebuild(); });
        for (var mode : FilterConfiguration.Mode.values()) {
            ScreenElement icon = switch (mode) {
                case ANY -> AllIcons.I_WHITELIST_OR;
                case ALL -> AllIcons.I_WHITELIST_AND;
                case NONE -> AllIcons.I_WHITELIST_NOT;
            };
            var modeButton = button(layout.modeButton(mode.ordinal()), icon, "mode." + mode.id(), () -> {
                draft.setMode(mode); rebuild();
            });
            modeButton.green = draft.mode() == mode;
        }
        confirm = button(layout.confirmButton(), Sprite.CONFIRM, "confirm", () -> {
            if (!sameItem()) { onClose(); return; }
            var live = GraphIndex.get(minecraft.level).graphs().get(draft.graph());
            if (live == null || !draft.canConfirm(live.nodes().keySet())) return;
            PacketDistributor.sendToServer(new ConfigureFilterPayload(hand, draft.configuration().toJson())); onClose();
        });
        confirm.active = graph != null && draft.canConfirm(graph.nodes().keySet());
    }
    private static int x(Bounds b) { return (int)b.left(); }
    private static int y(Bounds b) { return (int)b.top(); }
    private static int w(Bounds b) { return (int)(b.right() - b.left()); }
    private static int h(Bounds b) { return (int)(b.bottom() - b.top()); }
    private Label label(Bounds bounds) {
        return new Label(x(bounds), y(bounds), Component.empty()).colored(filterColor("choice")).withShadow();
    }
    private void choiceText(Label label, Component value, Bounds selector) {
        int width = w(layout.choiceLabel(selector));
        // Create Label renders plainCopy(), dropping appended siblings; flatten only the display label.
        // Its trimFront=false path also trims from the wrong end. Keep full Components in tooltips.
        String display = value.getString();
        if (font.width(value) > width) display = font.substrByWidth(value, Math.max(0, width - font.width("..."))).getString() + "...";
        label.text = Component.literal(display);
    }
    private SelectionScrollInput selector(Bounds bounds) {
        return new SelectionScrollInput(x(bounds), y(bounds), w(bounds), h(bounds)) {
            @Override protected void renderTooltip(GuiGraphics g, int mx, int my, float partial) {}
        };
    }
    private <T extends AbstractSimiWidget> T nativeWidget(T widget) {
        nativeWidgets.add(widget); addRenderableWidget(new FilterScreenWidget(widget, layout)); return widget;
    }
    private IconButton button(Bounds bounds, Sprite icon, String tip, Runnable action) {
        var button = GraphGuiRenderer.iconButton(x(bounds), y(bounds), icon);
        button.setToolTip(text(tip)); button.withCallback(action); return nativeWidget(button);
    }
    private IconButton button(Bounds bounds, ScreenElement icon, String tip, Runnable action) {
        var button = GraphGuiRenderer.iconButton(x(bounds), y(bounds), icon);
        button.setToolTip(text(tip)); button.withCallback(action); return nativeWidget(button);
    }
    private void rebuild() { setFocused(null); setDragging(false); clearWidgets(); init(); }
    private void updateNodeChoice() {
        choiceText(nodeLabel, nodes.isEmpty() ? text("no_nodes") : nodeName(nodes.get(nodeIndex)), layout.nodeSelector());
        if (!nodes.isEmpty()) nodeSelector.addHint(Component.literal(nodes.get(nodeIndex).id()));
        boolean canAdd = !nodes.isEmpty() && steps.containsKey(nodes.get(nodeIndex).id()) && !draft.targets().contains(nodes.get(nodeIndex).id());
        if (add != null) add.active = canAdd;
        if (addInverted != null) addInverted.active = canAdd;
    }
    private boolean sameItem() {
        return minecraft.player != null && minecraft.player.getItemInHand(hand) == editingStack
            && ItemStack.matches(original, editingStack);
    }
    @Override public void tick() {
        if (!sameItem()) { onClose(); return; }
        nativeWidgets.forEach(AbstractSimiWidget::tick);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        boolean handled = super.mouseClicked(mx,my,button);
        // A native callback may rebuild all widgets. Do not keep its removed focus/drag target.
        if (getFocused() != null && !children().contains(getFocused())) { setFocused(null); setDragging(false); }
        return handled;
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        var local = layout.toLocal(mx,my);
        if (layout.selectedList().contains(local.x(),local.y())) {
            listOffset = Math.max(0, Math.min(Math.max(0, draft.targets().size() - ROWS), listOffset - (int)Math.signum(sy)));
            rebuild(); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        var transform = layout.transform();
        g.pose().pushPose();
        g.pose().translate(transform.x(),transform.y(),0);
        g.pose().scale((float)transform.scale(),(float)transform.scale(),1);
        try { renderPanel(g); }
        finally { g.flush(); g.pose().popPose(); }
    }
    private void line(GuiGraphics g, Component text, Bounds bounds, String role) {
        g.drawString(font, font.substrByWidth(text,w(bounds)).getString(),x(bounds),y(bounds),filterColor(role),false);
    }
    private void item(GuiGraphics g, ItemStack stack, Bounds bounds) { g.renderItem(stack,x(bounds),y(bounds)); }
    private void renderPanel(GuiGraphics g) {
        GraphGuiRenderer.filterPanel(g, layout);
        // The full filter atlas already contains both choice slots. The fallback
        // Create texture has no baked slots, so retain its separate backgrounds.
        if (!GraphGuiAssets.current().available().contains(Sprite.FILTER_PANEL)) {
            GraphGuiRenderer.sprite(g, Sprite.FILTER_CHOICE, layout.graphSelector());
            GraphGuiRenderer.sprite(g, Sprite.FILTER_CHOICE, layout.nodeSelector());
        }
        var titleBounds = layout.title();
        g.drawString(font, title, x(titleBounds) + (w(titleBounds) - font.width(title)) / 2, y(titleBounds),filterColor("title"),false);
        line(g,text("graph"),layout.graphTitle(),"label");
        line(g,text("nodes"),layout.nodeTitle(),"label");
        line(g,text("selected." + draft.mode().id(),draft.targets().size()),layout.selectedTitle(),"label");
        var graph = graph();
        if (graph != null) item(g,representative(graph),layout.graphIcon());
        if (!nodes.isEmpty()) {
            var node = nodes.get(nodeIndex);
            if (node.operation().isCampfire() || steps.containsKey(node.id())) item(g,StepDescription.machine(node,steps.get(node.id())),layout.nodeIcon());
        }
        for (int row = 0; row < ROWS && listOffset + row < draft.targets().size(); row++) {
            var id = draft.targets().get(listOffset + row); var node = graph == null ? null : graph.nodes().get(id);
            GraphGuiRenderer.sprite(g, Sprite.FILTER_ICON_SLOT, layout.rowIconFrame(row));
            GraphGuiRenderer.sprite(g, Sprite.FILTER_CHOICE, layout.rowTextFrame(row));
            if (node != null && (node.operation().isCampfire() || steps.containsKey(id)))
                item(g,StepDescription.machine(node,steps.get(id)),layout.rowIcon(row));
            var label = node == null ? text("unavailable_node", id) : nodeName(node);
            if (draft.inverted().contains(id)) label = text("inverted", label);
            line(g,label,layout.rowLabel(row),node == null ? "error" : "choice");
        }
        boolean stale = !draft.graph().isEmpty() && (graph == null || !graph.nodes().keySet().containsAll(draft.targets()));
        var capacity = draft.capacityProblem();
        line(g,text(capacity != null ? "capacity." + capacity.name().toLowerCase(Locale.ROOT) : graphs.isEmpty() ? "empty" : stale ? "stale" : "hint"),
            layout.status(),stale || capacity != null ? "error" : "hint");
        if (draft.targets().size() > ROWS) line(g,Component.literal((listOffset + 1) + "–" + Math.min(listOffset + ROWS,draft.targets().size()) + "/" + draft.targets().size()),layout.rangeLabel(),"label");
        GraphGuiRenderer.filterPreview(g,editingStack,layout.preview());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        var graph = graph();
        var local = layout.toLocal(mx,my);
        if (draft.capacityProblem() != null && (layout.status().contains(local.x(),local.y()) || layout.confirmButton().contains(local.x(),local.y()))) {
            g.renderComponentTooltip(font, List.of(text("capacity." + draft.capacityProblem().name().toLowerCase(Locale.ROOT))), mx,my); return;
        }
        for (var widget : nativeWidgets) if (widget.isMouseOver(local.x(),local.y()) && !widget.getToolTip().isEmpty()) {
            g.renderComponentTooltip(font, widget.getToolTip(), mx, my); return;
        }
        int row = layout.listRowAt(local.x(),local.y());
        if (row >= 0 && layout.rowDescription(row).contains(local.x(),local.y())) {
            int index = listOffset + row;
            if (index < draft.targets().size()) {
                var id = draft.targets().get(index); var node = graph == null ? null : graph.nodes().get(id);
                Component description = node == null ? text("unavailable_node", id) : nodeName(node);
                if (draft.inverted().contains(id)) description = text("inverted", description);
                g.renderComponentTooltip(font, List.of(description, Component.literal(id)), mx, my);
            }
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
