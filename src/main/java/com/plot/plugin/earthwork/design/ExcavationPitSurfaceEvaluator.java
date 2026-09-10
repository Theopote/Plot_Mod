package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.api.geometry.Vec2d;

import java.util.List;
import java.util.Objects;

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
            int slopePitchRatio,
            EarthworkCanvasScale canvasScale) {
        if (canvasCenter == null || pitPolygon == null || pitPolygon.size() < 3) {
            return bottomElevation;
        }
        if (!EarthworkGeometryUtils.containsCanvasPoint(pitPolygon, canvasCenter)) {
            return bottomElevation;
        }
        EarthworkCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        double distCanvas = EarthworkGeometryUtils.distanceToPolygonBoundary(pitPolygon, canvasCenter);
        Vec2d inward = inwardDirection(pitPolygon, canvasCenter);
        double distBlocks = scale.canvasToBlocks(distCanvas, canvasCenter, inward);
        int workingMargin = Math.max(0, workingMarginBlocks);
        int pitch = Math.max(1, slopePitchRatio);
        if (distBlocks >= workingMargin) {
            return bottomElevation;
        }
        int rise = (int) Math.ceil((workingMargin - distBlocks) / (double) pitch);
        return bottomElevation + rise;
    }

    private static Vec2d inwardDirection(List<Vec2d> polygon, Vec2d point) {
        BoundaryProximity proximity = nearestEdge(polygon, point);
        Vec2d delta = point.subtract(proximity.closestPoint);
        if (delta.lengthSquared() < 1e-12) {
            return new Vec2d(1, 0);
        }
        return delta;
    }

    private static BoundaryProximity nearestEdge(List<Vec2d> polygon, Vec2d point) {
        double minDistance = Double.MAX_VALUE;
        Vec2d closestPoint = point;
        int count = polygon.size();
        for (int edgeIndex = 0; edgeIndex < count; edgeIndex++) {
            Vec2d start = polygon.get(edgeIndex);
            Vec2d end = polygon.get((edgeIndex + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            Vec2d projected = com.plot.core.geometry.GeometryUtils.projectPointOnLine(point, start, end);
            Vec2d segment = end.subtract(start);
            double t = point.subtract(start).dot(segment) / Math.max(segment.dot(segment), 1e-12);
            t = Math.max(0.0, Math.min(1.0, t));
            projected = start.add(segment.multiply(t));
            double distance = point.distance(projected);
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = projected;
            }
        }
        return new BoundaryProximity(closestPoint);
    }

    private record BoundaryProximity(Vec2d closestPoint) {
    }
}
