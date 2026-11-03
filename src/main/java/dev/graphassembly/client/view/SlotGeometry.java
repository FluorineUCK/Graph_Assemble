package dev.graphassembly.client.view;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import dev.graphassembly.client.view.NodePresentation.Bounds;

/** Supported UI geometry; non-axis-aligned or degenerate matrices fail closed. */
public final class SlotGeometry {
    private SlotGeometry() {}
    public static Bounds transform(Matrix4fc matrix, Bounds source) {
        if (!matrix.isFinite() || Math.abs(matrix.m01()) > 1e-5 || Math.abs(matrix.m10()) > 1e-5
                || Math.abs(matrix.m03()) > 1e-5 || Math.abs(matrix.m13()) > 1e-5
                || Math.abs(matrix.m02()) > 1e-5 || Math.abs(matrix.m12()) > 1e-5
                || Math.abs(matrix.m33() - 1) > 1e-5 || matrix.m00() <= 0 || matrix.m11() <= 0) return null;
        double left = Double.POSITIVE_INFINITY, top = left, right = Double.NEGATIVE_INFINITY, bottom = right;
        for (int i = 0; i < 4; i++) {
            var point = matrix.transformPosition(new Vector3f((float)((i & 1) == 0 ? source.left() : source.right()),
                (float)((i & 2) == 0 ? source.top() : source.bottom()), 0));
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) return null;
            left = Math.min(left, point.x); top = Math.min(top, point.y);
            right = Math.max(right, point.x); bottom = Math.max(bottom, point.y);
        }
        return new Bounds(left, top, right, bottom);
    }
}
