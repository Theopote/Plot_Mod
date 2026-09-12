package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathSelectionAnalysisTest {

    @Test
    void classifiesOpenBezierAsAdoptable() {
        BezierCurveShape curve = sampleBezier();
        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(List.of(curve));

        assertEquals(1, analysis.adoptable().size());
        assertTrue(analysis.hasCanvasSelection());
        assertTrue(analysis.canAdopt());
    }

    @Test
    void classifiesMixedSelection() {
        BezierCurveShape curve = sampleBezier();
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        PolylineShape closedPolyline = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(5, 0), new Vec2d(5, 5), new Vec2d(0, 5)),
            true);

        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(
            List.of(curve, line, closedPolyline));

        assertEquals(2, analysis.adoptable().size());
        assertEquals(1, analysis.rejectedClosed().size());
        assertTrue(analysis.canAdopt());
        assertEquals(1, analysis.skippedCount());
    }

    @Test
    void analyzeSelectionDelegatesToAnalysis() {
        BezierCurveShape curve = sampleBezier();
        PowerLinePathSelectionAnalysis analysis = PowerLinePathUtils.analyzeSelection(List.of(curve));

        assertTrue(analysis.canAdopt());
        assertEquals(1, analysis.adoptable().size());
    }

    private static BezierCurveShape sampleBezier() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});
        return new BezierCurveShape(anchors, controls, false);
    }
}
