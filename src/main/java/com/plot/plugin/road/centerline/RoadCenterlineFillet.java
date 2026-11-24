package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 开放折线内角圆角（道路中心线 PI 圆角）。
 */
final class RoadCenterlineFillet {

    private static final double EPSILON = 1e-9;
    private static final int ARC_SEGMENTS = 12;

    private RoadCenterlineFillet() {
    }

    static List<Vec2d> filletVertex(List<Vec2d> points, int vertexIndex, double radius) {
        if (points == null || points.size() < 3 || radius <= EPSILON) {
            return null;
        }
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return null;
        }

        Vec2d prev = points.get(vertexIndex - 1);
        Vec2d corner = points.get(vertexIndex);
        Vec2d next = points.get(vertexIndex + 1);

        Vec2d dir1 = normalize(prev.subtract(corner));
        Vec2d dir2 = normalize(next.subtract(corner));
        if (dir1.lengthSquared() < EPSILON || dir2.lengthSquared() < EPSILON) {
            return null;
        }

        double dot = Math.clamp(dir1.x * dir2.x + dir1.y * dir2.y, -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 0.05 || angle > Math.PI - 0.1) {
            return null;
        }

        FilletLocalGeometry local = computeLocalGeometry(prev, corner, next, radius);
        if (local == null) {
            return null;
        }

        List<Vec2d> result = new ArrayList<>();
        for (int i = 0; i < vertexIndex; i++) {
            result.add(points.get(i).copy());
        }
        if (result.isEmpty() || result.getLast().distance(local.trim1()) > EPSILON) {
            result.add(local.trim1());
        } else {
            result.set(result.size() - 1, local.trim1());
        }
        result.addAll(local.arcInterior());
        result.add(local.trim2());
        for (int i = vertexIndex + 1; i < points.size(); i++) {
            result.add(points.get(i).copy());
        }
        return result.size() >= 2 ? result : null;
    }

    /**
     * 当前折点可构造的最大圆角半径（由较短腿与半角决定）；无法构造时返回 0。
     */
    static double maxFeasibleRadius(List<Vec2d> points, int vertexIndex) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return 0.0;
        }
        Vec2d prev = points.get(vertexIndex - 1);
        Vec2d corner = points.get(vertexIndex);
        Vec2d next = points.get(vertexIndex + 1);
        Vec2d dir1 = normalize(prev.subtract(corner));
        Vec2d dir2 = normalize(next.subtract(corner));
        if (dir1.lengthSquared() < EPSILON || dir2.lengthSquared() < EPSILON) {
            return 0.0;
        }
        double dot = Math.clamp(dir1.x * dir2.x + dir1.y * dir2.y, -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 0.05 || angle > Math.PI - 0.1) {
            return 0.0;
        }
        double tanHalf = Math.tan(angle / 2.0);
        if (tanHalf <= EPSILON) {
            return 0.0;
        }
        double maxTangent = Math.min(prev.distance(corner), next.distance(corner)) - EPSILON;
        if (maxTangent <= EPSILON) {
            return 0.0;
        }
        return maxTangent * tanHalf;
    }

    /**
     * 仅返回圆角局部几何：切点 trim1 → 弧采样 → trim2（不含折线其余部分）。
     * 供预览绘制，避免把整条新折线叠画成乱线。
     */
    static List<Vec2d> filletLocalArc(List<Vec2d> points, int vertexIndex, double radius) {
        if (points == null || points.size() < 3 || radius <= EPSILON) {
            return null;
        }
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return null;
        }
        FilletLocalGeometry local = computeLocalGeometry(
            points.get(vertexIndex - 1),
            points.get(vertexIndex),
            points.get(vertexIndex + 1),
            radius);
        if (local == null) {
            return null;
        }
        List<Vec2d> arc = new ArrayList<>(2 + local.arcInterior().size());
        arc.add(local.trim1());
        arc.addAll(local.arcInterior());
        arc.add(local.trim2());
        return arc;
    }

    private record FilletLocalGeometry(Vec2d trim1, List<Vec2d> arcInterior, Vec2d trim2) {
    }

    /**
     * 按腿长钳制切线长后，必须用有效半径重建圆心，否则切点不在圆弧上会产生乱线。
     */
    private static FilletLocalGeometry computeLocalGeometry(
            Vec2d prev, Vec2d corner, Vec2d next, double radius) {
        Vec2d dir1 = normalize(prev.subtract(corner));
        Vec2d dir2 = normalize(next.subtract(corner));
        if (dir1.lengthSquared() < EPSILON || dir2.lengthSquared() < EPSILON) {
            return null;
        }

        double dot = Math.clamp(dir1.x * dir2.x + dir1.y * dir2.y, -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 0.05 || angle > Math.PI - 0.1) {
            return null;
        }

        double len1 = prev.distance(corner);
        double len2 = next.distance(corner);
        double halfAngle = angle / 2.0;
        double tanHalf = Math.tan(halfAngle);
        if (tanHalf <= EPSILON) {
            return null;
        }
        double tangentOffset = Math.min(radius / tanHalf, Math.min(len1, len2) - EPSILON);
        if (tangentOffset <= EPSILON) {
            return null;
        }
        // 腿长不足时缩小半径，保持切点落在圆弧上。
        double effectiveRadius = tangentOffset * tanHalf;
        if (effectiveRadius <= EPSILON) {
            return null;
        }
        double centerOffset = effectiveRadius / Math.sin(halfAngle);
        Vec2d trim1 = corner.add(dir1.multiply(tangentOffset));
        Vec2d trim2 = corner.add(dir2.multiply(tangentOffset));
        Vec2d bisector = normalize(dir1.add(dir2));
        if (bisector.lengthSquared() < EPSILON) {
            return null;
        }
        Vec2d center = corner.add(bisector.multiply(centerOffset));

        // 始终按 trim1 → trim2 采样，取不超过 π 的短弧（圆心在角平分线上，圆角弧必为短弧）。
        double startAngle = Math.atan2(trim1.y - center.y, trim1.x - center.x);
        double endAngle = Math.atan2(trim2.y - center.y, trim2.x - center.x);
        double sweep = endAngle - startAngle;
        while (sweep > Math.PI) {
            sweep -= 2.0 * Math.PI;
        }
        while (sweep < -Math.PI) {
            sweep += 2.0 * Math.PI;
        }

        List<Vec2d> arcInterior = new ArrayList<>(ARC_SEGMENTS);
        for (int i = 1; i < ARC_SEGMENTS; i++) {
            double t = (double) i / ARC_SEGMENTS;
            double angleAt = startAngle + sweep * t;
            arcInterior.add(new Vec2d(
                center.x + effectiveRadius * Math.cos(angleAt),
                center.y + effectiveRadius * Math.sin(angleAt)
            ));
        }
        return new FilletLocalGeometry(trim1, List.copyOf(arcInterior), trim2);
    }

    private static Vec2d normalize(Vec2d vector) {
        double lengthSquared = vector.lengthSquared();
        if (lengthSquared < EPSILON) {
            return new Vec2d(0, 0);
        }
        return vector.normalize();
    }
}
