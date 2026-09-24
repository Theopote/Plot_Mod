package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单多边形校验：有限坐标、最小面积、自交检测。
 */
public final class PolygonValidator {
    private PolygonValidator() {
    }

    public record ValidationResult(boolean valid, List<String> issues) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(String issue) {
            return new ValidationResult(false, List.of(issue));
        }

        public static ValidationResult fail(List<String> issues) {
            return new ValidationResult(false, List.copyOf(issues));
        }
    }

    public static ValidationResult validateSimplePolygon(List<Vec2d> points) {
        return validateSimplePolygon(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static ValidationResult validateSimplePolygon(List<Vec2d> points, double epsilon) {
        List<String> issues = new ArrayList<>();
        if (points == null || points.isEmpty()) {
            issues.add("empty");
            return ValidationResult.fail(issues);
        }
        if (points.size() < 3) {
            issues.add("too_few_vertices");
        }
        if (!hasFiniteCoordinates(points)) {
            issues.add("non_finite_coordinates");
        }
        if (hasDuplicateVertices(points, epsilon)) {
            issues.add("duplicate_vertices");
        }
        double area = PolygonUtils.absoluteArea(points);
        if (area <= PolygonUtils.MIN_AREA) {
            issues.add("degenerate_area");
        }
        if (hasSelfIntersection(points, epsilon)) {
            issues.add("self_intersection");
        }
        return issues.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(issues);
    }

    public static boolean hasFiniteCoordinates(List<Vec2d> points) {
        if (points == null) {
            return false;
        }
        for (Vec2d point : points) {
            if (!PolygonUtils.isFinite(point)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasDuplicateVertices(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 2) {
            return false;
        }
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = points.get(i);
            for (int j = i + 1; j < n; j++) {
                if (a.distance(points.get(j)) <= epsilon) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasSelfIntersection(List<Vec2d> points) {
        return hasSelfIntersection(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static boolean hasSelfIntersection(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 4) {
            return false;
        }
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a1 = points.get(i);
            Vec2d a2 = points.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (j == i || j == (i + 1) % n || (j + 1) % n == i) {
                    continue;
                }
                Vec2d b1 = points.get(j);
                Vec2d b2 = points.get((j + 1) % n);
                if (segmentsIntersectProperly(a1, a2, b1, b2, epsilon)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 开放折线自交：非相邻线段发生真交（不含首尾闭合）。
     */
    public static boolean hasOpenPolylineSelfIntersection(List<Vec2d> points) {
        return hasOpenPolylineSelfIntersection(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static boolean hasOpenPolylineSelfIntersection(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 4) {
            return false;
        }
        int segmentCount = points.size() - 1;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d a1 = points.get(i);
            Vec2d a2 = points.get(i + 1);
            for (int j = i + 2; j < segmentCount; j++) {
                Vec2d b1 = points.get(j);
                Vec2d b2 = points.get(j + 1);
                if (segmentsIntersectProperly(a1, a2, b1, b2, epsilon)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 开放折线自重叠：非相邻线段共线且投影区间有实质重叠（不含端点相接）。
     */
    public static boolean hasOpenPolylineSelfOverlap(List<Vec2d> points) {
        return hasOpenPolylineSelfOverlap(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static boolean hasOpenPolylineSelfOverlap(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 3) {
            return false;
        }
        int segmentCount = points.size() - 1;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d a1 = points.get(i);
            Vec2d a2 = points.get(i + 1);
            for (int j = i + 2; j < segmentCount; j++) {
                Vec2d b1 = points.get(j);
                Vec2d b2 = points.get(j + 1);
                if (segmentsOverlapCollinearly(a1, a2, b1, b2, epsilon)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean segmentsIntersectProperly(
            Vec2d a1, Vec2d a2, Vec2d b1, Vec2d b2, double epsilon) {
        double o1 = orientation(a1, a2, b1, epsilon);
        double o2 = orientation(a1, a2, b2, epsilon);
        double o3 = orientation(b1, b2, a1, epsilon);
        double o4 = orientation(b1, b2, a2, epsilon);

        if (o1 * o2 < 0 && o3 * o4 < 0) {
            return true;
        }
        return false;
    }

    private static boolean segmentsOverlapCollinearly(
            Vec2d a1, Vec2d a2, Vec2d b1, Vec2d b2, double epsilon) {
        if (orientation(a1, a2, b1, epsilon) != 0.0 || orientation(a1, a2, b2, epsilon) != 0.0) {
            return false;
        }
        double dx = a2.x - a1.x;
        double dy = a2.y - a1.y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= epsilon * epsilon) {
            return false;
        }
        double invLengthSquared = 1.0 / lengthSquared;
        double minA = 0.0;
        double maxA = Math.sqrt(lengthSquared);
        double tB1 = ((b1.x - a1.x) * dx + (b1.y - a1.y) * dy) * invLengthSquared;
        double tB2 = ((b2.x - a1.x) * dx + (b2.y - a1.y) * dy) * invLengthSquared;
        double minB = Math.min(tB1, tB2) * maxA;
        double maxB = Math.max(tB1, tB2) * maxA;
        double overlap = Math.min(maxA, maxB) - Math.max(minA, minB);
        return overlap > epsilon;
    }

    private static double orientation(Vec2d a, Vec2d b, Vec2d c, double epsilon) {
        double value = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        if (Math.abs(value) <= epsilon) {
            return 0.0;
        }
        return value;
    }
}
