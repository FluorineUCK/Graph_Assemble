package dev.graphassembly.client.view;

/** Layout sizing in GUI pixels, independent of Minecraft's physical GUI scale. */
public record RecipeViewport(double scale, int width, int canvasX, int canvasHeight) {
    // JEI 19's default tabs/header/borders + recipe spacing; footer keeps native 16px ingredient slots.
    private static final int CHROME_HEIGHT = 110, FOOTER_HEIGHT = 22;
    public static RecipeViewport fit(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(1, screenWidth - 40);
        double scale = Math.min(1, Math.min(availableWidth / 320.0, Math.max(1, screenHeight - CHROME_HEIGHT - FOOTER_HEIGHT) / 228.0));
        int canvasHeight = (int)Math.floor(228 * scale);
        scale = Math.max(1, canvasHeight) / 228.0;
        int canvasWidth = (int)Math.ceil(320 * scale);
        int width = Math.min(availableWidth, Math.max(250, canvasWidth));
        return new RecipeViewport(scale, width, (width - canvasWidth) / 2, Math.max(1, canvasHeight));
    }
    public int height() { return canvasHeight + 22; }
    public int slotY() { return canvasHeight + 4; }
    public int slotX(int originalX) { return (int)Math.round(originalX * width / 320.0); }
    public double local(double coordinate) { return coordinate / scale; }
}
