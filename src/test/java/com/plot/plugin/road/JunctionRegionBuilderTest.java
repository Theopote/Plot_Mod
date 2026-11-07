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
    void acuteAndObliqueAnglesKeepOverlapCornersInsidePavementStrips() {
        Vec2d center = new Vec2d(0, 0);
        double halfA = 3.0;
        double halfB = 3.0;
        for (double degrees : List.of(15.0, 30.0, 45.0, 60.0, 90.0)) {
            double theta = Math.toRadians(degrees);
            JunctionRegionBuilder.StraightRoad a =
                new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), halfA);
            JunctionRegionBuilder.StraightRoad b =
                new JunctionRegionBuilder.StraightRoad(new Vec2d(Math.cos(theta), Math.sin(theta)), halfB);
            JunctionRegionGeometry geo = JunctionRegionBuilder.build(center, a, b, 0.0);
            assertEquals(4, geo.overlapRegion().size(), degrees + "° overlap corners");
            List<Vec2d> stripA = JunctionRegionBuilder.buildStripRectangle(
                center, a, JunctionRegionBuilder.pavementExtentFromOverlap(
                    center, a, b, geo.overlapRegion()));
            List<Vec2d> stripB = JunctionRegionBuilder.buildStripRectangle(
                center, b, JunctionRegionBuilder.pavementExtentFromOverlap(
                    center, a, b, geo.overlapRegion()));
            for (Vec2d corner : geo.overlapRegion()) {
                assertTrue(
                    RoadGeometryUtils.pointInPolygon(corner, stripA)
                        || distanceToPolygon(corner, stripA) < 1e-3,
                    degrees + "° corner must lie in strip A");
                assertTrue(
                    RoadGeometryUtils.pointInPolygon(corner, stripB)
                        || distanceToPolygon(corner, stripB) < 1e-3,
                    degrees + "° corner must lie in strip B");
                assertTrue(
                    RoadGeometryUtils.pointInPolygon(corner, geo.originalRoadUnion())
                        || distanceToPolygon(corner, geo.originalRoadUnion()) < 1e-3,
                    degrees + "° corner must lie in pavement union");
            }
        }
        // 不等宽 15°
        JunctionRegionGeometry skewed = JunctionRegionBuilder.build(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 4.5),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(Math.cos(Math.toRadians(15)), Math.sin(Math.toRadians(15))), 2.5),
            0.0);
        assertEquals(4, skewed.overlapRegion().size());
        assertTrue(skewed.hasPavement());
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
        assertFalse(sharp.containsPavement(neStreet), "R=0 leaves NE street empty");
        assertTrue(filleted.containsPavement(neStreet),
            "R>0 must add pavement into NE street corner; arcs=" + filleted.curbArcs().size()
                + " minR=" + filleted.minEffectiveCornerRadius());
        assertFalse(filleted.filletAdditions().isEmpty());
        double addArea = filleted.filletAdditions().stream()
            .mapToDouble(JunctionRegionBuilder::polygonArea)
            .sum();
        assertTrue(addArea > 1.0, "fillet additions must cover street corners");
    }

    @Test
    void acuteSkewFilletsStayAtOverlapCornersNotFarArms() {
        Vec2d center = new Vec2d(0, 0);
        double theta = Math.toRadians(35);
        JunctionRegionBuilder.StraightRoad a =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 2.5);
        JunctionRegionBuilder.StraightRoad b =
            new JunctionRegionBuilder.StraightRoad(
                new Vec2d(Math.cos(theta), Math.sin(theta)), 1.5);
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(center, a, b, 12.0);

        assertTrue(geo.curbArcs().size() >= 4, "cross needs four curb returns");
        for (var arc : geo.curbArcs()) {
            double nearCorner = Double.POSITIVE_INFINITY;
            for (Vec2d corner : geo.overlapRegion()) {
                nearCorner = Math.min(nearCorner, Math.min(
                    arc.tangentStart().distance(corner),
                    arc.tangentEnd().distance(corner)));
            }
            // 正确后退 t = R / tan(φ/2)
            double maxSetback = arc.radius()
                / Math.max(0.05, Math.tan(arc.cornerAngle() * 0.5)) + 1.5;
            assertTrue(nearCorner <= maxSetback,
                "arc tangents must belong to an overlap street corner; near=" + nearCorner
                    + " maxSetback=" + maxSetback + " ang=" + Math.toDegrees(arc.cornerAngle()));
            double oA = Math.abs(arc.center().dot(a.normal()));
            double oB = Math.abs(arc.center().dot(b.normal()));
            assertTrue(oA > a.halfWidth() - 1e-3 && oB > b.halfWidth() - 1e-3,
                "fillet center must lie outside both strips; oA=" + oA + " oB=" + oB);
            // 弧中点应落在两条路缘带之外（街角草地），不得缩进交叉点附近
            double sa = Math.atan2(
                arc.tangentStart().y - arc.center().y, arc.tangentStart().x - arc.center().x);
            double sweep = FilletArcSweep.resolveShort(
                arc.center(), arc.tangentStart(), arc.tangentEnd());
            assertTrue(Math.abs(sweep) <= Math.PI + 1e-6,
                "curb arc must be a short sweep, not a near-full circle; sweepDeg="
                    + Math.toDegrees(sweep));
            Vec2d mid = FilletArcSweep.pointOnArc(arc.center(), arc.radius(), sa + sweep * 0.5);
            assertTrue(Math.abs(mid.dot(a.normal())) > a.halfWidth() - 0.05
                    && Math.abs(mid.dot(b.normal())) > b.halfWidth() - 0.05,
                "arc midpoint must lie in the outer grass pocket, mid=" + mid);
        }
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

        double a0 = r0.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        double a4 = r4.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        double a8 = r8.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        double a12 = r12.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        assertTrue(a4 > a0 + 0.5);
        assertTrue(a8 > a4 + 0.5);
        assertTrue(a12 > a8 + 0.25 || r12.maxEffectiveCornerRadius() >= r8.maxEffectiveCornerRadius());
    }

    @Test
    void pavementArmLengthIndependentOfCornerRadius() {
        Vec2d center = new Vec2d(0, 0);
        JunctionRegionBuilder.StraightRoad a =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0);
        JunctionRegionBuilder.StraightRoad b =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(0, 1), 3.0);

        JunctionRegionGeometry r0 = JunctionRegionBuilder.build(center, a, b, 0.0);
        JunctionRegionGeometry r12 = JunctionRegionBuilder.build(center, a, b, 12.0);

        assertEquals(
            JunctionRegionBuilder.polygonArea(r0.originalRoadUnion()),
            JunctionRegionBuilder.polygonArea(r12.originalRoadUnion()),
            1e-6,
            "original union must not grow with R");
        // 远离开口嘴部：应同属原始并集，不因 R 进出最终铺装
        Vec2d farEastMouth = new Vec2d(6.5, 0.0);
        assertTrue(RoadGeometryUtils.pointInPolygon(farEastMouth, r0.originalRoadUnion()));
        assertTrue(RoadGeometryUtils.pointInPolygon(farEastMouth, r0.finalPavementRegion()));
        assertTrue(RoadGeometryUtils.pointInPolygon(farEastMouth, r12.finalPavementRegion()));
        // 更远的路臂外点：两种 R 都不应覆盖（禁止因 extent 随 R 变长而吞路）
        Vec2d beyondArm = new Vec2d(15.0, 0.0);
        assertFalse(RoadGeometryUtils.pointInPolygon(beyondArm, r0.finalPavementRegion()));
        assertFalse(RoadGeometryUtils.pointInPolygon(beyondArm, r12.finalPavementRegion()));
    }

    @Test
    void twoApproachesDoNotExpandIntoFourArmCross() {
        List<JunctionCurbFilletBuilder.Approach> twoArms = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0.2, 1), 3.0)
        );
        JunctionRegionGeometry geo =
            JunctionRegionBuilder.buildFromApproaches(new Vec2d(0, 0), twoArms, 4.0);
        assertFalse(geo.hasPavement(), "two-arm nodes must not synthesize a 4-arm cross");
    }

    @Test
    void orthogonalTJunctionHasTwoCurbFilletsAndNoSouthStem() {
        Vec2d center = new Vec2d(0, 0);
        JunctionRegionGeometry sharp = JunctionRegionBuilder.buildT(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0),
            0.0);
        JunctionRegionGeometry filleted = JunctionRegionBuilder.buildT(
            center,
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0),
            3.0);

        assertTrue(sharp.hasPavement());
        assertEquals(4, sharp.overlapRegion().size());
        // 贯通道路背侧不应被支路铺装覆盖
        assertFalse(RoadGeometryUtils.pointInPolygon(new Vec2d(0, -6), sharp.originalRoadUnion()),
            "T must not invent a south stem arm");
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 5), sharp.originalRoadUnion()),
            "stem stub must extend north");
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(5, 0), sharp.originalRoadUnion()),
            "through road must remain");

        assertTrue(filleted.curbArcs().size() >= 2, "T has two street-corner fillets");
        Vec2d neStreet = new Vec2d(2.0 + 0.5, 3.0 + 0.5);
        assertFalse(sharp.containsPavement(neStreet));
        assertTrue(filleted.containsPavement(neStreet), "fillet must expand NE T-corner");
    }

    @Test
    void threeApproachesBuildTViaBuildFromApproaches() {
        List<JunctionCurbFilletBuilder.Approach> tee = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.5)
        );
        JunctionRegionGeometry geo =
            JunctionRegionBuilder.buildFromApproaches(new Vec2d(0, 0), tee, 2.0);
        assertTrue(geo.hasPavement());
        assertTrue(geo.hasPavement() && geo.toJunctionGeometryResult().hasRegionZones());
        assertTrue(geo.curbArcs().size() >= 2);
    }

    @Test
    void filletInfluenceOnlyExpandsStreetCorners() {
        Vec2d center = new Vec2d(0, 0);
        JunctionRegionBuilder.StraightRoad wide =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 4.5); // width 9
        JunctionRegionBuilder.StraightRoad narrow =
            new JunctionRegionBuilder.StraightRoad(
                new Vec2d(Math.cos(Math.toRadians(60)), Math.sin(Math.toRadians(60))),
                2.5); // width 5

        JunctionRegionGeometry r0 = JunctionRegionBuilder.build(center, wide, narrow, 0.0);
        JunctionRegionGeometry r4 = JunctionRegionBuilder.build(center, wide, narrow, 4.0);
        JunctionRegionGeometry r12 = JunctionRegionBuilder.build(center, wide, narrow, 12.0);

        assertEquals(
            JunctionRegionBuilder.polygonArea(r0.originalRoadUnion()),
            JunctionRegionBuilder.polygonArea(r12.originalRoadUnion()),
            1e-6);

        // 锐角并集环不随 R 变；仅补铺多边形随 R 变，且不得侵上路臂远端
        assertEquals(
            JunctionRegionBuilder.polygonArea(r0.finalPavementRegion()),
            JunctionRegionBuilder.polygonArea(r12.finalPavementRegion()),
            1e-6,
            "final ring stays sharp union; fillets live in additions");
        int cornerChanged = 0;
        int armChanged = 0;
        for (int ix = -20; ix <= 20; ix++) {
            for (int iz = -20; iz <= 20; iz++) {
                Vec2d p = new Vec2d(ix + 0.5, iz + 0.5);
                boolean in0 = r0.containsPavement(p);
                boolean in4 = r4.containsPavement(p);
                boolean in12 = r12.containsPavement(p);
                if (in0 == in4 && in4 == in12) {
                    continue;
                }
                boolean nearAnyFillet = false;
                for (var addition : r12.filletAdditions()) {
                    if (RoadGeometryUtils.pointInPolygon(p, addition)
                            || distanceToPolygon(p, addition) < 1.25) {
                        nearAnyFillet = true;
                        break;
                    }
                }
                if (nearAnyFillet) {
                    cornerChanged++;
                } else {
                    armChanged++;
                }
            }
        }
        assertTrue(cornerChanged > 0, "street corners must change with R");
        assertEquals(0, armChanged, "cells far from curb fillets must not change with R");
    }

    private static double distanceToPolygon(Vec2d point, List<Vec2d> polygon) {
        double min = Double.POSITIVE_INFINITY;
        if (polygon == null || polygon.size() < 2) {
            return min;
        }
        for (int i = 0; i < polygon.size(); i++) {
            Vec2d a = polygon.get(i);
            Vec2d b = polygon.get((i + 1) % polygon.size());
            Vec2d ab = b.subtract(a);
            double len2 = ab.lengthSquared();
            double t = len2 < 1e-18 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / len2));
            min = Math.min(min, point.distance(a.add(ab.multiply(t))));
        }
        return min;
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
        assertEquals(geo.originalRoadUnion(), adapted.sharpPolygon());
        assertTrue(adapted.fillPolygon().size() > geo.finalPavementRegion().size(),
            "fillPolygon weaves curb arcs into the sharp union");
        assertFalse(adapted.fillets().isEmpty());
        assertEquals(geo.filletAdditions().size(), adapted.filletAdditions().size());
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
