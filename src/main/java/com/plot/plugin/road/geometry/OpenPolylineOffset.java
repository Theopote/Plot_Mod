package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.core.geometry.polygon.PolygonValidator;

import java.util.ArrayList;
import java.util.List;

/** 开放折线等距偏移（无限直线斜接 + miter limit + bevel；画布可用 round join）。 */
final class OpenPolylineOffset {
    private static final double ROUND_JOIN_STEP_RADIANS = Math.PI / 12.0;

    enum JoinStyle {
        /** 斜接，超限切 bevel（生成/土方等）。 */
        MITER,
        /** 顶点圆弧接缝（画布走廊示意，避免锐角刀尖）。 */
        ROUND
    }

    private OpenPolylineOffset() {
    }

    record OffsetResult(List<Vec2d> points, List<String> warnings) {
        static OffsetResult empty() {
            return new OffsetResult(List.of(), List.of());
        }
    }

    static OffsetResult offset(List<Vec2d> centerline, double distance) {
        return offset(centerline, distance, PolygonUtils.DEFAULT_MITER_LIMIT, JoinStyle.MITER);
    }

    static OffsetResult offsetRound(List<Vec2d> centerline, double distance) {
        return offset(centerline, distance, PolygonUtils.DEFAULT_MITER_LIMIT, JoinStyle.ROUND);
    }

