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

        double dot = Math.max(-1.0, Math.min(1.0, dir1.x * dir2.x + dir1.y * dir2.y));
        double angle = Math.acos(dot);
        if (angle < 0.05 || angle > Math.PI - 0.1) {
            return null;
        }

        double len1 = prev.distance(corner);
        double len2 = next.distance(corner);
        double halfAngle = angle / 2.0;
        double tangentOffset = Math.min(radius / Math.tan(halfAngle), Math.min(len1, len2) - EPSILON);
        if (tangentOffset <= EPSILON) {
            return null;
        }

        double centerOffset = radius / Math.sin(halfAngle);
        Vec2d trim1 = corner.add(dir1.multiply(tangentOffset));
        Vec2d trim2 = corner.add(dir2.multiply(tangentOffset));
        Vec2d bisector = normalize(dir1.add(dir2));
        if (bisector.lengthSquared() < EPSILON) {
            return null;
        }
        Vec2d center = corner.add(bisector.multiply(centerOffset));

        double startAngle = Math.atan2(trim1.y - center.y, trim1.x - center.x);
        double endAngle = Math.atan2(trim2.y - center.y, trim2.x - center.x);
        double cross = dir1.x * dir2.y - dir1.y * dir2.x;
        if (cross < 0.0) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
        }
        while (endAngle <= startAngle) {
            endAngle += 2.0 * Math.PI;
        }
        if (endAngle - startAngle > Math.PI) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
            while (endAngle <= startAngle) {
                endAngle += 2.0 * Math.PI;
            }
        }

        List<Vec2d> result = new ArrayList<>();
        for (int i = 0; i < vertexIndex; i++) {
            result.add(points.get(i).copy());
        }
        if (result.isEmpty() || result.getLast().distance(trim1) > EPSILON) {
            result.add(trim1);
        } else {
            result.set(result.size() - 1, trim1);
        }

        double sweep = endAngle - startAngle;
        for (int i = 1; i < ARC_SEGMENTS; i++) {
            double t = (double) i / ARC_SEGMENTS;
            double angleAt = startAngle + sweep * t;
            result.add(new Vec2d(
                center.x + radius * Math.cos(angleAt),
                center.y + radius * Math.sin(angleAt)
            ));
        }
        result.add(trim2);
        for (int i = vertexIndex + 1; i < points.size(); i++) {
            result.add(points.get(i).copy());
        }
        return result.size() >= 2 ? result : null;
    }

    private static Vec2d normalize(Vec2d vector) {
        double lengthSquared = vector.lengthSquared();
        if (lengthSquared < EPSILON) {
            return new Vec2d(0, 0);
        }
        return vector.normalize();
    }
}
