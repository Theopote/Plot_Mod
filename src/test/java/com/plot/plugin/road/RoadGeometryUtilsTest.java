package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.ui.tools.impl.drawing.helper.BezierUtils;
import net.minecraft.util.math.BlockPos;
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

    @Test
    void pointInPolygonDetectsInsideAndOutside() {
        List<Vec2d> square = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        );

        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(2, 2), square));
        assertFalse(RoadGeometryUtils.pointInPolygon(new Vec2d(5, 2), square));
    }

    @Test
    void pointAlongPolylineFromRespectsMaxDistance() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 5),
            new Vec2d(0, 12)
        );

        Vec2d point = RoadGeometryUtils.pointAlongPolylineFrom(path.getFirst(), path, 7.0);

        assertEquals(0, point.x, 1e-6);
        assertEquals(7, point.y, 1e-6);
    }

    @Test
    void canvasToBlockXZFallsBackToOneToOneMapping() {
        BlockPos pos = RoadGeometryUtils.canvasToBlockXZ(new Vec2d(12.7, -3.2), null);

        assertEquals(12, pos.getX());
        assertEquals(-3, pos.getZ());
    }
}
