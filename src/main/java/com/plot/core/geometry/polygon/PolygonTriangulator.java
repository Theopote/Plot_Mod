package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单多边形耳切三角化（Phase 1：凸/简单多边形）。
 */
public final class PolygonTriangulator {
    private PolygonTriangulator() {
    }

    public record Triangle(int i0, int i1, int i2) {
    }

    public record TriangulationResult(List<Triangle> triangles, boolean success, String issue) {
        public static TriangulationResult ok(List<Triangle> triangles) {
            return new TriangulationResult(List.copyOf(triangles), true, "");
        }

        public static TriangulationResult fail(String issue) {
            return new TriangulationResult(List.of(), false, issue);
        }
    }

    public static TriangulationResult triangulate(List<Vec2d> points) {
        List<Vec2d> polygon = PolygonNormalizer.normalizeOutline(points);
        if (polygon.size() < 3) {
            return TriangulationResult.fail("too_few_vertices");
        }
        PolygonValidator.ValidationResult validation = PolygonValidator.validateSimplePolygon(polygon);
        if (!validation.valid()) {
            return TriangulationResult.fail(String.join(",", validation.issues()));
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            indices.add(i);
        }

        List<Triangle> triangles = new ArrayList<>();
        int guard = 0;
        while (indices.size() > 3 && guard++ < polygon.size() * polygon.size()) {
            boolean earRemoved = false;
            int count = indices.size();
            for (int i = 0; i < count; i++) {
                int prev = indices.get((i - 1 + count) % count);
                int current = indices.get(i);
                int next = indices.get((i + 1) % count);
                if (!isEar(polygon, indices, prev, current, next)) {
                    continue;
                }
                triangles.add(new Triangle(prev, current, next));
                indices.remove(i);
                earRemoved = true;
                break;
            }
            if (!earRemoved) {
                return TriangulationResult.fail("ear_clipping_stalled");
            }
        }

        if (indices.size() == 3) {
            triangles.add(new Triangle(indices.get(0), indices.get(1), indices.get(2)));
            return TriangulationResult.ok(triangles);
        }
        return TriangulationResult.fail("triangulation_incomplete");
    }

    private static boolean isEar(
            List<Vec2d> polygon,
            List<Integer> indices,
            int prev,
            int current,
            int next) {
        Vec2d a = polygon.get(prev);
        Vec2d b = polygon.get(current);
        Vec2d c = polygon.get(next);
        if ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) <= PolygonUtils.DEFAULT_EPSILON) {
            return false;
        }
        for (int index : indices) {
            if (index == prev || index == current || index == next) {
                continue;
            }
            if (pointInTriangle(polygon.get(index), a, b, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInTriangle(Vec2d point, Vec2d a, Vec2d b, Vec2d c) {
        double area = Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x));
        double a1 = Math.abs((b.x - point.x) * (c.y - point.y) - (b.y - point.y) * (c.x - point.x));
        double a2 = Math.abs((point.x - a.x) * (c.y - a.y) - (point.y - a.y) * (c.x - a.x));
        double a3 = Math.abs((b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x));
        return Math.abs((a1 + a2 + a3) - area) <= PolygonUtils.MIN_AREA;
    }
}
