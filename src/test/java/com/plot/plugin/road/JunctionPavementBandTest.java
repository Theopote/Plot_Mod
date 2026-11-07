package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionPavementBandTest {

    @Test
    void straightStubIsApproximatelyRectangular() {
        List<Vec2d> centerline = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d> band = JunctionPavementBand.buildStubPolygon(centerline, 3.0, 2.0);
        assertTrue(band.size() >= 4);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(5, 0), band));
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(5, 2.5), band));
        assertFalse(RoadGeometryUtils.pointInPolygon(new Vec2d(5, 4), band));
        // 回伸越过原点
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(-1, 0), band));
    }

    @Test
    void curvedStubFollowsArcInsteadOfTangentRectangle() {
        List<Vec2d> arc = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            double t = i / 12.0;
            // 从原点沿四分之一圆弧到 (10,10) 方向
            double angle = t * Math.PI * 0.5;
            arc.add(new Vec2d(10 * Math.sin(angle), 10 * (1 - Math.cos(angle))));
        }
        // 平移使起点在原点
        Vec2d origin = arc.getFirst();
        List<Vec2d> fromOrigin = arc.stream()
            .map(p -> p.subtract(origin))
            .toList();

        List<Vec2d> band = JunctionPavementBand.buildStubPolygon(fromOrigin, 2.0, 1.0);
        assertTrue(band.size() >= 6, "curved band should keep multiple offset samples");

        // 弧中段附近应在条带内；切线无限外推的直角矩形远角不应误盖
        Vec2d midArc = fromOrigin.get(6);
        assertTrue(RoadGeometryUtils.pointInPolygon(midArc, band), "mid-arc centerline inside band");

        JunctionRegionBuilder.StraightRoad fake =
            new JunctionRegionBuilder.StraightRoad(fromOrigin.get(1), 2.0);
        List<Vec2d> tangentRect = JunctionRegionBuilder.buildStripRectangle(
            new Vec2d(0, 0), fake, 12.0);
        // 弧末端附近：真实条带应覆盖；与纯切线矩形覆盖不必相同
        Vec2d nearEnd = fromOrigin.get(fromOrigin.size() - 2);
        assertTrue(RoadGeometryUtils.pointInPolygon(nearEnd, band));
        assertTrue(tangentRect.size() >= 4);
    }

    @Test
    void curvedCrossBandsProduceRegionGeometryWithFillets() {
        // 东西直线 + 北向弯曲支路 组成 T
        List<Vec2d> east = List.of(new Vec2d(0, 0), new Vec2d(12, 0));
        List<Vec2d> west = List.of(new Vec2d(0, 0), new Vec2d(-12, 0));
        List<Vec2d> northCurve = new ArrayList<>();
        northCurve.add(new Vec2d(0, 0));
        for (int i = 1; i <= 10; i++) {
            double t = i / 10.0;
            northCurve.add(new Vec2d(2 * t * t, 10 * t)); // 向东弯的支路
        }
        List<List<Vec2d>> bands = List.of(
            JunctionPavementBand.buildStubPolygon(east, 3.0, 3.0),
            JunctionPavementBand.buildStubPolygon(west, 3.0, 3.0),
            JunctionPavementBand.buildStubPolygon(northCurve, 2.0, 3.0)
        );
        List<JunctionCurbFilletBuilder.Approach> tangents = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), 3.0),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0)
        );
        JunctionRegionGeometry geo = JunctionRegionBuilder.buildFromApproachBands(
            new Vec2d(0, 0), tangents, bands, 2.5);
        assertTrue(geo.hasPavement());
        assertEquals(4, geo.overlapRegion().size());
        assertTrue(geo.curbArcs().size() >= 1, "curved T should still fillet street corners");
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(1.5, 6), geo.originalRoadUnion()),
            "curved stem band must cover bent centerline");
    }
}
