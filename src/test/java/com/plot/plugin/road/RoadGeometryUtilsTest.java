package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.ui.tools.impl.drawing.helper.BezierUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGeometryUtilsTest {

    @Test
    void sampleBezierCurveReturnsCurvePointsNotControlHandles() {
        Vec2d anchorStart = new Vec2d(0, 0);
        Vec2d anchorEnd = new Vec2d(10, 0);
        Vec2d control1 = new Vec2d(0, 20);
        Vec2d control2 = new Vec2d(10, 20);

        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{control1, control2});

        BezierCurveShape curve = new BezierCurveShape(
            List.of(anchorStart, anchorEnd),
            controls,
            false
        );

        List<Vec2d> sampled = RoadGeometryUtils.sampleBezierCurve(curve);
        List<Vec2d> controlPoints = curve.getControlPoints();

        assertFalse(sampled.isEmpty());
        assertNotEquals(controlPoints.size(), sampled.size(),
            "curve sampling should produce a denser polyline than the control polygon");
        assertNotEquals(controlPoints, sampled,
            "sampleBezierCurve must not return the control-handle sequence");

        Vec2d expectedMidpoint = BezierUtils.evaluateCubicBezier(
            anchorStart, control1, control2, anchorEnd, 0.5);
        assertTrue(sampled.stream().anyMatch(point -> point.distance(expectedMidpoint) < 1.0),
            "sampled points should follow the actual Bezier curve, not linear control handles");

        double maxSampledY = sampled.stream().mapToDouble(point -> point.y).max().orElse(0);
        assertTrue(maxSampledY > 10.0,
            "curved road centerline should bulge above the straight chord between anchors");
    }

    @Test
    void extractShapePointsUsesBezierCurveSampling() {
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 15), new Vec2d(10, 15)});

        BezierCurveShape curve = new BezierCurveShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            controls,
            false
        );

        List<Vec2d> extracted = RoadGeometryUtils.extractShapePoints(curve);
        List<Vec2d> sampled = RoadGeometryUtils.sampleBezierCurve(curve);

        assertEquals(sampled, extracted);
        assertNotEquals(curve.getControlPoints(), extracted);
    }

    @Test
    void extractShapePointsFallsBackToPolylinePoints() {
        PolylineShape polyline = new PolylineShape(List.of(
            new Vec2d(0, 0),
            new Vec2d(5, 5),
            new Vec2d(10, 0)
        ), false);

        List<Vec2d> extracted = RoadGeometryUtils.extractShapePoints(polyline);

        assertEquals(3, extracted.size());
        assertEquals(0, extracted.getFirst().x, 1e-6);
        assertEquals(10, extracted.getLast().x, 1e-6);
    }

    @Test
    void sampleAlongPathRespectsSkipNearEndsDistance() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(100, 0)
        );

        List<Vec2d> samples = RoadGeometryUtils.sampleAlongPath(path, 10, 5);

        assertEquals(10, samples.size());
        assertEquals(5, samples.getFirst().x, 1e-6);
        assertEquals(95, samples.getLast().x, 1e-6);
    }
}
