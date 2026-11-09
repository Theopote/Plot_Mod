package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变宽/弯曲真实路缘：切点应从种子弧吸附到条带外缘，而不是停留在交叉中心恒定半宽切线上。
 */
class JunctionBandCurbFilletSnapTest {

    @Test
    void snapMovesEastTangentOntoWidenedCurb() {
        Vec2d center = new Vec2d(0, 0);
        double narrow = 2.5;
        double wideFar = 5.5;
        // 加宽须落在圆角切点后退段内（R=4 → setback≈4），否则种子切点仍在窄段路缘上
        DoubleUnaryOperator eastWidth = along -> along >= 3.0 ? wideFar : narrow;

        List<Vec2d> east = JunctionPavementBand.buildStubPolygon(
            List.of(center, new Vec2d(20, 0)), eastWidth, 3.0);
        List<Vec2d> north = JunctionPavementBand.buildStubPolygon(
            List.of(center, new Vec2d(0, 20)), narrow, 3.0);
        List<Vec2d> west = JunctionPavementBand.buildStubPolygon(
            List.of(center, new Vec2d(-20, 0)), narrow, 3.0);
        List<Vec2d> south = JunctionPavementBand.buildStubPolygon(
            List.of(center, new Vec2d(0, -20)), narrow, 3.0);
        List<List<Vec2d>> bands = List.of(east, west, north, south);

        List<JunctionCurbFilletBuilder.Approach> tangents = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), narrow),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), narrow),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), narrow),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, -1), narrow));

        JunctionRegionGeometry region = JunctionRegionBuilder.buildFromApproachBands(
            center, tangents, bands, 4.0, 8.0);
        assertTrue(region.hasPavement());
        assertTrue(region.curbArcs().size() >= 1, "widened cross should keep curb arcs");

        boolean anyBandCurb = region.curbArcs().stream()
            .anyMatch(arc -> "band_curb".equals(arc.shrinkReason()));
        assertTrue(anyBandCurb, "at least one corner should refine onto real band curbs");

        // 东北角：东向加宽后切点 Y 应接近 wideFar，而不是 narrow
        JunctionRegionGeometry.CurbArc ne = null;
        double best = Double.NEGATIVE_INFINITY;
        for (JunctionRegionGeometry.CurbArc arc : region.curbArcs()) {
            if (arc == null || arc.center() == null) {
                continue;
            }
            double score = arc.center().x + arc.center().y;
            if (score > best) {
                best = score;
                ne = arc;
            }
        }
        assertNotNull(ne);
        double maxAbsY = Math.max(Math.abs(ne.tangentStart().y), Math.abs(ne.tangentEnd().y));
        assertTrue(maxAbsY > narrow + 0.75,
            "NE fillet should touch widened east curb; maxAbsY=" + maxAbsY
                + " start=" + ne.tangentStart() + " end=" + ne.tangentEnd()
                + " reason=" + ne.shrinkReason());
        assertTrue(distanceToBoundary(ne.tangentStart(), east) <= 0.85
                || distanceToBoundary(ne.tangentEnd(), east) <= 0.85
                || distanceToBoundary(ne.tangentStart(), north) <= 0.85
                || distanceToBoundary(ne.tangentEnd(), north) <= 0.85,
            "NE tangents must lie on east/north real curbs");
    }

    private static double distanceToBoundary(Vec2d point, List<Vec2d> ring) {
        double best = Double.POSITIVE_INFINITY;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = ring.get(i);
            Vec2d b = ring.get((i + 1) % n);
            Vec2d ab = b.subtract(a);
            double len2 = ab.lengthSquared();
            if (len2 < 1e-12) {
                best = Math.min(best, point.distance(a));
                continue;
            }
            double t = Math.clamp(point.subtract(a).dot(ab) / len2, 0.0, 1.0);
            best = Math.min(best, point.distance(a.add(ab.multiply(t))));
        }
        return best;
    }
}
