package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路几何工具（与 Shape / RoadGenerator 解耦的纯几何运算）
 */
public final class RoadGeometryUtils {
    private RoadGeometryUtils() {
    }

    public static List<Vec2d> extractShapePoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        if (shape instanceof PolylineShape || shape instanceof FreeDrawPath) {
            return shape.getPoints();
        }
        if (shape instanceof BezierCurveShape bezier) {
            return sampleBezierCurve(bezier);
        }
        List<Vec2d> endpoints = shape.getEndpoints();
        return endpoints != null ? new ArrayList<>(endpoints) : List.of();
    }

    /**
     * 获取贝塞尔曲线上的采样点（使用 {@link BezierCurveShape#getCurvePoints()}，而非控制手柄序列）
     */
    public static List<Vec2d> sampleBezierCurve(BezierCurveShape curve) {
        if (curve == null) {
            return List.of();
        }
        return new ArrayList<>(curve.getCurvePoints());
    }

    public static double calculatePathLength(List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distance(points.get(i + 1));
        }
        return total;
    }

    /**
     * 沿路径按弧长等间距采样
     */
    public static List<Vec2d> sampleAlongPath(List<Vec2d> pathPoints, double spacing, double skipNearEndsDistance) {
        List<Vec2d> result = new ArrayList<>();
        if (pathPoints == null || pathPoints.size() < 2 || spacing <= 0) {
            return result;
        }

        double totalLength = calculatePathLength(pathPoints);
        if (totalLength <= 2 * skipNearEndsDistance) {
            return result;
        }

        double distance = skipNearEndsDistance;
        while (distance <= totalLength - skipNearEndsDistance) {
            Vec2d point = pointAtDistance(pathPoints, distance);
            if (point != null) {
                result.add(point);
            }
            distance += spacing;
        }
        return result;
    }

    public static Vec2d pointAtDistance(List<Vec2d> points, double targetDistance) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (targetDistance <= 0) {
            return points.getFirst().copy();
        }

        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            double segmentLength = start.distance(end);
            if (segmentLength < 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= targetDistance) {
                double t = (targetDistance - accumulated) / segmentLength;
                return start.lerp(end, t);
            }
            accumulated += segmentLength;
        }
        return points.getLast().copy();
    }

    /**
     * 在折线上找到距给定点最近的索引位置（用于打断）
     */
    public static int findNearestSegmentIndex(List<Vec2d> points, Vec2d target) {
        if (points == null || points.size() < 2 || target == null) {
            return -1;
        }

        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            Vec2d projected = projectPointOnSegment(start, end, target);
            double distance = projected.distance(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public static Vec2d projectPointOnSegment(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d direction = end.subtract(start);
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < 1e-12) {
            return start.copy();
        }
        double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(direction) / lengthSquared));
        return start.lerp(end, t);
    }

    public static List<List<Vec2d>> splitPolylineAt(List<Vec2d> points, Vec2d splitPoint, double tolerance) {
        if (points == null || points.size() < 2 || splitPoint == null) {
            return singlePart(points);
        }

        if (splitPoint.distance(points.getFirst()) <= tolerance) {
            return singlePart(points);
        }
        if (splitPoint.distance(points.getLast()) <= tolerance) {
            return singlePart(points);
        }

        int segmentIndex = findNearestSegmentIndex(points, splitPoint);
        if (segmentIndex < 0) {
            return singlePart(points);
        }

        Vec2d projected = projectPointOnSegment(
            points.get(segmentIndex),
            points.get(segmentIndex + 1),
            splitPoint
        );

        List<Vec2d> firstPart = new ArrayList<>();
        for (int i = 0; i <= segmentIndex; i++) {
            firstPart.add(points.get(i).copy());
        }
        firstPart.add(projected);

        List<Vec2d> secondPart = new ArrayList<>();
        secondPart.add(projected);
        for (int i = segmentIndex + 1; i < points.size(); i++) {
            secondPart.add(points.get(i).copy());
        }

        List<List<Vec2d>> parts = new ArrayList<>(2);
        parts.add(firstPart);
        parts.add(secondPart);
        return parts;
    }

    private static List<List<Vec2d>> singlePart(List<Vec2d> points) {
        List<List<Vec2d>> parts = new ArrayList<>(1);
        parts.add(copyPoints(points));
        return parts;
    }

    public static boolean pointsNear(Vec2d a, Vec2d b, double tolerance) {
        return a != null && b != null && a.distance(b) <= tolerance;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point.copy());
            }
        }
        return copy;
    }
}
