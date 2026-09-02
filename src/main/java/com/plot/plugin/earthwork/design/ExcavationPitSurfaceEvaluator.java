package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.api.geometry.Vec2d;

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
        double distToEdge = EarthworkGeometryUtils.distanceToPolygonBoundary(pitPolygon, canvasCenter);
        int workingMargin = Math.max(0, workingMarginBlocks);
        int pitch = Math.max(1, slopePitchRatio);
        if (distToEdge >= workingMargin) {
            return bottomElevation;
        }
        int rise = (int) Math.ceil((workingMargin - distToEdge) / (double) pitch);
        return bottomElevation + rise;
    }
}
