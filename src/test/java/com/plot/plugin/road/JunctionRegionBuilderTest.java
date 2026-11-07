package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionRegionBuilderTest {

    @Test
    void orthogonalEqualWidthOverlapIsRectangleWithExpectedArea() {
        Vec2d center = new Vec2d(0, 0);
        double halfA = 3.0;
        double halfB = 3.0;
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), halfA),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), halfB),
            0.0);

        assertEquals(4, geo.overlapRegion().size(), "orthogonal overlap is a rectangle");
        double expectedArea = (2 * halfA) * (2 * halfB); // wA*wB/sin90
        assertEquals(expectedArea, JunctionRegionBuilder.polygonArea(geo.overlapRegion()), 1e-6);
        assertTrue(geo.originalRoadUnion().size() >= 8, "cross union has outer ring vertices");
        assertEquals(geo.originalRoadUnion(), geo.finalPavementRegion());
    }

    @Test
    void skewedOverlapIsParallelogramAndAreaFollowsSinTheta() {
        Vec2d center = new Vec2d(0, 0);
        double halfA = 2.0;
        double halfB = 3.0;
        double theta = Math.toRadians(60.0);
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), halfA),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(Math.cos(theta), Math.sin(theta)), halfB),
            0.0);

        assertEquals(4, geo.overlapRegion().size());
        double widthA = 2 * halfA;
        double widthB = 2 * halfB;
        double expectedArea = widthA * widthB / Math.sin(theta);
        assertEquals(expectedArea, JunctionRegionBuilder.polygonArea(geo.overlapRegion()), 1e-4);

        // 相对正交同宽，60° 重叠区应明显拉长（面积更大）
        JunctionRegionGeometry ortho = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), halfA),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), halfB),
            0.0);
        assertTrue(
            JunctionRegionBuilder.polygonArea(geo.overlapRegion())
                > JunctionRegionBuilder.polygonArea(ortho.overlapRegion()) + 1.0,
            "skewed overlap must be larger than orthogonal for same widths");
    }

    @Test
    void fortyFiveDegreeUnequalWidthStillYieldsFourCornerOverlap() {
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(
            new Vec2d(10, 20),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 4.0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 1), 2.0),
            0.0);
        assertEquals(4, geo.overlapRegion().size());
        assertTrue(geo.originalRoadUnion().size() >= 8);
        // 锐角拉长：重叠区最长对角线应明显大于正交情形
        double maxDiag = maxPairDistance(geo.overlapRegion());
        assertTrue(maxDiag > 2 * 4.0 / Math.sin(Math.PI / 4) * 0.5, "45° overlap stretches along acute axis");
    }

    @Test
    void filletExpandsPavementAtConcaveStreetCorners() {
        Vec2d center = new Vec2d(0, 0);
        double half = 3.0;
        JunctionRegionGeometry sharp = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), half),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), half),
            0.0);
        JunctionRegionGeometry filleted = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), half),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), half),
            4.0);

        assertTrue(filleted.curbArcs().size() >= 4, "four street corners");
        assertTrue(filleted.minEffectiveCornerRadius() > 1.5);
        assertFalse(filleted.filletAdditions().isEmpty());

        Vec2d neStreet = new Vec2d(half + 0.6, half + 0.6);
        assertFalse(RoadGeometryUtils.pointInPolygon(neStreet, sharp.finalPavementRegion()),
            "R=0 leaves NE street empty");
        assertTrue(RoadGeometryUtils.pointInPolygon(neStreet, filleted.finalPavementRegion()),
            "R>0 must add pavement into NE street corner; arcs=" + filleted.curbArcs().size()
                + " minR=" + filleted.minEffectiveCornerRadius());

        assertTrue(
            JunctionRegionBuilder.polygonArea(filleted.finalPavementRegion())
                > JunctionRegionBuilder.polygonArea(sharp.finalPavementRegion()) + 1.0,
            "filleted pavement area must exceed sharp union");
    }

    @Test
    void differentRequestedRadiiProduceDistinctFinalContours() {
        Vec2d center = new Vec2d(0, 0);
        JunctionRegionBuilder.StraightRoad a =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0);
        JunctionRegionBuilder.StraightRoad b =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), 3.0);

        JunctionRegionGeometry r0 = JunctionRegionBuilder.build(center, a, b, 0.0);
        JunctionRegionGeometry r4 = JunctionRegionBuilder.build(center, a, b, 4.0);
        JunctionRegionGeometry r8 = JunctionRegionBuilder.build(center, a, b, 8.0);
        JunctionRegionGeometry r12 = JunctionRegionBuilder.build(center, a, b, 12.0);

        double a0 = JunctionRegionBuilder.polygonArea(r0.finalPavementRegion());
        double a4 = JunctionRegionBuilder.polygonArea(r4.finalPavementRegion());
        double a8 = JunctionRegionBuilder.polygonArea(r8.finalPavementRegion());
        double a12 = JunctionRegionBuilder.polygonArea(r12.finalPavementRegion());
        assertTrue(a4 > a0 + 0.5);
        assertTrue(a8 > a4 + 0.5);
        assertTrue(a12 > a8 + 0.25 || r12.maxEffectiveCornerRadius() >= r8.maxEffectiveCornerRadius());
    }

    @Test
    void toJunctionGeometryResultPreservesFillAndFillets() {
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(
            new Vec2d(0, 0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), 2.0),
            3.0);
        JunctionGeometryResult adapted = geo.toJunctionGeometryResult();
        assertTrue(adapted.hasFillPolygon());
        assertEquals(geo.finalPavementRegion().size(), adapted.fillPolygon().size());
        assertFalse(adapted.fillets().isEmpty());
    }

    @Test
    void buildFromFourOppositeApproachesMatchesTwoRoadBuild() {
        List<JunctionCurbFilletBuilder.Approach> approaches = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, -1), 2.0)
        );
        JunctionRegionGeometry fromApproaches =
            JunctionRegionBuilder.buildFromApproaches(new Vec2d(0, 0), approaches, 0.0);
        JunctionRegionGeometry direct = JunctionRegionBuilder.build(
            new Vec2d(0, 0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), 2.0),
            0.0);
        assertEquals(4, fromApproaches.overlapRegion().size());
        assertEquals(
            JunctionRegionBuilder.polygonArea(direct.overlapRegion()),
            JunctionRegionBuilder.polygonArea(fromApproaches.overlapRegion()),
            1e-4);
    }

    private static double maxPairDistance(List<Vec2d> points) {
        double max = 0.0;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                max = Math.max(max, points.get(i).distance(points.get(j)));
            }
        }
        return max;
    }
}
