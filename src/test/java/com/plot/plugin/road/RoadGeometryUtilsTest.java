package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.SpiralType;
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
        List<Vec2d> rawDense = curve.getCurvePoints();

        assertFalse(sampled.isEmpty());
        assertNotEquals(controlPoints.size(), sampled.size(),
            "curve sampling should produce a denser polyline than the control polygon");
        assertNotEquals(controlPoints, sampled,
            "sampleBezierCurve must not return the control-handle sequence");
        assertTrue(sampled.size() <= rawDense.size(),
            "road adoption should not densify beyond render sampling");
        assertTrue(sampled.size() < rawDense.size() || rawDense.size() <= 4,
            "road adoption should simplify dense render sampling when curve is oversampled");

        Vec2d expectedMidpoint = BezierUtils.evaluateCubicBezier(
            anchorStart, control1, control2, anchorEnd, 0.5);
        assertTrue(sampled.stream().anyMatch(point -> point.distance(expectedMidpoint) < 1.5),
            "sampled points should follow the actual Bezier curve, not linear control handles");

        double maxSampledY = sampled.stream().mapToDouble(point -> point.y).max().orElse(0);
        assertTrue(maxSampledY > 10.0,
            "curved road centerline should bulge above the straight chord between anchors");
    }

    @Test
    void sampleBezierCurveKeepsSingleLogicalPolylineForNearlyStraightCurve() {
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(3, 0.2), new Vec2d(7, -0.2)});
        BezierCurveShape curve = new BezierCurveShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            controls,
            false
        );

        List<Vec2d> sampled = RoadGeometryUtils.sampleBezierCurve(curve);
        // 近乎直线：点数应远少于渲染密采样
        assertTrue(sampled.size() >= 2);
        assertTrue(sampled.size() <= 12,
            "nearly straight spline should not produce dozens of centerline vertices");
    }

    @Test
    void simplifyPolylineRemovesColinearMiddlePoints() {
        List<Vec2d> dense = List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0),
            new Vec2d(2, 0),
            new Vec2d(3, 0),
            new Vec2d(4, 0)
        );
        List<Vec2d> simplified = RoadGeometryUtils.simplifyPolyline(dense, 0.1);
        assertEquals(2, simplified.size());
        assertEquals(0, simplified.getFirst().x, 1e-9);
        assertEquals(4, simplified.getLast().x, 1e-9);
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
    void isAdoptablePathAcceptsCircleEllipseArcRectangleSpiral() {
        assertTrue(RoadGeometryUtils.isAdoptablePath(new CircleShape(new Vec2d(0, 0), 10)));
        assertTrue(RoadGeometryUtils.isAdoptablePath(new EllipseShape(new Vec2d(0, 0), 12, 6, 0)));
        assertTrue(RoadGeometryUtils.isAdoptablePath(
            new ArcShape(new Vec2d(0, 0), 8, 0, Math.PI)));
        assertTrue(RoadGeometryUtils.isAdoptablePath(
            new RectangleShape(new Vec2d(0, 0), 20, 10, 0)));
        assertTrue(RoadGeometryUtils.isAdoptablePath(
            new SpiralShape(new Vec2d(0, 0), 5, 3, 15, SpiralType.LINEAR)));
    }

    @Test
    void extractShapePointsFromCircleIsClosedLoop() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(circle);

        assertTrue(points.size() >= 8);
        assertTrue(
            points.getFirst().distance(points.getLast()) < 0.5,
            "circle centerline should close (first ≈ last)");
        double radiusError = points.stream()
            .mapToDouble(p -> Math.abs(p.distance(new Vec2d(0, 0)) - 10))
            .max()
            .orElse(999);
        assertTrue(radiusError < 0.5, "sampled points should lie near the circle");
    }

    @Test
    void extractShapePointsFromRectangleHasFourCorners() {
        RectangleShape rect = new RectangleShape(new Vec2d(0, 0), 20, 10, 0);
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(rect);

        assertTrue(points.size() >= 4);
        assertTrue(RoadGeometryUtils.isAdoptablePath(rect));
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
    void interpolatePolylineByNormalizedDistanceUsesArcLength() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10)
        );

        Vec2d quarter = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(path, 0.25);
        Vec2d midpoint = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(path, 0.5);
        Vec2d end = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(path, 1.0);

        assertEquals(5, quarter.x, 1e-6);
        assertEquals(0, quarter.y, 1e-6);
        assertEquals(10, midpoint.x, 1e-6);
        assertEquals(0, midpoint.y, 1e-6);
        assertEquals(10, end.x, 1e-6);
        assertEquals(10, end.y, 1e-6);
    }

    @Test
    void leftNormalRotatesTangentCounterClockwise() {
        Vec2d tangent = new Vec2d(1, 0);
        Vec2d normal = RoadGeometryUtils.leftNormal(tangent);

        assertEquals(0, normal.x, 1e-6);
        assertEquals(1, normal.y, 1e-6);
        assertEquals(1.0, normal.length(), 1e-6);
    }

    @Test
    void crossSectionSampleOffsetsCoverRoadWidth() {
        List<Integer> offsets = RoadGeometryUtils.crossSectionSampleOffsets(2.5);

        assertTrue(offsets.contains(0));
        assertTrue(offsets.contains(-2));
        assertTrue(offsets.contains(2));
        assertTrue(offsets.stream().allMatch(offset -> offset >= -3 && offset <= 3));
    }

    @Test
    void canvasToBlockXZFallsBackToOneToOneMapping() {
        BlockPos pos = RoadGeometryUtils.canvasToBlockXZ(new Vec2d(12.7, -3.2), null);

        // 与 RoadGeometryUtils / 体素栅格一致：四舍五入到最近格点
        assertEquals(13, pos.getX());
        assertEquals(-3, pos.getZ());
    }

    @Test
    void groupConnectedPathsMergesSimpleChain() {
        List<LineShape> segments = List.of(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
            new LineShape(new Vec2d(10, 0), new Vec2d(20, 0)),
            new LineShape(new Vec2d(20, 0), new Vec2d(30, 0))
        );

        List<List<Vec2d>> groups = RoadGeometryUtils.groupConnectedPathsForAdoption(
            new ArrayList<>(segments));

        assertEquals(1, groups.size());
        assertEquals(4, groups.getFirst().size());
        assertEquals(0, groups.getFirst().getFirst().x, 1e-6);
        assertEquals(30, groups.getFirst().getLast().x, 1e-6);
    }

    @Test
    void groupConnectedPathsMergesReversedSegments() {
        List<LineShape> segments = List.of(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
            new LineShape(new Vec2d(20, 0), new Vec2d(10, 0))
        );

        List<List<Vec2d>> groups = RoadGeometryUtils.groupConnectedPathsForAdoption(
            new ArrayList<>(segments));

        assertEquals(1, groups.size());
        assertEquals(3, groups.getFirst().size());
        assertEquals(0, groups.getFirst().getFirst().x, 1e-6);
        assertEquals(20, groups.getFirst().getLast().x, 1e-6);
    }

    @Test
    void groupConnectedPathsKeepsDisconnectedSegmentsSeparate() {
        List<LineShape> segments = List.of(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
            new LineShape(new Vec2d(0, 20), new Vec2d(10, 20))
        );

        List<List<Vec2d>> groups = RoadGeometryUtils.groupConnectedPathsForAdoption(
            new ArrayList<>(segments));

        assertEquals(2, groups.size());
    }

    @Test
    void groupConnectedPathsDoesNotMergeFork() {
        List<LineShape> segments = List.of(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
            new LineShape(new Vec2d(10, 0), new Vec2d(20, 0)),
            new LineShape(new Vec2d(10, 0), new Vec2d(10, 10))
        );

        List<List<Vec2d>> groups = RoadGeometryUtils.groupConnectedPathsForAdoption(
            new ArrayList<>(segments));

        assertEquals(3, groups.size());
    }
}
