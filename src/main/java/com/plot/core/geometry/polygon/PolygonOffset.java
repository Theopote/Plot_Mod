package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 闭合多边形偏移（逐边平移 + 顶点求交，含 miter/bevel 回退与结果校验）。
 */
public final class PolygonOffset {
    private PolygonOffset() {
    }

    public record OffsetResult(List<Vec2d> points, boolean success, List<String> warnings) {
        public static OffsetResult ok(List<Vec2d> points) {
            return new OffsetResult(List.copyOf(points), true, List.of());
        }

        public static OffsetResult fail(String warning) {
            return new OffsetResult(List.of(), false, List.of(warning));
        }

        public List<Vec2d> pointsOrEmpty() {
            return success ? points : List.of();
        }
    }

    public static OffsetResult offset(List<Vec2d> points, double distance) {
        return offset(points, distance, PolygonUtils.DEFAULT_MITER_LIMIT);
    }

    public static OffsetResult offset(List<Vec2d> points, double distance, double miterLimit) {
        if (points == null || points.size() < 3) {
            return OffsetResult.fail("too_few_vertices");
        }
        if (!PolygonValidator.hasFiniteCoordinates(points)) {
            return OffsetResult.fail("non_finite_coordinates");
        }
        if (Math.abs(distance) <= PolygonUtils.DEFAULT_EPSILON) {
            return OffsetResult.ok(PolygonNormalizer.removeDuplicateVertices(points));
        }

        List<Vec2d> normalized = PolygonNormalizer.normalizeOutline(points);
        if (normalized.size() < 3) {
            return OffsetResult.fail("degenerate_after_normalize");
        }

        double sourceArea = PolygonUtils.absoluteArea(normalized);
        if (sourceArea <= PolygonUtils.MIN_AREA) {
            return OffsetResult.fail("degenerate_area");
        }

        int n = normalized.size();
        List<OffsetEdge> offsetEdges = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec2d start = normalized.get(i);
            Vec2d end = normalized.get((i + 1) % n);
            Vec2d direction = end.subtract(start);
            double length = direction.length();
            if (length <= PolygonUtils.DEFAULT_EPSILON) {
                return OffsetResult.fail("zero_length_edge");
            }
            Vec2d unit = direction.multiply(1.0 / length);
            Vec2d offsetVector = PolygonUtils.leftNormal(unit).multiply(distance);
            offsetEdges.add(new OffsetEdge(
                start.add(offsetVector),
                end.add(offsetVector),
                unit
            ));
        }

        List<Vec2d> offsetPoints = new ArrayList<>(n * 2);
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int cornerIndex = (i + 1) % n;
            Vec2d prev = normalized.get(i);
            Vec2d vertex = normalized.get(cornerIndex);
            Vec2d next = normalized.get((cornerIndex + 1) % n);
            OffsetEdge current = offsetEdges.get(i);
            OffsetEdge nextEdge = offsetEdges.get((i + 1) % n);

            if (isReflexVertex(prev, vertex, next)) {
                appendBevelCorner(offsetPoints, current.end(), nextEdge.start(), warnings);
                continue;
            }

