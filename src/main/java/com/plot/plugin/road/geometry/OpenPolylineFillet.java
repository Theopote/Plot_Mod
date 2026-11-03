package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 开放折线顶点圆角（画布走廊预处理专用）。
 * <p>
 * 急折中心线先圆角再等距偏移，才能在两侧保持半宽，又避免锐角偏移自交。
 */
final class OpenPolylineFillet {
    private static final double EPSILON = PolygonUtils.DEFAULT_EPSILON;
    private static final int ARC_SEGMENTS = 10;
    /** 转角达到该值时做叠加层圆角（约 40°），覆盖截图里的 Z 折，避免急折半宽被挤窄。 */
    private static final double FILLET_TURN_RADIANS = Math.toRadians(40.0);

    private OpenPolylineFillet() {
    }

    /**
     * 对过急折点插入半径约 {@code radius} 的圆弧。半径应 ≥ 半宽，内侧偏移才不会收成尖点。
     */
    static List<Vec2d> filletSharpCorners(List<Vec2d> points, double radius) {
        if (points == null || points.size() < 3 || radius <= EPSILON) {
            return points == null ? List.of() : List.copyOf(points);
        }
        List<Vec2d> current = new ArrayList<>(points.size() * 2);
        for (Vec2d point : points) {
            if (point != null && PolygonUtils.isFinite(point)) {
                current.add(point.copy());
            }
        }
        if (current.size() < 3) {
            return List.copyOf(current);
        }

        // 从尾向头处理，插入点不影响尚未处理的较低下标
        for (int index = current.size() - 2; index >= 1; index--) {
            double turn = turnRadians(current, index);
            if (Math.abs(turn) + EPSILON < FILLET_TURN_RADIANS) {
                continue;
            }
            List<Vec2d> filleted = filletVertex(current, index, radius);
            if (filleted != null && filleted.size() >= 3) {
                current = filleted;
            }
        }
        return List.copyOf(current);
    }

    private static double turnRadians(List<Vec2d> points, int index) {
        Vec2d in = points.get(index).subtract(points.get(index - 1));
        Vec2d out = points.get(index + 1).subtract(points.get(index));
        double inLen = in.length();
        double outLen = out.length();
        if (inLen <= EPSILON || outLen <= EPSILON) {
            return 0.0;
        }
        Vec2d inUnit = in.multiply(1.0 / inLen);
        Vec2d outUnit = out.multiply(1.0 / outLen);
        return Math.atan2(inUnit.cross(outUnit), inUnit.dot(outUnit));
    }

    private static List<Vec2d> filletVertex(List<Vec2d> points, int vertexIndex, double radius) {
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

        double len1 = prev.distance(corner);
        double len2 = next.distance(corner);
        double halfAngle = angle / 2.0;
        double tanHalf = Math.tan(halfAngle);
        if (tanHalf <= EPSILON) {
            return null;
        }
        // 短边时缩小有效半径，使切点/圆心/弧一致；尽量吃满邻边以免半宽内侧塌缩
        double maxTangent = Math.min(len1, len2) * 0.49;
        double tangentOffset = Math.min(radius / tanHalf, maxTangent);
        if (tangentOffset <= EPSILON) {
            return null;
        }
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
        // 圆弧中点应落在圆心→折点方向上（内角圆角），据此选择 trim1→trim2 的旋向，避免绕远/折返自交
        Vec2d towardCorner = normalize(corner.subtract(center));
        if (towardCorner.lengthSquared() < EPSILON) {
            return null;
        }
        Vec2d expectedMid = center.add(towardCorner.multiply(effectiveRadius));

        double startAngle = Math.atan2(trim1.y - center.y, trim1.x - center.x);
        double endAngle = Math.atan2(trim2.y - center.y, trim2.x - center.x);
        double sweep = signedSweepNear(startAngle, endAngle, center, effectiveRadius, expectedMid);

        List<Vec2d> result = new ArrayList<>(points.size() + ARC_SEGMENTS);
        for (int i = 0; i < vertexIndex; i++) {
            result.add(points.get(i).copy());
        }
        if (result.isEmpty() || result.getLast().distance(trim1) > EPSILON) {
            result.add(trim1);
        } else {
            result.set(result.size() - 1, trim1);
        }

        for (int i = 1; i < ARC_SEGMENTS; i++) {
            double t = (double) i / ARC_SEGMENTS;
            double angleAt = startAngle + sweep * t;
            result.add(new Vec2d(
                center.x + effectiveRadius * Math.cos(angleAt),
                center.y + effectiveRadius * Math.sin(angleAt)));
        }
        result.add(trim2);
        for (int i = vertexIndex + 1; i < points.size(); i++) {
            result.add(points.get(i).copy());
        }
        return result.size() >= 3 ? result : null;
    }

    /**
     * 从 {@code a0} 转到 {@code a1} 的有向扫角，取使弧中点更靠近 {@code nearPoint} 的那一侧。
     */
    private static double signedSweepNear(
            double a0,
            double a1,
            Vec2d center,
            double radius,
            Vec2d nearPoint) {
        double ccw = a1 - a0;
        while (ccw < 0.0) {
            ccw += 2.0 * Math.PI;
        }
        while (ccw >= 2.0 * Math.PI) {
            ccw -= 2.0 * Math.PI;
        }
        double cw = ccw - 2.0 * Math.PI;
        Vec2d midCcw = polar(center, radius, a0 + ccw * 0.5);
        Vec2d midCw = polar(center, radius, a0 + cw * 0.5);
        return midCcw.distance(nearPoint) <= midCw.distance(nearPoint) ? ccw : cw;
    }

    private static Vec2d polar(Vec2d center, double radius, double angle) {
        return new Vec2d(
            center.x + radius * Math.cos(angle),
            center.y + radius * Math.sin(angle));
    }

    private static Vec2d normalize(Vec2d vector) {
        double lengthSquared = vector.lengthSquared();
        if (lengthSquared < EPSILON) {
            return new Vec2d(0, 0);
        }
        return vector.normalize();
    }
}
