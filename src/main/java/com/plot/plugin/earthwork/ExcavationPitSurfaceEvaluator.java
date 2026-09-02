package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;

import java.util.List;

/**
 * 基坑分区设计面：坑底平台 + 内侧放坡带。
 */
public final class ExcavationPitSurfaceEvaluator {
    private ExcavationPitSurfaceEvaluator() {
    }

    public static int evaluateTargetY(
            Vec2d canvasCenter,
            List<Vec2d> pitPolygon,
            int bottomElevation,
            int workingMarginBlocks,
            int slopePitchRatio) {
        if (canvasCenter == null || pitPolygon == null || pitPolygon.size() < 3) {
            return bottomElevation;
        }
        if (!EarthworkGeometryUtils.containsCanvasPoint(pitPolygon, canvasCenter)) {
            return bottomElevation;
        }
        double distToEdge = distanceToPolygonBoundary(pitPolygon, canvasCenter);
        int workingMargin = Math.max(0, workingMarginBlocks);
        int pitch = Math.max(1, slopePitchRatio);
        if (distToEdge >= workingMargin) {
            return bottomElevation;
        }
        int rise = (int) Math.ceil((workingMargin - distToEdge) / (double) pitch);
        return bottomElevation + rise;
    }

    static double distanceToPolygonBoundary(List<Vec2d> polygon, Vec2d point) {
        if (polygon == null || polygon.size() < 2 || point == null) {
            return 0.0;
        }
        double minDistance = Double.MAX_VALUE;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            minDistance = Math.min(minDistance, GeometryUtils.pointToSegmentDistance(point, start, end));
        }
        return minDistance;
    }
}
