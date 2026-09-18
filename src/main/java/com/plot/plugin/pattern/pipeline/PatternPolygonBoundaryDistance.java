package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;

import java.util.List;

/**
 * 采样点到多边形边界（外环 + 孔洞环）的最短距离。
 * <p>
 * 供回字边、边框带、退距铺装等沿真实轮廓计算使用。
 */
public final class PatternPolygonBoundaryDistance {
    private PatternPolygonBoundaryDistance() {
    }

    public static double distanceToPolygonBoundary(
            double x,
            double z,
            List<Vec2d> outerRing,
            List<List<Vec2d>> holes) {
        return distanceToPolygonBoundary(x, z, outerRing, holes, null);
    }

    public static double distanceToPolygonBoundary(
            double x,
            double z,
            List<Vec2d> outerRing,
            List<List<Vec2d>> holes,
            PolygonRegionUtils.RectBounds boundsFallback) {
        if (outerRing == null || outerRing.size() < 2) {
            return distanceToAxisAlignedBounds(x, z, boundsFallback);
        }
        Vec2d point = new Vec2d(x, z);
        double min = distanceToRingBoundary(point, outerRing);
        if (holes != null) {
            for (List<Vec2d> hole : holes) {
                min = Math.min(min, distanceToRingBoundary(point, hole));
            }
        }
        return min;
    }

    public static double distanceToRingBoundary(Vec2d point, List<Vec2d> ring) {
        if (point == null || ring == null || ring.size() < 2) {
            return Double.MAX_VALUE;
        }
        double min = Double.MAX_VALUE;
        int count = ring.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = ring.get(i);
            Vec2d end = ring.get((i + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            min = Math.min(min, distancePointToSegment(point, start, end));
        }
        return min;
    }

    private static double distanceToAxisAlignedBounds(
            double x,
            double z,
            PolygonRegionUtils.RectBounds bounds) {
        if (bounds == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(
            Math.min(x - bounds.minX(), bounds.maxX() - x),
            Math.min(z - bounds.minZ(), bounds.maxZ() - z)));
    }

    private static double distancePointToSegment(Vec2d point, Vec2d start, Vec2d end) {
        double dx = end.x - start.x;
        double dz = end.y - start.y;
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1e-12) {
            return point.distance(start);
        }
        double t = ((point.x - start.x) * dx + (point.y - start.y) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = start.x + t * dx;
        double closestZ = start.y + t * dz;
        double px = point.x - closestX;
        double pz = point.y - closestZ;
        return Math.sqrt(px * px + pz * pz);
    }
}