            Vec2d corner = intersectOffsetEdges(
                vertex,
                current,
                nextEdge,
                Math.abs(distance),
                miterLimit,
                warnings
            );
            if (corner == null || !PolygonUtils.isFinite(corner)) {
                return OffsetResult.fail("offset_intersection_failed");
            }
            offsetPoints.add(corner);
        }

        List<Vec2d> cleaned = PolygonNormalizer.removeDuplicateVertices(offsetPoints);
        if (cleaned.size() < 3) {
            return OffsetResult.fail("collapsed_polygon");
        }
        if (!PolygonValidator.hasFiniteCoordinates(cleaned)) {
            return OffsetResult.fail("non_finite_offset_result");
        }
        if (PolygonValidator.hasSelfIntersection(cleaned)) {
            return OffsetResult.fail("self_intersection");
        }

        double resultArea = PolygonUtils.absoluteArea(cleaned);
        if (resultArea <= PolygonUtils.MIN_AREA) {
            return OffsetResult.fail("collapsed_area");
        }

        boolean expanding = resultArea > sourceArea + PolygonUtils.MIN_AREA;
        boolean shrinking = resultArea + PolygonUtils.MIN_AREA < sourceArea;
        if (distance > 0 && expanding) {
            return OffsetResult.fail("unexpected_expand_on_inward_offset");
        }
        if (distance < 0 && shrinking) {
            return OffsetResult.fail("unexpected_shrink_on_outward_offset");
        }

        PolygonValidator.ValidationResult validation = PolygonValidator.validateSimplePolygon(cleaned);
        if (!validation.valid()) {
            return OffsetResult.fail(String.join(",", validation.issues()));
        }

        return new OffsetResult(cleaned, true, List.copyOf(warnings));
    }

    /**
     * 内缩偏移：先规范为 CCW，再沿左法线正距离偏移（真正向内）。
     */
    public static OffsetResult offsetInward(List<Vec2d> points, double distance) {
        if (points == null || points.size() < 3 || distance <= 0) {
            return OffsetResult.ok(points == null ? List.of() : copy(points));
        }
        List<Vec2d> normalized = PolygonNormalizer.normalizeWinding(
            PolygonNormalizer.normalizeOutline(points),
            PolygonUtils.Winding.COUNTER_CLOCKWISE);
        if (normalized.size() < 3) {
            return OffsetResult.fail("degenerate_after_normalize");
        }
        return offset(normalized, distance);
    }

    /**
     * 外扩偏移：先规范为 CCW，再沿左法线负距离偏移。
     */
    public static OffsetResult offsetOutward(List<Vec2d> points, double distance) {
        if (points == null || points.size() < 3 || distance <= 0) {
            return OffsetResult.ok(points == null ? List.of() : copy(points));
        }
        List<Vec2d> normalized = PolygonNormalizer.normalizeWinding(
            PolygonNormalizer.normalizeOutline(points),
            PolygonUtils.Winding.COUNTER_CLOCKWISE);
        if (normalized.size() < 3) {
            return OffsetResult.fail("degenerate_after_normalize");
        }
        return offset(normalized, -distance);
    }

    private static boolean isReflexVertex(Vec2d prev, Vec2d vertex, Vec2d next) {
        Vec2d incoming = vertex.subtract(prev);
        Vec2d outgoing = next.subtract(vertex);
        return incoming.cross(outgoing) < -PolygonUtils.DEFAULT_EPSILON;
    }

    private static void appendBevelCorner(
            List<Vec2d> output,
            Vec2d bevelA,
            Vec2d bevelB,
            List<String> warnings) {
        warnings.add("reflex_bevel");
        if (bevelA.distance(bevelB) <= PolygonUtils.DEFAULT_EPSILON) {
            output.add(bevelA);
            return;
        }
        output.add(bevelA);
        output.add(bevelB);
    }

    private static Vec2d intersectOffsetEdges(
            Vec2d vertex,
            OffsetEdge current,
            OffsetEdge next,
            double distance,
            double miterLimit,
            List<String> warnings) {
        Vec2d intersection = intersectInfiniteLines(
            current.start(), current.direction(),
            next.start(), next.direction()
        );
        if (intersection != null && PolygonUtils.isFinite(intersection)) {
            double miterLength = intersection.distance(vertex);
            if (miterLength <= distance * miterLimit + PolygonUtils.DEFAULT_EPSILON) {
                return intersection;
            }
            warnings.add("miter_limit_exceeded");
        } else {
            warnings.add("parallel_offset_edges");
        }

        Vec2d bevelA = current.end();
        Vec2d bevelB = next.start();
        return new Vec2d((bevelA.x + bevelB.x) / 2.0, (bevelA.y + bevelB.y) / 2.0);
    }

    private static Vec2d intersectInfiniteLines(Vec2d p1, Vec2d d1, Vec2d p2, Vec2d d2) {
        double cross = d1.cross(d2);
        if (Math.abs(cross) <= PolygonUtils.DEFAULT_EPSILON) {
            List<Vec2d> segmentHit = GeometryUtils.segmentIntersection(p1, p1.add(d1), p2, p2.add(d2));
            return segmentHit.isEmpty() ? null : segmentHit.getFirst();
        }
        double t = p2.subtract(p1).cross(d2) / cross;
        return p1.add(d1.multiply(t));
    }

    private static List<Vec2d> copy(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            if (point != null) {
                copy.add(point.copy());
            }
        }
        return copy;
    }

    private record OffsetEdge(Vec2d start, Vec2d end, Vec2d direction) {
    }
}
