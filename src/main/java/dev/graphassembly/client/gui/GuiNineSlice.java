package dev.graphassembly.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Fixed Java UV contract: PNG artists need neither JSON nor per-image metadata. */
public final class GuiNineSlice {
    private GuiNineSlice() {}
    public record Slice(int x, int y, int width, int height,
                        int u, int v, int sourceWidth, int sourceHeight) {}

    public static List<Slice> slices(GraphGuiAssets.Sprite sprite, int width, int height) {
        if (width <= 0 || height <= 0) return List.of();
        int bx = sprite.width() == 3 ? 0 : sprite.border();
        int by = sprite.border();
        if (bx == 0 && by == 0 || width == sprite.width() && height == sprite.height())
            return List.of(new Slice(0, 0, width, height, sprite.x(), sprite.y(), sprite.width(), sprite.height()));
        int left = Math.min(bx, width / 2 + width % 2), right = Math.min(bx, width / 2);
        int top = Math.min(by, height / 2 + height % 2), bottom = Math.min(by, height / 2);
        int[] sw = {bx, sprite.width() - 2 * bx, bx}, sh = {by, sprite.height() - 2 * by, by};
        int[] dw = {left, width - left - right, right}, dh = {top, height - top - bottom, bottom};
        var slices = new ArrayList<Slice>(9);
        int y = 0, v = sprite.y();
        for (int row = 0; row < 3; row++) {
            int x = 0, u = sprite.x();
            for (int col = 0; col < 3; col++) {
                if (dw[col] > 0 && dh[row] > 0)
                    slices.add(new Slice(x, y, dw[col], dh[row], u, v, sw[col], sh[row]));
                x += dw[col]; u += sw[col];
            }
            y += dh[row]; v += sh[row];
        }
        return List.copyOf(slices);
    }
}
