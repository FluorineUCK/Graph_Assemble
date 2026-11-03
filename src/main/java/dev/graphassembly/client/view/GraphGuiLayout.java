package dev.graphassembly.client.view;

import java.util.List;
import dev.graphassembly.client.view.NodePresentation.Bounds;

/** Visible navigation rectangles, also used for hit testing after RecipeViewport's inverse transform. */
public final class GraphGuiLayout {
    private GraphGuiLayout() {}
    /** Inner PNG area below the controls and above the details; never include the 8px frame. */
    public static final Bounds CANVAS = new Bounds(8, 32, 312, 189);
    public static final Bounds VIEW_MODE = new Bounds(12, 10, 30, 28);
    public static final Bounds CONTROLS = new Bounds(38, 10, 282, 28);
    public static final Bounds RESET_VIEW = new Bounds(290, 10, 308, 28);
    public static final Bounds DETAILS = new Bounds(12, 194, 308, 216);
    public static double canvasCenterX() { return (CANVAS.left() + CANVAS.right()) / 2; }
    public static double canvasCenterY() { return (CANVAS.top() + CANVAS.bottom()) / 2; }
    public static List<Bounds> navigation() { return List.of(VIEW_MODE, RESET_VIEW); }
    public static int centeredTextY(Bounds area, int lineHeight) {
        return (int)area.top() + Math.max(0, ((int)(area.bottom() - area.top()) - lineHeight) / 2);
    }
    public static int detailRows(int lineHeight) {
        return Math.min(2, ((int)(DETAILS.bottom() - DETAILS.top()) + 2) / (lineHeight + 2));
    }
    public static int detailY(int row, int lineHeight) { return (int)DETAILS.top() + row * (lineHeight + 2); }
    public record Scissor(int left, int top, int right, int bottom) {}
    /** GuiGraphics scissors use screen GUI coordinates, not the current pose. */
    public static Scissor canvasScissor(double left, double top, double right, double bottom) {
        // A responsive scale can leave fractional edges. Round inward so an enlarged
        // native model cannot paint the frame or the details strip by one GUI pixel.
        int x = (int)Math.ceil(left), y = (int)Math.ceil(top);
        return new Scissor(x, y, Math.max(x, (int)Math.floor(right)), Math.max(y, (int)Math.floor(bottom)));
    }
}
