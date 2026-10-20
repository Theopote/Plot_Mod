package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 带孔区域三角化：逐孔桥接为简单多边形后耳切。
 * <p>
 * 桥接走廊对回程边做微小偏移，避免重合顶点导致耳切失败；支持多个互不重叠内环。
 */
public final class RegionTriangulator {
    /** 桥接回程偏移（画布单位），足够小不影响可视面积，足够大避免重合。 */
    private static final double BRIDGE_EPSILON = 1e-4;

    private RegionTriangulator() {
    }

    public static PolygonTriangulator.TriangulationResult triangulate(RegionGeometry region) {
        if (region == null || region.isEmpty()) {
            return PolygonTriangulator.TriangulationResult.fail("empty_region");
        }
        if (!region.hasHoles()) {
            return PolygonTriangulator.triangulate(region.outerRing());
        }
        BridgedPolygon bridged = bridgeAllHoles(region.outerRing(), region.holes());
        if (bridged == null || bridged.vertices().size() < 3) {
            return PolygonTriangulator.TriangulationResult.fail("bridge_failed");
        }
        return PolygonTriangulator.triangulate(bridged.vertices());
    }

    /**
     * 将外环与全部内环桥接为单一简单多边形（规范化后无重复顶点）。
     * 失败返回 {@code null}。
     */
    public static BridgedPolygon bridgeAllHoles(List<Vec2d> outer, List<List<Vec2d>> holes) {
        if (outer == null || outer.size() < 3) {
            return null;
        }
        if (holes == null || holes.isEmpty()) {
            List<Vec2d> vertices = PolygonNormalizer.normalizeOutline(outer);
            return vertices.size() >= 3 ? new BridgedPolygon(List.copyOf(vertices)) : null;
        }

        boolean outerCcw = PolygonUtils.isCounterClockwise(outer);
        List<Vec2d> current = PolygonNormalizer.normalizeOutline(outer);
        if (current.size() < 3) {
            return null;
        }

        List<List<Vec2d>> orderedHoles = new ArrayList<>();
        for (List<Vec2d> hole : holes) {
            if (hole != null && hole.size() >= 3) {
                orderedHoles.add(hole);
            }
        }
        orderedHoles.sort(Comparator.comparingDouble(RegionTriangulator::rightmostX).reversed());

        for (List<Vec2d> hole : orderedHoles) {
            List<Vec2d> orientedHole = ensureOppositeWinding(
                PolygonNormalizer.normalizeOutline(hole), outerCcw);
            if (orientedHole.size() < 3) {
                return null;
            }
            List<Vec2d> next = bridgeSingleHole(current, orientedHole);
            if (next.size() < 3) {
                return null;
            }
            current = PolygonNormalizer.normalizeOutline(next);
            if (current.size() < 3) {
                // normalize 可能过度简化；保留 removeDuplicate 结果再试标准三角化输入
                current = PolygonNormalizer.removeDuplicateVertices(next);
                if (current.size() < 3) {
                    return null;
                }
            }
        }
        // 最终保证 CCW，供耳切使用
        if (!PolygonUtils.isCounterClockwise(current)) {
            current = reverseCopy(current);
        }
        return new BridgedPolygon(List.copyOf(current));
    }

    public record BridgedPolygon(List<Vec2d> vertices) {
    }

    /**
     * 单孔桥接：外环 → 孔 → 偏移回程，形成无重合顶点的细走廊。
     */
    public static List<Vec2d> bridgeSingleHole(List<Vec2d> outer, List<Vec2d> hole) {
        if (outer == null || outer.size() < 3 || hole == null || hole.size() < 3) {
            return List.of();
        }
        int holeIndex = indexOfRightmost(hole);
        int outerIndex = findBridgeOuterIndex(outer, hole, holeIndex);
        if (outerIndex < 0) {
            outerIndex = indexOfRightmost(outer);
        }

        Vec2d outerPoint = outer.get(outerIndex);
        Vec2d holePoint = hole.get(holeIndex);
        Vec2d offsetReturn = offsetPerpendicular(outerPoint, holePoint, BRIDGE_EPSILON);

        List<Vec2d> bridged = new ArrayList<>(outer.size() + hole.size() + 4);
        // 外环从桥接点走一整圈回到桥接点（含起点，不含终点重复）
        for (int i = 0; i < outer.size(); i++) {
            bridged.add(outer.get((outerIndex + i) % outer.size()).copy());
        }
        // 去孔
        bridged.add(holePoint.copy());
        for (int i = 1; i < hole.size(); i++) {
            bridged.add(hole.get((holeIndex + i) % hole.size()).copy());
        }
        bridged.add(holePoint.copy());
        // 偏移回程，避免与去程重合
        bridged.add(offsetReturn);

        return PolygonNormalizer.removeDuplicateVertices(bridged);
    }

    private static Vec2d offsetPerpendicular(Vec2d outerPoint, Vec2d holePoint, double epsilon) {
        double dx = outerPoint.x - holePoint.x;
        double dy = outerPoint.y - holePoint.y;
        double len = Math.hypot(dx, dy);
        if (len < PolygonUtils.DEFAULT_EPSILON) {
            return new Vec2d(outerPoint.x + epsilon, outerPoint.y);
        }
        // 垂直于桥接方向的微小偏移
        double nx = -dy / len;
        double ny = dx / len;
        return new Vec2d(outerPoint.x + nx * epsilon, outerPoint.y + ny * epsilon);
    }

    private static List<Vec2d> ensureOppositeWinding(List<Vec2d> hole, boolean outerCcw) {
        if (hole.size() < 3) {
            return hole;
        }
        boolean holeCcw = PolygonUtils.isCounterClockwise(hole);
        if (holeCcw == outerCcw) {
            return reverseCopy(hole);
        }
        return hole;
    }

    private static List<Vec2d> reverseCopy(List<Vec2d> ring) {
        List<Vec2d> reversed = new ArrayList<>(ring.size());
        for (int i = ring.size() - 1; i >= 0; i--) {
            reversed.add(ring.get(i).copy());
        }
        return reversed;
    }

    private static double rightmostX(List<Vec2d> ring) {
        double max = Double.NEGATIVE_INFINITY;
        for (Vec2d point : ring) {
            if (point != null) {
                max = Math.max(max, point.x);
            }
        }
        return max;
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
