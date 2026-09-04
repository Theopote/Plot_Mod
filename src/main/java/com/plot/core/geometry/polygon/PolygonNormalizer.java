package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 多边形顶点规范化：去重、去共线、统一绕序。
 */
public final class PolygonNormalizer {
    private PolygonNormalizer() {
    }

    /**
     * 与 {@link com.plot.core.geometry.PolygonRegionUtils#normalizeRegionOutline} 语义一致。
     */
    public static List<Vec2d> normalizeOutline(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<Vec2d> sanitized = removeDuplicateVertices(points, PolygonUtils.DEFAULT_EPSILON);
        if (sanitized.size() >= 2
            && sanitized.getFirst().distance(sanitized.getLast()) <= PolygonUtils.CLOSE_RING_EPSILON) {
            sanitized = new ArrayList<>(sanitized);
            sanitized.remove(sanitized.size() - 1);
        }
        sanitized = removeCollinearVertices(sanitized, PolygonUtils.DEFAULT_EPSILON);
        return sanitized.size() >= 3 ? sanitized : List.of();
    }

    public static List<Vec2d> removeDuplicateVertices(List<Vec2d> points) {
        return removeDuplicateVertices(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static List<Vec2d> removeDuplicateVertices(List<Vec2d> points, double epsilon) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<Vec2d> sanitized = new ArrayList<>();
        Vec2d previous = null;
        for (Vec2d point : points) {
            if (point == null || !PolygonUtils.isFinite(point)) {
                continue;
            }
            if (previous != null && previous.distance(point) <= epsilon) {
                continue;
            }
            sanitized.add(point.copy());
            previous = sanitized.getLast();
        }
        return sanitized;
    }

    public static List<Vec2d> removeCollinearVertices(List<Vec2d> points) {
        return removeCollinearVertices(points, PolygonUtils.DEFAULT_EPSILON);
    }

    public static List<Vec2d> removeCollinearVertices(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 3) {
            return points == null ? List.of() : copy(points);
        }

        List<Vec2d> working = copy(points);
        boolean removed;
        do {
            removed = false;
            if (working.size() < 3) {
                break;
            }
            List<Vec2d> next = new ArrayList<>();
            int n = working.size();
            for (int i = 0; i < n; i++) {
                Vec2d prev = working.get((i - 1 + n) % n);
                Vec2d current = working.get(i);
                Vec2d nextPoint = working.get((i + 1) % n);
                if (isCollinear(prev, current, nextPoint, epsilon)) {
                    removed = true;
                    continue;
                }
                next.add(current);
            }
            working = next;
        } while (removed && working.size() >= 3);

        return working.size() >= 3 ? working : List.of();
    }

    public static List<Vec2d> normalizeWinding(List<Vec2d> points, PolygonUtils.Winding winding) {
        if (points == null || points.size() < 3 || winding == null) {
            return points == null ? List.of() : copy(points);
        }
        List<Vec2d> copy = copy(points);
        boolean ccw = PolygonUtils.isCounterClockwise(copy);
        if (winding == PolygonUtils.Winding.COUNTER_CLOCKWISE && !ccw) {
            copy = reverse(copy);
        } else if (winding == PolygonUtils.Winding.CLOCKWISE && ccw) {
            copy = reverse(copy);
        }
        return copy;
    }

    /**
     * 完整规范化：去重 → 去共线 → 可选绕序。
     */
    public static List<Vec2d> normalize(List<Vec2d> points, PolygonUtils.Winding winding) {
        List<Vec2d> outline = normalizeOutline(points);
        if (outline.size() < 3) {
            return List.of();
        }
        if (winding == null) {
            return outline;
        }
        return normalizeWinding(outline, winding);
    }

    private static boolean isCollinear(Vec2d a, Vec2d b, Vec2d c, double epsilon) {
        Vec2d ab = b.subtract(a);
        Vec2d bc = c.subtract(b);
        return Math.abs(ab.cross(bc)) <= epsilon
            && Math.max(ab.lengthSquared(), bc.lengthSquared()) > epsilon;
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

    private static List<Vec2d> reverse(List<Vec2d> points) {
        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i).copy());
        }
        return reversed;
    }
}