    /**
     * 按顶点半宽偏移（画布叠加层可变宽走廊）。符号由 {@code distances} 正负决定侧向。
     * 角点取相邻切向平均法线，不做完整斜接；锐角处略窄于等距偏移，足够叠加深色示意。
     */
    static OffsetResult offsetVariable(List<Vec2d> centerline, List<Double> distances) {
        if (centerline == null
                || distances == null
                || centerline.size() < 2
                || distances.size() != centerline.size()) {
            return OffsetResult.empty();
        }
        List<Vec2d> sanitizedPoints = new ArrayList<>();
        List<Double> sanitizedDistances = new ArrayList<>();
        Vec2d previous = null;
        for (int i = 0; i < centerline.size(); i++) {
            Vec2d point = centerline.get(i);
            if (point == null || !PolygonUtils.isFinite(point)) {
                continue;
            }
            double distance = distances.get(i);
            if (previous != null && previous.distance(point) <= PolygonUtils.DEFAULT_EPSILON) {
                // 重复顶点保留较大半宽，避免阶梯跳变被吃掉
                if (!sanitizedDistances.isEmpty()
                        && Math.abs(distance) > Math.abs(sanitizedDistances.getLast())) {
                    sanitizedDistances.set(sanitizedDistances.size() - 1, distance);
                }
                continue;
            }
            sanitizedPoints.add(point.copy());
            sanitizedDistances.add(distance);
            previous = point;
        }
        if (sanitizedPoints.size() < 2) {
            return OffsetResult.empty();
        }

        List<Vec2d> output = new ArrayList<>(sanitizedPoints.size());
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < sanitizedPoints.size(); i++) {
            Vec2d tangent = tangentAt(sanitizedPoints, i);
            double length = tangent.length();
            if (length <= PolygonUtils.DEFAULT_EPSILON) {
                warnings.add("degenerate_vertex_tangent");
                output.add(sanitizedPoints.get(i).copy());
                continue;
            }
            Vec2d unit = tangent.multiply(1.0 / length);
            Vec2d offset = PolygonUtils.leftNormal(unit).multiply(sanitizedDistances.get(i));
            output.add(sanitizedPoints.get(i).add(offset));
        }
        return new OffsetResult(PolygonNormalizer.removeDuplicateVertices(output), List.copyOf(warnings));
    }

    private static Vec2d tangentAt(List<Vec2d> points, int index) {
        if (index <= 0) {
            return points.get(1).subtract(points.get(0));
        }
        if (index >= points.size() - 1) {
            return points.get(points.size() - 1).subtract(points.get(points.size() - 2));
        }
        Vec2d incoming = points.get(index).subtract(points.get(index - 1));
        Vec2d outgoing = points.get(index + 1).subtract(points.get(index));
        double inLen = incoming.length();
        double outLen = outgoing.length();
        if (inLen <= PolygonUtils.DEFAULT_EPSILON) {
            return outgoing;
        }
        if (outLen <= PolygonUtils.DEFAULT_EPSILON) {
            return incoming;
        }
        return incoming.multiply(1.0 / inLen).add(outgoing.multiply(1.0 / outLen));
    }

    static OffsetResult offset(List<Vec2d> centerline, double distance, double miterLimit) {
        return offset(centerline, distance, miterLimit, JoinStyle.MITER);
    }

    static OffsetResult offset(
            List<Vec2d> centerline,
            double distance,
            double miterLimit,
            JoinStyle joinStyle) {
        List<Vec2d> sanitized = sanitizeOpenCenterline(centerline);
        if (sanitized.size() < 2 || Math.abs(distance) <= PolygonUtils.DEFAULT_EPSILON) {
            return OffsetResult.empty();
        }
        if (!PolygonValidator.hasFiniteCoordinates(sanitized)) {
            return OffsetResult.empty();
        }

        JoinStyle style = joinStyle != null ? joinStyle : JoinStyle.MITER;
        List<OffsetEdge> offsetEdges = buildOffsetEdges(sanitized, distance);
        if (offsetEdges.isEmpty()) {
            return OffsetResult.empty();
        }

        List<Vec2d> output = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        output.add(offsetEdges.getFirst().start());

        for (int i = 0; i < offsetEdges.size() - 1; i++) {
            OffsetEdge current = offsetEdges.get(i);
            OffsetEdge nextEdge = offsetEdges.get(i + 1);
            Vec2d vertex = current.centerlineEnd();

            if (style == JoinStyle.ROUND) {
                appendRoundOrInnerBevel(
                    output, vertex, current, nextEdge, distance, warnings);
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
            if (PolygonUtils.isFinite(corner)) {
                output.add(corner);
            } else {
                appendBevelCorner(output, current.end(), nextEdge.start(), warnings);
            }
        }

        output.add(offsetEdges.getLast().end());

        List<Vec2d> cleaned = PolygonNormalizer.removeDuplicateVertices(output);
        if (PolygonValidator.hasOpenPolylineSelfIntersection(cleaned)) {
            warnings.add("inner_self_intersection");
            return offsetWithForcedBevel(sanitized, distance, miterLimit);
        }
        return new OffsetResult(cleaned, List.copyOf(warnings));
    }

    private static List<OffsetEdge> buildOffsetEdges(List<Vec2d> sanitized, double distance) {
        int segmentCount = sanitized.size() - 1;
        List<OffsetEdge> offsetEdges = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            Vec2d start = sanitized.get(i);
            Vec2d end = sanitized.get(i + 1);
            Vec2d direction = end.subtract(start);
            double length = direction.length();
            if (length <= PolygonUtils.DEFAULT_EPSILON) {
                continue;
            }
            Vec2d unit = direction.multiply(1.0 / length);
            Vec2d offsetVector = PolygonUtils.leftNormal(unit).multiply(distance);
            offsetEdges.add(new OffsetEdge(
                start.add(offsetVector),
                end.add(offsetVector),
                unit,
                end
            ));
        }
        return offsetEdges;
    }

    /**
     * 外角：半宽圆弧（去掉刀尖）；内角：斜接交点（bevel 在直角内会自交）。
     */
    private static void appendRoundOrInnerBevel(
            List<Vec2d> output,
            Vec2d vertex,
            OffsetEdge current,
            OffsetEdge next,
            double distance,
            List<String> warnings) {
        Vec2d inDir = current.direction();
        Vec2d outDir = next.direction();
        double inLen = inDir.length();
        double outLen = outDir.length();
        if (inLen <= PolygonUtils.DEFAULT_EPSILON || outLen <= PolygonUtils.DEFAULT_EPSILON) {
            appendBevelCorner(output, current.end(), next.start(), warnings);
            return;
        }
        Vec2d inUnit = inDir.multiply(1.0 / inLen);
        Vec2d outUnit = outDir.multiply(1.0 / outLen);
        double cross = inUnit.cross(outUnit);
        // 左转且左偏 / 右转且右偏 → 内角：用斜接，超限再 bevel
        if (cross * distance > PolygonUtils.DEFAULT_EPSILON) {
            Vec2d corner = intersectOffsetEdges(
                vertex,
                current,
                next,
                Math.abs(distance),
                PolygonUtils.DEFAULT_MITER_LIMIT,
                warnings);
            if (PolygonUtils.isFinite(corner)) {
                output.add(corner);
            } else {
                appendBevelCorner(output, current.end(), next.start(), warnings);
            }
            return;
        }
        if (!appendRoundCorner(output, vertex, current, next, Math.abs(distance), inUnit, outUnit, warnings)) {
            appendBevelCorner(output, current.end(), next.start(), warnings);
        }
    }

    /**
     * 以折角顶点为圆心、半宽为半径，按路径转角扫圆弧连接两偏移边。
     * @return false 表示退化，调用方应 bevel
     */
    private static boolean appendRoundCorner(
            List<Vec2d> output,
            Vec2d vertex,
            OffsetEdge current,
            OffsetEdge next,
            double absDistance,
            Vec2d inUnit,
            Vec2d outUnit,
            List<String> warnings) {
        if (!PolygonUtils.isFinite(vertex) || absDistance <= PolygonUtils.DEFAULT_EPSILON) {
            return false;
        }
        double turn = Math.atan2(inUnit.cross(outUnit), inUnit.dot(outUnit));
        if (Math.abs(turn) <= PolygonUtils.DEFAULT_EPSILON) {
            output.add(current.end());
            return true;
        }

        Vec2d from = current.end().subtract(vertex);
        if (from.lengthSquared() <= PolygonUtils.DEFAULT_EPSILON) {
            return false;
        }
        double a0 = Math.atan2(from.y, from.x);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(turn) / ROUND_JOIN_STEP_RADIANS));
        warnings.add("round_join");
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double angle = a0 + turn * t;
            output.add(new Vec2d(
                vertex.x + Math.cos(angle) * absDistance,
                vertex.y + Math.sin(angle) * absDistance));
        }
        return true;
    }

    private static OffsetResult offsetWithForcedBevel(
            List<Vec2d> sanitized,
            double distance,
            double miterLimit) {
        List<Vec2d> output = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.add("forced_bevel_fallback");

        int segmentCount = sanitized.size() - 1;
        List<OffsetEdge> offsetEdges = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            Vec2d start = sanitized.get(i);
            Vec2d end = sanitized.get(i + 1);
            Vec2d direction = end.subtract(start);
            double length = direction.length();
            if (length <= PolygonUtils.DEFAULT_EPSILON) {
                continue;
            }
            Vec2d unit = direction.multiply(1.0 / length);
            Vec2d offsetVector = PolygonUtils.leftNormal(unit).multiply(distance);
            offsetEdges.add(new OffsetEdge(
                start.add(offsetVector),
                end.add(offsetVector),
                unit,
                end
            ));
        }
        if (offsetEdges.isEmpty()) {
            return OffsetResult.empty();
        }

        output.add(offsetEdges.getFirst().start());
        for (int i = 0; i < offsetEdges.size() - 1; i++) {
            OffsetEdge current = offsetEdges.get(i);
            OffsetEdge nextEdge = offsetEdges.get(i + 1);
            appendBevelCorner(output, current.end(), nextEdge.start(), warnings);
        }
        output.add(offsetEdges.getLast().end());
        return new OffsetResult(PolygonNormalizer.removeDuplicateVertices(output), List.copyOf(warnings));
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
            double absDistance,
            double miterLimit,
            List<String> warnings) {
        Vec2d intersection = intersectInfiniteLines(
            current.start(), current.direction(),
            next.start(), next.direction()
        );
        if (PolygonUtils.isFinite(intersection)) {
            double miterLength = intersection.distance(vertex);
            if (miterLength <= absDistance * miterLimit + PolygonUtils.DEFAULT_EPSILON) {
                return intersection;
            }
            warnings.add("miter_limit_exceeded");
            return null;
        }
        warnings.add("parallel_offset_edges");
        return null;
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

    static List<Vec2d> sanitizeOpenCenterline(List<Vec2d> centerline) {
        if (centerline == null || centerline.isEmpty()) {
            return List.of();
        }
        List<Vec2d> result = new ArrayList<>();
        Vec2d previous = null;
        for (Vec2d point : centerline) {
            if (!PolygonUtils.isFinite(point)) {
                continue;
            }
            if (previous != null && previous.distance(point) <= PolygonUtils.DEFAULT_EPSILON) {
                continue;
            }
            result.add(point.copy());
            previous = point;
        }
        return result;
    }

    private record OffsetEdge(Vec2d start, Vec2d end, Vec2d direction, Vec2d centerlineEnd) {
    }
}
