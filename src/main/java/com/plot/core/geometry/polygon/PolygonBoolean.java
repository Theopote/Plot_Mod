package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.Polygon;

import java.util.List;

/**
 * 多边形点包含与基础布尔关系（Phase 1：包含 / 线段相交）。
 */
public final class PolygonBoolean {
    private PolygonBoolean() {
    }

    public static boolean contains(List<Vec2d> outerRing, Vec2d point) {
        if (point == null || outerRing == null || outerRing.size() < 3) {
            return false;
        }
        return new Polygon(PolygonRegionUtils.copyPoints(outerRing)).contains(point);
    }

    public static boolean contains(List<Vec2d> outerRing, List<List<Vec2d>> holes, Vec2d point) {
        return PolygonRegionUtils.containsPoint(outerRing, holes, point);
    }

    public static boolean contains(RegionGeometry geometry, Vec2d point) {
        return PolygonRegionUtils.containsPoint(geometry, point);
    }

    public static boolean intersectsSegment(List<Vec2d> polygon, Vec2d start, Vec2d end) {
        if (polygon == null || polygon.size() < 3 || start == null || end == null) {
            return false;
        }
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = polygon.get(i);
            Vec2d b = polygon.get((i + 1) % n);
            if (PolygonValidator.segmentsIntersectProperly(a, b, start, end, PolygonUtils.DEFAULT_EPSILON)) {
                return true;
            }
        }
        return contains(polygon, start) || contains(polygon, end);
    }

    public static boolean intersects(List<Vec2d> left, List<Vec2d> right) {
        if (left == null || right == null || left.size() < 3 || right.size() < 3) {
            return false;
        }
        int leftCount = left.size();
        for (int i = 0; i < leftCount; i++) {
            Vec2d a = left.get(i);
            Vec2d b = left.get((i + 1) % leftCount);
            if (intersectsSegment(right, a, b)) {
                return true;
            }
        }
        return contains(left, right.getFirst()) || contains(right, left.getFirst());
    }
}
