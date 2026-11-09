package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.client.view.GraphProjection.Projected;
import static org.junit.jupiter.api.Assertions.*;

class GraphPixelMeshTest {
    @Test void orderedLinePixelsMatchLegacyInclusiveSampling() {
        var expected = legacyLine(0.2, 20.4, 4.6, 22.1);
        var mesh = GraphPixelMesh.line(0.2, 20.4, 4.6, 22.1);
        var actual = new ArrayList<String>();
        mesh.forEach((x, y) -> actual.add(x + "," + y));
        assertEquals(expected, actual);
    }

    @Test void linesRetainClippingDuplicatesAndUnusualEndpoints() {
        double[][] cases = {{-10, 20, 320, 189}, {320, 189, 320, 189}, {3.4, 22.6, 3.4, 22.6},
            {-1.6, 20.2, 3.2, 20.2}, {-100, -100, -5, -5}, {Double.NaN, 20, 50, 50},
            {Double.POSITIVE_INFINITY, 20, 50, 50}, {0, 189, 320, 189}};
        for (var c : cases) {
            var actual = new ArrayList<String>();
            GraphPixelMesh.line(c[0], c[1], c[2], c[3]).forEach((x,y) -> actual.add(x + "," + y));
            assertEquals(legacyLine(c[0], c[1], c[2], c[3]), actual);
        }
    }

    @Test void dotsKeepLegacyMaskOrderAndTruncatedCenters() {
        for (double[] center : new double[][]{{10.9, 21.8}, {-1.9, -2.8}}) {
            var actual = new ArrayList<String>();
            GraphPixelMesh.dot(center[0], center[1]).forEach((x,y) -> actual.add(x + "," + y));
            var expected = new ArrayList<String>();
            for (int y=-2; y<=2; y++) for (int x=-2; x<=2; x++) if (x*x+y*y<=6)
                expected.add(((int)center[0]+x) + "," + ((int)center[1]+y));
            assertEquals(expected, actual);
        }
    }

    @Test void fullPathsKeepSegmentsThenBothArrowStrokes() {
        var a = new Projected(60,100,1); var b = new Projected(260,100,1);
        var edges = List.of(new Edge("a","a",1), new Edge("a","b",1), new Edge("b","a",1));
        var paths = GraphEdges.layout(edges, Map.of("a",a,"b",b), false);
        for (var path : paths) {
            var expected = new ArrayList<String>();
            for (int i=1;i<path.points().size();i++) expected.addAll(legacyLine(path.points().get(i-1).x(),path.points().get(i-1).y(),
                path.points().get(i).x(),path.points().get(i).y()));
            var arrow = GraphEdges.arrow(path);
            expected.addAll(legacyLine(arrow.tip().x(),arrow.tip().y(),arrow.left().x(),arrow.left().y()));
            expected.addAll(legacyLine(arrow.tip().x(),arrow.tip().y(),arrow.right().x(),arrow.right().y()));
            var actual = new ArrayList<String>();
            GraphPixelMesh.path(path).forEach((x,y) -> actual.add(x + "," + y));
            assertEquals(expected, actual);
        }
    }

    private static List<String> legacyLine(double x1, double y1, double x2, double y2) {
        var clipped = GraphProjection.clipSegment(x1, y1, x2, y2, 0, 20, 320, 189);
        if (clipped == null) return List.of();
        x1 = clipped[0]; y1 = clipped[1]; x2 = clipped[2]; y2 = clipped[3];
        int steps = (int)Math.ceil(Math.max(Math.abs(x2-x1), Math.abs(y2-y1)));
        var result = new ArrayList<String>();
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double)i / steps;
            result.add(Math.round(x1 + (x2-x1)*t) + "," + Math.round(y1 + (y2-y1)*t));
        }
        return result;
    }
}
