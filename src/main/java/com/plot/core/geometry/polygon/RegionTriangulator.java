package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;

import java.util.ArrayList;
import java.util.List;

/** 带孔区域三角化（单孔：桥接后耳切）。 */
public final class RegionTriangulator {
    private RegionTriangulator() {
    }

    public static PolygonTriangulator.TriangulationResult triangulate(RegionGeometry region) {
        if (region == null || region.isEmpty()) {
            return PolygonTriangulator.TriangulationResult.fail("empty_region");
        }
        if (!region.hasHoles()) {
            return PolygonTriangulator.triangulate(region.outerRing());
        }
        if (region.holes().size() != 1) {
            return PolygonTriangulator.TriangulationResult.fail("unsupported_hole_count");
        }
        List<Vec2d> bridged = bridgeSingleHole(region.outerRing(), region.holes().getFirst());
        if (bridged.size() < 3) {
            return PolygonTriangulator.TriangulationResult.fail("bridge_failed");
        }
        return PolygonTriangulator.triangulate(bridged);
    }

    public static List<Vec2d> bridgeSingleHole(List<Vec2d> outer, List<Vec2d> hole) {
        if (outer == null || outer.size() < 3 || hole == null || hole.size() < 3) {
            return List.of();
        }
        int holeIndex = indexOfRightmost(hole);
        int outerIndex = findBridgeOuterIndex(outer, hole, holeIndex);
        if (outerIndex < 0) {
            outerIndex = indexOfRightmost(outer);
        }

        List<Vec2d> bridged = new ArrayList<>(outer.size() + hole.size() + 4);
        for (int i = 0; i <= outer.size(); i++) {
            bridged.add(outer.get((outerIndex + i) % outer.size()).copy());
        }
        Vec2d bridgeHole = hole.get(holeIndex).copy();
        bridged.add(bridgeHole);
        for (int i = 0; i <= hole.size(); i++) {
            bridged.add(hole.get((holeIndex + i) % hole.size()).copy());
        }
        bridged.add(outer.get(outerIndex).copy());
        return PolygonNormalizer.removeDuplicateVertices(bridged);
    }

    private static int indexOfRightmost(List<Vec2d> ring) {
        int best = 0;
        for (int i = 1; i < ring.size(); i++) {
            Vec2d candidate = ring.get(i);
            Vec2d current = ring.get(best);
            if (candidate.x > current.x + PolygonUtils.DEFAULT_EPSILON
                    || (Math.abs(candidate.x - current.x) <= PolygonUtils.DEFAULT_EPSILON
                        && candidate.y < current.y)) {
                best = i;
            }
        }
        return best;
    }

    private static int findBridgeOuterIndex(List<Vec2d> outer, List<Vec2d> hole, int holeIndex) {
        Vec2d holePoint = hole.get(holeIndex);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < outer.size(); i++) {
            Vec2d outerPoint = outer.get(i);
            if (!isValidBridge(outer, hole, outerPoint, holePoint, holeIndex)) {
                continue;
            }
            double distance = outerPoint.distance(holePoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static boolean isValidBridge(
            List<Vec2d> outer,
            List<Vec2d> hole,
            Vec2d outerPoint,
            Vec2d holePoint,
            int holeIndex) {
        if (segmentIntersectsRing(outerPoint, holePoint, outer, -1)) {
            return false;
        }
        if (segmentIntersectsRing(outerPoint, holePoint, hole, holeIndex)) {
            return false;
        }
        return true;
    }

    private static boolean segmentIntersectsRing(
            Vec2d start,
            Vec2d end,
            List<Vec2d> ring,
            int skipVertexIndex) {
        int count = ring.size();
        for (int i = 0; i < count; i++) {
            if (i == skipVertexIndex) {
                continue;
            }
            Vec2d a = ring.get(i);
            Vec2d b = ring.get((i + 1) % count);
            if (segmentsIntersectProperly(start, end, a, b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentsIntersectProperly(Vec2d a, Vec2d b, Vec2d c, Vec2d d) {
        if (a.distance(c) <= PolygonUtils.DEFAULT_EPSILON
                || a.distance(d) <= PolygonUtils.DEFAULT_EPSILON
                || b.distance(c) <= PolygonUtils.DEFAULT_EPSILON
                || b.distance(d) <= PolygonUtils.DEFAULT_EPSILON) {
            return false;
        }
        double denominator = (b.x - a.x) * (d.y - c.y) - (b.y - a.y) * (d.x - c.x);
        if (Math.abs(denominator) <= PolygonUtils.DEFAULT_EPSILON) {
            return false;
        }
        double ua = ((d.x - c.x) * (a.y - c.y) - (d.y - c.y) * (a.x - c.x)) / denominator;
        double ub = ((b.x - a.x) * (a.y - c.y) - (b.y - a.y) * (a.x - c.x)) / denominator;
        return ua > PolygonUtils.DEFAULT_EPSILON
                && ua < 1.0 - PolygonUtils.DEFAULT_EPSILON
                && ub > PolygonUtils.DEFAULT_EPSILON
                && ub < 1.0 - PolygonUtils.DEFAULT_EPSILON;
    }
}
