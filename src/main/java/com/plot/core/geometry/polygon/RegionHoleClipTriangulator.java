package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 带孔区域三角化：外环耳切后，对与孔相交的三角形做中点细分，
 * 仅保留完全位于所有孔外的子三角形。
 * <p>
 * 保证：任何输出三角形不会进入孔洞内部（不含孔边界上的退化情况）。
 * 孔边界附近可能留下亚像素级缝隙；面积在细分深度下收敛到净面积。
 */
public final class RegionHoleClipTriangulator {
    private static final int MAX_SUBDIVIDE_DEPTH = 12;

    private RegionHoleClipTriangulator() {
    }

    public record WorldTriangle(Vec2d a, Vec2d b, Vec2d c) {
        public double absoluteArea() {
            return PolygonUtils.absoluteArea(List.of(a, b, c));
        }
    }

    public static List<WorldTriangle> triangulate(RegionGeometry region) {
        if (region == null || region.isEmpty()) {
            return List.of();
        }
        List<Vec2d> outer = PolygonNormalizer.normalizeOutline(region.outerRing());
        if (outer.size() < 3) {
            return List.of();
        }
        PolygonTriangulator.TriangulationResult outerMesh = PolygonTriangulator.triangulate(outer);
        if (!outerMesh.success()) {
            return List.of();
        }

        List<List<Vec2d>> holes = new ArrayList<>();
        for (List<Vec2d> hole : region.holes()) {
            List<Vec2d> normalized = PolygonNormalizer.normalizeOutline(hole);
            if (normalized.size() >= 3) {
                holes.add(normalized);
            }
        }
        if (holes.isEmpty()) {
            List<WorldTriangle> plain = new ArrayList<>(outerMesh.triangles().size());
            for (PolygonTriangulator.Triangle t : outerMesh.triangles()) {
                plain.add(new WorldTriangle(outer.get(t.i0()), outer.get(t.i1()), outer.get(t.i2())));
            }
            return List.copyOf(plain);
        }

        List<WorldTriangle> result = new ArrayList<>();
        for (PolygonTriangulator.Triangle t : outerMesh.triangles()) {
            subdivideAndKeep(
                outer.get(t.i0()),
                outer.get(t.i1()),
                outer.get(t.i2()),
                holes,
                MAX_SUBDIVIDE_DEPTH,
                result);
        }
        return List.copyOf(result);
    }

    static void subdivideAndKeep(
            Vec2d a,
            Vec2d b,
            Vec2d c,
            List<List<Vec2d>> holes,
            int depth,
            List<WorldTriangle> out) {
        if (PolygonUtils.absoluteArea(List.of(a, b, c)) <= PolygonUtils.MIN_AREA) {
            return;
        }
        boolean aIn = insideAnyHole(a, holes);
        boolean bIn = insideAnyHole(b, holes);
        boolean cIn = insideAnyHole(c, holes);
        if (aIn && bIn && cIn) {
            return;
        }
        boolean crosses = edgesCrossAnyHole(a, b, c, holes)
            || anyHoleVertexInsideTriangle(a, b, c, holes);
        if (!aIn && !bIn && !cIn && !crosses) {
            out.add(new WorldTriangle(a.copy(), b.copy(), c.copy()));
            return;
        }
        if (depth <= 0) {
            // 安全降级：丢弃仍与孔相交的叶子，避免错误填充
            return;
        }
        Vec2d ab = midpoint(a, b);
        Vec2d bc = midpoint(b, c);
        Vec2d ca = midpoint(c, a);
        subdivideAndKeep(a, ab, ca, holes, depth - 1, out);
        subdivideAndKeep(b, bc, ab, holes, depth - 1, out);
        subdivideAndKeep(c, ca, bc, holes, depth - 1, out);
        subdivideAndKeep(ab, bc, ca, holes, depth - 1, out);
    }

    private static boolean insideAnyHole(Vec2d point, List<List<Vec2d>> holes) {
        for (List<Vec2d> hole : holes) {
            if (PolygonRegionUtils.containsPoint(hole, List.of(), point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean edgesCrossAnyHole(Vec2d a, Vec2d b, Vec2d c, List<List<Vec2d>> holes) {
        for (List<Vec2d> hole : holes) {
            if (PolygonBoolean.intersectsSegment(hole, a, b)
                || PolygonBoolean.intersectsSegment(hole, b, c)
                || PolygonBoolean.intersectsSegment(hole, c, a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyHoleVertexInsideTriangle(
            Vec2d a, Vec2d b, Vec2d c, List<List<Vec2d>> holes) {
        for (List<Vec2d> hole : holes) {
            for (Vec2d hv : hole) {
                if (pointInTriangle(hv, a, b, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pointInTriangle(Vec2d p, Vec2d a, Vec2d b, Vec2d c) {
        double area = Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x));
        double a1 = Math.abs((b.x - p.x) * (c.y - p.y) - (b.y - p.y) * (c.x - p.x));
        double a2 = Math.abs((p.x - a.x) * (c.y - a.y) - (p.y - a.y) * (c.x - a.x));
        double a3 = Math.abs((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x));
        return Math.abs((a1 + a2 + a3) - area) <= PolygonUtils.MIN_AREA;
    }

    private static Vec2d midpoint(Vec2d a, Vec2d b) {
        return new Vec2d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5);
    }
}
