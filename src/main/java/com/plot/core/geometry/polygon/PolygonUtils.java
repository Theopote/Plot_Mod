package com.plot.core.geometry.polygon;

/**
 * 多边形内核共享常量与基础度量。
 */
public final class PolygonUtils {
    public static final double DEFAULT_EPSILON = 1e-9;
    public static final double CLOSE_RING_EPSILON = 1e-6;
    public static final double MIN_AREA = 1e-6;
    public static final double DEFAULT_MITER_LIMIT = 4.0;

    private PolygonUtils() {
    }

    public enum Winding {
        COUNTER_CLOCKWISE,
        CLOCKWISE
    }

    public static double signedArea(java.util.List<com.plot.api.geometry.Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            com.plot.api.geometry.Vec2d a = points.get(i);
            com.plot.api.geometry.Vec2d b = points.get((i + 1) % n);
            area += a.x * b.y - b.x * a.y;
        }
        return area / 2.0;
    }

    public static double absoluteArea(java.util.List<com.plot.api.geometry.Vec2d> points) {
        return Math.abs(signedArea(points));
    }

    public static boolean isCounterClockwise(java.util.List<com.plot.api.geometry.Vec2d> points) {
        return signedArea(points) > DEFAULT_EPSILON;
    }

    public static com.plot.api.geometry.Vec2d leftNormal(com.plot.api.geometry.Vec2d direction) {
        if (direction == null || direction.lengthSquared() < DEFAULT_EPSILON) {
            return new com.plot.api.geometry.Vec2d(0, 1);
        }
        com.plot.api.geometry.Vec2d normalized = direction.normalize();
        return new com.plot.api.geometry.Vec2d(-normalized.y, normalized.x);
    }

    public static boolean isFinite(com.plot.api.geometry.Vec2d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y);
    }
}
