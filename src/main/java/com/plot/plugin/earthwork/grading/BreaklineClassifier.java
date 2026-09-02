package com.plot.plugin.earthwork.grading;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.plugin.earthwork.model.Breakline;

import java.util.List;

/**
 * Breakline 折线侧归属：按最近线段法向判定左/右半平面。
 */
public final class BreaklineClassifier {

    public enum Side {
        LEFT,
        RIGHT,
        ON_LINE
    }

    private BreaklineClassifier() {
    }

    public static boolean isHardBoundary(Breakline breakline) {
        if (breakline == null) {
            return false;
        }
        String role = breakline.getRole();
        return Breakline.ROLE_HARD_BOUNDARY.equals(role)
            || Breakline.ROLE_NO_BLENDING.equals(role)
            || Breakline.ROLE_ELEVATION_STEP.equals(role);
    }

    /**
     * 若格点落在 breakline 影响带内，返回该侧强制归属的 Zone id；否则返回 null。
     */
    public static String resolveMandatedZoneId(
            Vec2d point,
            List<Breakline> breaklines,
            double influenceDistanceBlocks) {
        if (point == null || breaklines == null || breaklines.isEmpty()) {
            return null;
        }
        double influence = Math.max(0.0, influenceDistanceBlocks);
        String mandatedZoneId = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Breakline breakline : breaklines) {
            if (breakline == null || !isHardBoundary(breakline)) {
                continue;
            }
            List<Vec2d> points = breakline.getPoints();
            if (points.size() < 2) {
                continue;
            }
            NearestSegment nearest = findNearestSegment(point, points);
            if (nearest.distance > influence) {
                continue;
            }
            Side side = classifySide(point, nearest.start, nearest.end);
            String zoneId = switch (side) {
                case LEFT -> breakline.getLeftZoneId();
                case RIGHT -> breakline.getRightZoneId();
                case ON_LINE -> pickNonBlank(breakline.getLeftZoneId(), breakline.getRightZoneId());
            };
            if (zoneId == null || zoneId.isBlank()) {
                continue;
            }
            if (nearest.distance < nearestDistance) {
                nearestDistance = nearest.distance;
                mandatedZoneId = zoneId;
            }
        }
        return mandatedZoneId;
    }

    static Side classifySide(Vec2d point, Vec2d segmentStart, Vec2d segmentEnd) {
        if (point == null || segmentStart == null || segmentEnd == null) {
            return Side.ON_LINE;
        }
        double cross = (segmentEnd.x - segmentStart.x) * (point.y - segmentStart.y)
            - (segmentEnd.y - segmentStart.y) * (point.x - segmentStart.x);
        if (Math.abs(cross) < 1e-6) {
            return Side.ON_LINE;
        }
        return cross > 0.0 ? Side.LEFT : Side.RIGHT;
    }

    public static double distanceToPolyline(Vec2d point, List<Vec2d> polyline) {
        if (point == null || polyline == null || polyline.size() < 2) {
            return Double.MAX_VALUE;
        }
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d start = polyline.get(i);
            Vec2d end = polyline.get(i + 1);
            if (start == null || end == null) {
                continue;
            }
            minDistance = Math.min(minDistance, GeometryUtils.pointToSegmentDistance(point, start, end));
        }
        return minDistance;
    }

    private static NearestSegment findNearestSegment(Vec2d point, List<Vec2d> polyline) {
        double minDistance = Double.MAX_VALUE;
        Vec2d bestStart = polyline.getFirst();
        Vec2d bestEnd = polyline.get(1);
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d start = polyline.get(i);
            Vec2d end = polyline.get(i + 1);
            if (start == null || end == null) {
                continue;
            }
            double distance = GeometryUtils.pointToSegmentDistance(point, start, end);
            if (distance < minDistance) {
                minDistance = distance;
                bestStart = start;
                bestEnd = end;
            }
        }
        return new NearestSegment(bestStart, bestEnd, minDistance);
    }

    private static String pickNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right;
    }

    private record NearestSegment(Vec2d start, Vec2d end, double distance) {
    }
}
