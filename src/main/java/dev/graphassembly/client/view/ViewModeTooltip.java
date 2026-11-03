package dev.graphassembly.client.view;

/** A single toggle shows the current view, its next action, and the current view's controls. */
public record ViewModeTooltip(boolean sphere) {
    public static ViewModeTooltip button(boolean currentSphere) {
        return new ViewModeTooltip(currentSphere);
    }

    public String currentModeKey() { return modeKey(sphere); }
    public String nextModeKey() { return modeKey(!sphere); }
    private static String modeKey(boolean sphere) {
        return "graph_assembly.view." + (sphere ? "sphere" : "plane");
    }
    public String controlsKey() { return controlsKey(sphere); }
    public static String controlsKey(boolean sphere) {
        return "graph_assembly.view.controls." + (sphere ? "sphere" : "plane");
    }
}
