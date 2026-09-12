package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathSelectionAnalysisTest {

    @Test
    void classifiesOpenBezierAsAdoptable() {
        BezierCurveShape curve = sampleBezier(false);
        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(List.of(curve));

        assertEquals(1, analysis.adoptable().size());
        assertTrue(analysis.canAdopt());
    }

    @Test
    void classifiesClosedShapesAsAdoptable() {
        PolylineShape closedPolyline = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(10, 15)),
            true);
        CircleShape circle = new CircleShape(new Vec2d(10, 10), 8.0);
        BezierCurveShape closedBezier = sampleBezier(true);

        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(
            List.of(closedPolyline, circle, closedBezier));

        assertEquals(3, analysis.adoptable().size());
        assertTrue(analysis.canAdopt());
    }

    @Test
    void classifiesMixedSelection() {
        BezierCurveShape curve = sampleBezier(false);
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        PolylineShape closedPolyline = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(5, 0), new Vec2d(5, 5), new Vec2d(0, 5)),
            true);

        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(
            List.of(curve, line, closedPolyline));

        assertEquals(3, analysis.adoptable().size());
        assertTrue(analysis.canAdopt());
        assertEquals(0, analysis.skippedCount());
    }

    private static BezierCurveShape sampleBezier(boolean closed) {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});
        return new BezierCurveShape(anchors, controls, closed);
    }
}
