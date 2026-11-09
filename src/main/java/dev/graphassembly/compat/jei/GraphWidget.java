package dev.graphassembly.compat.jei;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.route.BranchMode;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.graphassembly.client.StepDescription;
import dev.graphassembly.client.gui.GraphGuiRenderer;
import dev.graphassembly.client.gui.GraphGuiPixelBatch;
import dev.graphassembly.client.gui.GraphGuiAssets.Sprite;
import static dev.graphassembly.client.gui.GraphGuiRenderer.color;
import dev.graphassembly.client.view.*;
import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.*;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.integration.GraphPreviewRecipes;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.*;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Vector3f;
import java.util.*;

final class GraphWidget implements IRecipeWidget, IJeiInputHandler {
    private static final Bounds CANVAS = GraphGuiLayout.CANVAS;
    private final GraphDefinition<OperationSpec> graph;
    private final SettlementGroups settlement;
    private final Map<String, Component> finishSummaries = new HashMap<>();
    private final Map<String, SettlementFinishDetails> finishDetails = new HashMap<>();
    private final GraphEdges.Topology edgeTopology;
    private final IGuiHelper gui;
    private final IFocusGroup focuses;
    private final Map<String, Projected> projected = new LinkedHashMap<>();
    private final PreviewLifecycle<String, NativeStepPreview> previews = new PreviewLifecycle<>();
    private final Map<Boolean, Map<String, GraphProjection.Point>> layouts = new HashMap<>();
    private boolean geometryDirty = true;
    private Object lastRuntime;
    private final Map<String, RecipeHolder<?>> recipes = new HashMap<>();
    private final Set<String> attempted = new HashSet<>(), diagnosed = new HashSet<>();
    private final Map<String, Boolean> expanded = new HashMap<>();
    private List<Surface> surfaces = List.of();
    private List<GraphEdges.Path> edgePaths = List.of();
    private GraphEdges.Prepared preparedEdges = GraphEdges.prepare(List.of());
    private final GraphRasterPlan raster = new GraphRasterPlan(CANVAS);
    private final GraphViewState view;
    private final com.simibubi.create.foundation.gui.widget.IconButton viewModeButton;
    private final com.simibubi.create.foundation.gui.widget.IconButton resetViewButton;
    private final Gesture gesture = new Gesture();
    private boolean draggingCanvas;
    private boolean draggingNative;
    private Surface pressedPreview;
    private double pressX, pressY;
    GraphWidget(GraphDefinition<OperationSpec> graph, IGuiHelper gui, IFocusGroup focuses) {
        this.graph = graph; this.gui = gui; this.focuses = focuses;
        this.settlement = SettlementGroups.from(graph);
        for (var finish : settlement.finishes()) {
            var summary = SettlementFinishDetails.from(finish);
            finishDetails.put(finish.id(), summary);
            finishSummaries.put(finish.id(), summary.ranges().isEmpty()
                ? SettlementInfo.text("footer.conditions", summary.rules())
                : SettlementInfo.text(summary.moreConditions() ? "footer.ranges_more" : "footer.ranges", summary.ranges()));
        }
        this.view = GraphJeiPlugin.viewState(graph);
        // One native 18px button. Its glyph always describes the current view, not the next action.
        this.viewModeButton = GraphGuiRenderer.iconButton((int)GraphGuiLayout.VIEW_MODE.left(),
            (int)GraphGuiLayout.VIEW_MODE.top(), (g, x, y) ->
                GraphGuiRenderer.sprite(g, view.sphere ? Sprite.SPHERE : Sprite.PLANE, x, y, 16, 16));
        this.resetViewButton = GraphGuiRenderer.iconButton((int)GraphGuiLayout.RESET_VIEW.left(),
            (int)GraphGuiLayout.RESET_VIEW.top(), com.simibubi.create.foundation.gui.AllIcons.I_REFRESH);
        this.edgeTopology = GraphEdges.Topology.of(graph.topology().edges(), graph.layout());
        GraphJeiPlugin.track(this);
    }
    GraphDefinition<OperationSpec> definition() { return graph; }
    GraphViewState viewState() { return view; }
    void clearPreviews() { invalidateFrames(); previews.clear(); recipes.clear(); attempted.clear(); diagnosed.clear(); expanded.clear(); surfaces = List.of(); raster.clear(); lastRuntime = null; pressedPreview = null; draggingCanvas = draggingNative = false; gesture.cancel(); PerformanceMetrics.meshRetainedBytes(0); geometryDirty = true; }
    private void invalidateFrames() { for (var surface : surfaces) { var preview = previews.get(surface.id()); if (preview != null) preview.invalidateFrame(); } }
    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, 320, 228); }
    private final net.minecraft.world.item.ItemStack brewingIcon = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREWING_STAND);
    private void diagnose(String id, Throwable error) {
        if (diagnosed.add(id)) LogUtils.getLogger().warn("Graph preview unavailable at {} / {}", graph.id(), id, error);
    }
    private RecipeHolder<?> recipe(NodeDefinition<OperationSpec> node) {
        return recipes.computeIfAbsent(node.id(), id -> GraphPreviewRecipes.resolve(Minecraft.getInstance().level, graph, node));
    }
    private NativeStepPreview preview(NodeDefinition<OperationSpec> node, int index, boolean eligible) {
        if (GraphJeiPlugin.runtime() == null || Minecraft.getInstance().level == null || !Minecraft.getInstance().isRunning()) return null;
        return previews.prepare(node.id(), eligible, () -> {
            if (!attempted.add(node.id())) return null;
            try {
                var runtime = GraphJeiPlugin.runtime();
                var nativeRecipe = recipe(node);
                // A brewing descriptor is a structural node, not a JEI brewing recipe.
                if (nativeRecipe.value() instanceof dev.graphassembly.integration.vanilla.brewing.BrewingRecipe) return null;
                PerformanceMetrics.previewAttempt();
                var preview = NativeStepPreview.create(runtime, gui, nativeRecipe, index, focuses, node.operation().completeRecipe());
                if (preview.isPresent()) { PerformanceMetrics.previewSuccess(); return preview.get(); }
                else diagnose(node.id(), new IllegalStateException("JEI did not create the native layout"));
            } catch (RuntimeException | LinkageError e) { diagnose(node.id(), e); }
            return null;
        });
    }
    private void project() {
        var currentRuntime = GraphJeiPlugin.runtime();
        if (lastRuntime != currentRuntime) { clearPreviews(); lastRuntime = currentRuntime; GraphJeiPlugin.track(this); }
        if (!geometryDirty) return;
        invalidateFrames();
        boolean measuring = PerformanceMetrics.enabled();
        long geometryStart = measuring ? System.nanoTime() : 0;
        long geometryCpuStart = measuring ? PerformanceMetrics.cpuTime() : 0;
        PerformanceMetrics.geometryRebuild();
        geometryDirty = false;
        projected.clear();
        layouts.computeIfAbsent(view.sphere, mode -> GraphProjection.layout(graph, mode)).forEach((id, point) -> projected.put(id, view.sphere
            ? GraphProjection.project(point, view.yaw, view.pitch, 65 * view.zoom, GraphGuiLayout.canvasCenterX(), GraphGuiLayout.canvasCenterY())
            : new Projected(GraphGuiLayout.canvasCenterX() + view.panX + point.x() * view.zoom,
                GraphGuiLayout.canvasCenterY() + view.panY + point.y() * view.zoom, 1)));
        List<Surface> next = new ArrayList<>(); int index = 0;
        for (var node : graph.nodes().values()) {
            var p = projected.get(node.id());
            double scale = NodePresentation.effectiveScale(view.sphere, view.zoom, p.depth());
            boolean front = !view.sphere || p.depth() > 0;
            // Admission only. Full native layouts can be much larger than sequence thumbnails;
            // their actual dimensions below still own drawing and mouse hit testing.
            var candidate = new Bounds(p.x() - 256 * scale, p.y() - 256 * scale, p.x() + 256 * scale, p.y() + 256 * scale);
            boolean visible = CANVAS.intersects(candidate);
            boolean brewing = dev.graphassembly.integration.vanilla.brewing.BrewingDefinition.SERIALIZER.equals(node.operation().recipe().get("type").getAsString());
            // Structural brewing dots still need their descriptor at the initial, collapsed scale.
            var preview = preview(node, index++, brewing ? front && visible : PreviewLifecycle.shouldPrepare(scale, front, visible));
            try {
                double w = preview == null ? 49 : preview.layout().getRect().getWidth();
                double h = preview == null ? 84 : preview.layout().getRect().getHeight();
                var t = new Transform(p.x() - w * scale / 2, p.y() - h * scale / 2, scale);
                var surface = NodePresentation.expandedPreview(node.id(), p.depth(), front, t, w, h, node.operation().isCampfire());
                boolean large = preview != null && NodePresentation.expanded(expanded.getOrDefault(node.id(), false), scale, front, CANVAS.intersects(surface.bounds()));
                expanded.put(node.id(), large);
                next.add(large ? surface
                    : new Surface(node.id(), p.depth(), front, false, new Transform(p.x() - 5, p.y() - 5, 1), 10, 10));
            } catch (IllegalArgumentException e) { expanded.put(node.id(), false); diagnose(node.id(), e); }
        }
        next.sort(Comparator.comparingDouble(Surface::depth).thenComparing(Surface::id));
        surfaces = List.copyOf(next);
        preparedEdges = GraphEdges.layoutRouted(edgeTopology, projected, view.sphere, surfaces, CANVAS,
            new GraphEdges.PlaneTransform(GraphGuiLayout.canvasCenterX() + view.panX,
                GraphGuiLayout.canvasCenterY() + view.panY, view.zoom));
        edgePaths = preparedEdges.paths();
        if (measuring) PerformanceMetrics.geometryTime(System.nanoTime()-geometryStart, PerformanceMetrics.cpuTime()-geometryCpuStart);
    }
    @Override public void tick() {
        project();
        Set<String> active = new HashSet<>();
        for (var surface : surfaces)
            if (surface.expanded() && surface.front() && CANVAS.intersects(surface.bounds())) active.add(surface.id());
        var evicted = previews.tick(active, p -> { p.invalidateFrame(); p.layout().tick(); });
        evicted.forEach(id -> { attempted.remove(id); expanded.remove(id); recipes.remove(id); });
    }
    @Override public void drawWidget(GuiGraphics g, double mx, double my) {
        var font = Minecraft.getInstance().font;
        GraphGuiRenderer.sprite(g, Sprite.GRAPH_PANEL, 0, 0, 320, 228);
        project();
        viewModeButton.doRender(g, (int)mx, (int)my, 0);
        resetViewButton.doRender(g, (int)mx, (int)my, 0);
        var controls = GraphGuiLayout.CONTROLS;
        g.drawString(font, font.substrByWidth(Component.translatable(ViewModeTooltip.controlsKey(view.sphere)), (int)(controls.right() - controls.left())).getString(),
            (int)controls.left(), GraphGuiLayout.centeredTextY(controls, font.lineHeight), color("text.secondary"), false);
        var topLeft = g.pose().last().pose().transformPosition(new Vector3f((float)CANVAS.left(), (float)CANVAS.top(), 0));
        var bottomRight = g.pose().last().pose().transformPosition(new Vector3f((float)CANVAS.right(), (float)CANVAS.bottom(), 0));
        var clip = GraphGuiLayout.canvasScissor(topLeft.x(), topLeft.y(), bottomRight.x(), bottomRight.y());
        boolean measuring = PerformanceMetrics.enabled();
        long canvasStart = measuring ? System.nanoTime() : 0;
        long canvasCpuStart = measuring ? PerformanceMetrics.cpuTime() : 0;
        var batch = new GraphGuiPixelBatch(g);
        boolean cacheHit = raster.update(edgePaths, surfaces, projected);
        PerformanceMetrics.meshCache(cacheHit);
        if (!cacheHit) PerformanceMetrics.meshRetainedBytes(raster.retainedBytes());
        g.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        try {
            // Plane background and nodes share exactly the same origin, pan and zoom.
            // Sphere rotation has no planar translation; retain a quiet, zoomable backdrop there.
            dev.graphassembly.client.gui.GraphCanvasBackground.draw(g,
                GraphGuiLayout.canvasCenterX() + (view.sphere ? 0 : view.panX),
                GraphGuiLayout.canvasCenterY() + (view.sphere ? 0 : view.panY), view.zoom);
            Surface hovered = hit(mx, my);
            raster.dispatch(new GraphRasterPlan.Sink() {
                @Override public void edge(GraphRasterPlan.EdgePixels pixels) {
                    var path = pixels.path(); var edge = path.edge();
                    batch.submit(pixels.mesh(), color(!path.hittable() ? "edge.back" : edge.equals(view.selectedEdge) ? "edge.selected" : "edge.normal"));
                }
                @Override public void dot(GraphRasterPlan.DotPixels pixels) {
                    var surface = pixels.surface(); var node = graph.nodes().get(surface.id());
                    batch.submit(pixels.mesh(), color(!surface.front() ? "node.back" : (node.id().equals(view.selected) || node.id().equals(view.selectedFinish)) ? "node.selected"
                        : node.roles().finish() ? "node.finish" : node.roles().start() ? "node.start" : node.effects().recordCheckpoint() ? "node.checkpoint" : "node.normal"));
                    var holder = recipes.get(node.id());
                    if (surface.front() && holder != null
                            && holder.value() instanceof dev.graphassembly.integration.vanilla.brewing.BrewingRecipe) {
                        batch.flush();
                        var pose = g.pose();
                        var bounds = surface.bounds();
                        pose.pushPose();
                        try {
                            pose.translate(bounds.left(), bounds.top(), 0);
                            // Fit the vanilla item into the existing structural node, without changing hit geometry.
                            pose.scale((float)((bounds.right() - bounds.left()) / 16),
                                (float)((bounds.bottom() - bounds.top()) / 16), 1);
                            g.renderItem(brewingIcon, 0, 0);
                            g.flush();
                        } finally { pose.popPose(); }
                    }
                }
                @Override public void flush() { batch.flush(); }
                @Override public void beginPreview(Surface surface) {
                    // Each front-facing panel paints over earlier panels, retaining its own model depth.
                    RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                    g.pose().pushPose();
                }
                @Override public boolean drawPreview(Surface surface) {
                    var t = surface.transform();
                    // Finish scope is marked by the fixed PNG check below, not by stretching
                    // the filter's opaque row strip across a zoomed native machine preview.
                    var preview = previews.get(surface.id());
                    try {
                        g.pose().translate(t.x(), t.y(), 0);
                        g.pose().scale((float)t.scale(), (float)t.scale(), 1);
                        var nativeMouse = NodePresentation.previewMouse(surface, hovered, mx, my);
                        preview.draw(g, nativeMouse.x(), nativeMouse.y());
                        var node = graph.nodes().get(surface.id());
                        if (node.operation().isCampfire())
                            g.renderItem(StepDescription.campfireMachine(node), surface.machineBadgeX(), 0);
                        return true;
                    } catch (RuntimeException | LinkageError e) {
                        previewFailure(surface, e);
                        return false;
                    }
                }
                @Override public void previewFailure(Surface surface, Throwable failure) {
                    previews.remove(surface.id()); expanded.put(surface.id(), false); raster.clear();
                    PerformanceMetrics.meshRetainedBytes(0); geometryDirty = true; diagnose(surface.id(), failure);
                }
                @Override public void endPreview(Surface surface) {
                    g.pose().popPose();
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                }
            });
            // Scope marking is independent from the ordinary node/edge selection color.
            if (view.selectedFinish != null) {
                var endpoint = projected.get(view.selectedFinish);
                if (endpoint != null && (!view.sphere || endpoint.depth() > 0))
                    GraphGuiRenderer.sprite(g, Sprite.CONFIRM, (int)endpoint.x() + 6, (int)endpoint.y() - 8, 16, 16);
            }
        } finally {
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            g.disableScissor();
            RenderSystem.disableDepthTest();
            if (measuring) PerformanceMetrics.canvas(System.nanoTime()-canvasStart,
                PerformanceMetrics.cpuTime()-canvasCpuStart, 0, batch.submissions(), batch.vertices());
        }
        var details = GraphGuiLayout.DETAILS;
        int rows = GraphGuiLayout.detailRows(font.lineHeight);
        String detailsId = view.detailsNode();
        if (detailsId != null) {
            var node = graph.nodes().get(detailsId);
            if (node.roles().finish()) {
                var heading = SettlementInfo.text("footer.finish_id", node.id()).copy();
                if (node.operation().isCampfire()) heading.append(" · ").append(StepDescription.describeCampfire(node));
                drawDetail(g, heading, 0, "text.primary");
                drawFinishSummary(g, node.id());
            } else {
                drawDetail(g, description(detailsId), 0, "text.primary");
                if (rows > 1) drawDetail(g, Component.translatable("graph_assembly.node.details", markGrant(node),
                yesNo(checkpoint(node)), Component.translatable(node.routing().redirect()!=null ? "graph_assembly.branch.routed"
                    : NextStepPresentation.branchKey(node.routing().branchMode(), graph.topology().outgoing(node.id()).size()))), 1, "text.secondary");
            }
        } else if (view.selectedEdge != null) {
            var lines = font.split(edgeText(view.selectedEdge), (int)(details.right() - details.left()));
            for (int i = 0; i < Math.min(rows, lines.size()); i++)
                g.drawString(font, lines.get(i), (int)details.left(), GraphGuiLayout.detailY(i, font.lineHeight), color("text.body"), false);
        } else {
            drawDetail(g, SettlementInfo.text("footer.overview", settlement.finishes().size()), 0, "text.primary");
            drawDetail(g, SettlementInfo.text("footer.overview_hint"), 1, "text.secondary");
        }
    }
    private void drawFinishSummary(GuiGraphics g, String id) {
        var summary = finishDetails.get(id);
        if (summary == null || summary.ranges().isEmpty()) {
            drawDetail(g, finishSummary(id), 1, "text.secondary"); return;
        }
        var font = Minecraft.getInstance().font;
        String suffix = SettlementInfo.text(summary.moreConditions() ? "footer.more_hint" : "footer.info_short").getString();
        int width = (int)(GraphGuiLayout.DETAILS.right() - GraphGuiLayout.DETAILS.left()) - font.width(suffix);
        String prefix = summary.ranges();
        if (font.width(prefix) > width) prefix = font.plainSubstrByWidth(prefix, Math.max(0, width - font.width("…"))) + "…";
        drawDetail(g, Component.literal(prefix + suffix), 1, "text.secondary");
    }
    private Component finishSummary(String id) {
        return finishSummaries.getOrDefault(id, SettlementInfo.text("footer.conditions", 0));
    }
    private static void drawDetail(GuiGraphics g, Component text, int row, String role) {
        var font = Minecraft.getInstance().font; var area = GraphGuiLayout.DETAILS;
        if (row >= GraphGuiLayout.detailRows(font.lineHeight)) return;
        int width = (int)(area.right() - area.left());
        String full = text.getString();
        String shown = font.width(full) > width ? font.plainSubstrByWidth(full, width - font.width("…")) + "…" : full;
        g.drawString(font, shown, (int)area.left(), GraphGuiLayout.detailY(row, font.lineHeight), color(role), false);
    }
    private Surface hit(double x, double y) {
        project();
        if (!CANVAS.contains(x, y)) return null;
        for (int i = surfaces.size() - 1; i >= 0; i--) {
            var surface = surfaces.get(i);
            if (!surface.front()) continue;
            if (surface.bounds().contains(x, y) || slot(surface, x, y) != null) return surface;
        }
        return null;
    }
    private Hit pick(double x, double y) {
        var surface = hit(x, y);
        return surface == null ? NodePresentation.pickPrepared(surfaces, preparedEdges, CANVAS, x, y) : new Hit(surface, null);
    }
    private RecipeSlotUnderMouse slot(Surface surface, double x, double y) {
        if (surface == null || !surface.expanded() || previews.get(surface.id()) == null) return null;
        var t = surface.transform();
        var slot = previews.get(surface.id()).slotAt(t.localX(x), t.localY(y));
        return slot == null ? null : new RecipeSlotUnderMouse(slot, 0, 0);
    }
    private static boolean checkpoint(NodeDefinition<OperationSpec> node) { return node.roles().start() || node.roles().finish() || node.effects().recordCheckpoint(); }
    private Component description(String id) {
        try {
            var node = graph.nodes().get(id);
            if (node.operation().isCampfire()) return StepDescription.describeCampfire(node);
            var preview = previews.get(id);
            if (preview != null) return preview.title();
            var value = recipe(graph.nodes().get(id)).value();
            if (value instanceof com.simibubi.create.content.processing.sequenced.IAssemblyRecipe assembly)
                return assembly.getDescriptionForAssembly();
            if (value instanceof com.simibubi.create.content.kinetics.millstone.MillingRecipe milling)
                return StepDescription.describe(milling);
            if (value instanceof net.minecraft.world.item.crafting.CraftingRecipe
                || value instanceof dev.graphassembly.integration.vanilla.anvil.AnvilRecipe
                || value instanceof dev.graphassembly.integration.vanilla.brewing.BrewingRecipe)
                return StepDescription.describe(value);
            return Component.literal(graph.nodes().get(id).operation().recipe().get("type").getAsString());
        }
        catch (RuntimeException | LinkageError e) { diagnose(id,e); return Component.translatable("graph_assembly.preview.unavailable"); }
    }
    @SuppressWarnings("removal") // JEI 19 widget tooltip collection is drawn later by the parent in screen space.
    @Override public void getTooltip(ITooltipBuilder tooltip, double mx, double my) {
        if (mx < 0 || mx >= 320 || my < 0 || my >= 228) return;
        if (GraphGuiLayout.RESET_VIEW.contains(mx, my)) {
            tooltip.add(Component.translatable("graph_assembly.view.reset"));
            return;
        }
        if (GraphGuiLayout.VIEW_MODE.contains(mx, my)) {
            var help = ViewModeTooltip.button(view.sphere);
            tooltip.add(Component.translatable("graph_assembly.view.current", Component.translatable(help.currentModeKey()))
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("graph_assembly.view.switch", Component.translatable(help.nextModeKey()))
                .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(help.controlsKey()).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (GraphGuiLayout.CONTROLS.contains(mx, my)) {
            tooltip.add(Component.translatable(ViewModeTooltip.controlsKey(view.sphere)));
            return;
        }
        var picked = pick(mx, my); var surface = picked.node(); var slot = slot(surface, mx, my);
        if (slot != null) {
            slot.slot().getTooltip(tooltip);
            var node = graph.nodes().get(surface.id());
            if (node.operation().isCampfire()) tooltip.add(StepDescription.describeCampfire(node));
            return;
        }
        if (GraphGuiLayout.DETAILS.contains(mx, my) && view.selectedEdge != null) { tooltip.add(edgeText(view.selectedEdge)); return; }
        String id = GraphGuiLayout.DETAILS.contains(mx, my) ? view.detailsNode() : surface == null ? null : surface.id();
        if (id != null) {
            var node = graph.nodes().get(id);
            tooltip.add(description(id));
            if (node.roles().finish()) {
                tooltip.add(SettlementInfo.text("footer.finish_id", node.id()));
                tooltip.add(finishSummary(node.id()));
            }
            if (view.selectedFinish != null && !view.selectedFinish.equals(id))
                tooltip.add(SettlementInfo.text("footer.active_finish", view.selectedFinish));
            if (previews.get(id) == null && recipes.containsKey(id))
                StepDescription.materials(recipes.get(id).value()).forEach(tooltip::add);
            var level = Minecraft.getInstance().level;
            if (level != null && !GraphIndex.get(level).graphs().containsKey(graph.id()))
                tooltip.add(Component.translatable("graph_assembly.preview.only"));
            if (surface != null && surface.expanded() && previews.get(id) != null) {
                var transform = surface.transform();
                previews.get(id).getTooltip(tooltip, transform.localX(mx), transform.localY(my));
            }
            if (diagnosed.contains(id)) tooltip.add(Component.translatable("graph_assembly.preview.unavailable"));
            tooltip.add(Component.translatable("graph_assembly.node.identities", node.id(), yesNo(node.roles().start()), yesNo(node.roles().finish())));
            dev.graphassembly.client.view.SettlementDescription.inputs(node).forEach(line ->
                tooltip.add(Component.translatable("graph_assembly.settlement." + line.key(), line.arguments().toArray())));
            dev.graphassembly.client.view.ProgressActionDescription.of(node).forEach(line ->
                tooltip.add(Component.translatable("graph_assembly.settlement." + line.key(), line.arguments().toArray())));
            tooltip.add(Component.translatable("graph_assembly.node.mark_grant", markGrant(node), node.effects().maxMarkApplications() == null
                ? Component.translatable("graph_assembly.node.unlimited") : node.effects().maxMarkApplications(), yesNo(checkpoint(node))));
            // Finish settlement summary above and the existing Info view remain authoritative.
            if (dev.graphassembly.client.view.NodePresentation.showStaticCarrier(node)) {
                var carrier = node.effects().carrierOverride() == null
                    ? Component.translatable("graph_assembly.node.inherit") : NativeSteps.stack(node.effects().carrierOverride()).getHoverName();
                if (node.roles().finish()) tooltip.add(Component.translatable("graph_assembly.node.finish",
                    Component.translatable("graph_assembly.finish." + node.settlement().finishMode().name().toLowerCase(Locale.ROOT)), carrier));
                else tooltip.add(Component.translatable("graph_assembly.node.carrier", carrier));
            }
        } else if (CANVAS.contains(mx, my)) {
            var edge = picked.edge(); if (edge != null) {
                tooltip.add(edgeText(edge));
                dev.graphassembly.client.view.SettlementDescription.edgeConditions(edge).forEach(line->
                    tooltip.add(Component.translatable("graph_assembly.settlement."+line.key(),line.arguments().toArray())));
            }
        }
    }
    @Override public boolean handleInput(double mx, double my, IJeiUserInput input) {
        boolean mouse = input.getKey().getType() == InputConstants.Type.MOUSE;
        boolean left = mouse && input.getKey().getValue() == 0;
        if (mouse && input.isSimulate()) {
            gesture.begin(mx, my); // capture only, no action in simulate phase
            draggingCanvas = left && CANVAS.contains(mx, my);
            draggingNative = false;
            pressedPreview = null;
            pressX = mx; pressY = my;
        }
        boolean click = !mouse || input.isSimulate() || gesture.release(mx, my);
        if (mouse && !input.isSimulate()) {
            boolean releaseNativeDrag = draggingNative;
            draggingCanvas = draggingNative = false;
            if (!click) {
                // Native scrollbars initialize drag state on press and still need release after a drag.
                // Send release at the captured press location so leaving the control does not leave it pressed.
                if (releaseNativeDrag && pressedPreview != null && previews.get(pressedPreview.id()) != null) {
                    var transform = pressedPreview.transform();
                    previews.get(pressedPreview.id()).layout().getInputHandler().handleInput(
                        transform.localX(pressX), transform.localY(pressY), input);
                }
                pressedPreview = null;
                return true;
            }
            pressedPreview = null;
        }
        if (CANVAS.contains(mx, my)) {
            var surface = hit(mx, my); var slot = slot(surface, mx, my); var runtime = GraphJeiPlugin.runtime();
            if (slot != null && runtime != null) {
                boolean recipe = input.is(runtime.getKeyMappings().getShowRecipe());
                boolean uses = input.is(runtime.getKeyMappings().getShowUses());
                if (recipe || uses) {
                    if (!input.isSimulate()) slot.slot().getDisplayedIngredient().ifPresent(ingredient -> runtime.getRecipesGui().show(
                        runtime.getJeiHelpers().getFocusFactory().createFocus(recipe ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, ingredient)));
                    return true;
                }
            }
            if (surface != null && surface.expanded()) {
                var preview = previews.get(surface.id());
                var transform = surface.transform();
                if (preview != null && left && input.isSimulate()) pressedPreview = surface;
                if (preview != null && preview.layout().getInputHandler().handleInput(
                        transform.localX(mx), transform.localY(my), input)) {
                    preview.invalidateFrame();
                    // JEI returns true for every simulated press inside a layout, including empty model areas.
                    // Only a consumed native drag below can take ownership away from the canvas.
                    if (!input.isSimulate()) draggingCanvas = false;
                    return true;
                }
            }
        }
        if (!left) return false;
        if (input.isSimulate()) return true;
        if (GraphGuiLayout.RESET_VIEW.contains(mx, my)) {
            view.resetCamera(graph);
            draggingCanvas = draggingNative = false;
            pressedPreview = null;
            gesture.cancel();
            geometryDirty = true;
            return true;
        }
        if (GraphGuiLayout.VIEW_MODE.contains(mx, my)) {
            if (view.selectMode(!view.sphere)) {
                ViewPreference.set(view.sphere);
                geometryDirty = true;
            }
            return true;
        }
        if (!CANVAS.contains(mx, my)) return false;
        var picked = pick(mx, my); view.selectNode(graph, picked.node() == null ? null : picked.node().id());
        view.selectedEdge = picked.edge(); return true;
    }
    @Override public boolean handleMouseDragged(double mx, double my, InputConstants.Key key, double dx, double dy) {
        if (key.getValue() != 0 || !CANVAS.contains(mx, my)) return false;
        moveCamera(mx, my);
        return true;
    }
    @Override public void handleMouseMoved(double mx, double my) {
        var surface = hit(mx, my);
        if (surface != null && surface.expanded() && previews.get(surface.id()) != null) {
            var transform = surface.transform();
            previews.get(surface.id()).layout().getInputHandler().handleMouseMoved(transform.localX(mx), transform.localY(my));
        }
        if (!draggingCanvas && !draggingNative) return;
        // Track public motion as well as drag callbacks; JEI/loader can consume the latter.
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), 0) == 0) {
            draggingCanvas = draggingNative = false; gesture.cancel(); return;
        }
        moveCamera(mx, my);
    }
    private void moveCamera(double mx, double my) {
        if ((!draggingCanvas && !draggingNative) || !CANVAS.contains(mx, my)) return;
        var delta = gesture.move(mx, my);
        if (delta[0] == 0 && delta[1] == 0) return;
        if (pressedPreview != null && previews.get(pressedPreview.id()) != null) {
            var transform = pressedPreview.transform();
            if (previews.get(pressedPreview.id()).layout().getInputHandler().handleMouseDragged(
                    transform.localX(mx), transform.localY(my), InputConstants.Type.MOUSE.getOrCreate(0),
                    delta[0] / transform.scale(), delta[1] / transform.scale())) {
                draggingNative = true;
                draggingCanvas = false;
                previews.get(pressedPreview.id()).invalidateFrame();
            }
        }
        if (draggingNative) return;
        geometryDirty = true;
        if (view.sphere) { view.yaw += delta[0] * .015; view.pitch += delta[1] * .015; }
        else { view.panX += delta[0]; view.panY += delta[1]; }
    }
    @Override public boolean handleMouseScrolled(double mx, double my, double sx, double sy) {
        if (!CANVAS.contains(mx, my)) return false;
        var surface = hit(mx, my);
        if (surface != null && surface.expanded() && previews.get(surface.id()) != null) {
            var transform = surface.transform();
            if (previews.get(surface.id()).layout().getInputHandler().handleMouseScrolled(
                    transform.localX(mx), transform.localY(my), sx, sy)) {
                previews.get(surface.id()).invalidateFrame(); return true;
            }
        }
        double nextZoom = Math.max(.25, Math.min(3, view.zoom * Math.pow(1.12, sy)));
        if (nextZoom != view.zoom) { view.zoom = nextZoom; geometryDirty = true; } return true;
    }
    private static Component yesNo(boolean value) {
        return Component.translatable("graph_assembly.text." + (value ? "yes" : "no"));
    }
    private static Component markGrant(NodeDefinition<OperationSpec> node) {
        return node.effects().markAmounts().isEmpty() ? Component.translatable("graph_assembly.text.none")
            : Component.literal(dev.graphassembly.client.view.SettlementDescription.markValues(node.effects().markAmounts()));
    }
    private Component edgeText(Edge edge) {
        var node = graph.nodes().get(edge.from());
        if(node.routing().redirect()!=null) return Component.translatable("graph_assembly.edge.overridden",edge.from(),edge.to());
        String key = NextStepPresentation.edgeKey(node.routing().branchMode(), graph.topology().outgoing(node.id()).size());
        if (node.routing().branchMode() == BranchMode.CHOICE) return Component.translatable(key, edge.from(), edge.to());
        boolean conditional = graph.topology().outgoing(node.id()).stream().anyMatch(candidate->
            !candidate.requirements().equals(dev.graphassembly.core.condition.ProgressRequirements.NONE)
                || graph.nodes().get(candidate.to()).inputs().workpieceInput(0)!=null
                || !graph.nodes().get(candidate.to()).effects().markAmounts().isEmpty());
        if(conditional)return Component.translatable("graph_assembly.edge.random.filtered",edge.from(),edge.to(),Double.toString(edge.weight()));
        double scale = graph.topology().edges().stream().filter(e -> e.from().equals(edge.from())).mapToDouble(Edge::weight).max().orElse(1);
        double sum = graph.topology().edges().stream().filter(e -> e.from().equals(edge.from())).mapToDouble(e -> e.weight() / scale).sum();
        return Component.translatable(key, edge.from(), edge.to(),
            String.format(Locale.ROOT, "%.2f%%", edge.weight() / scale / sum * 100), Double.toString(edge.weight()));
    }
}
