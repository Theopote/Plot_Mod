package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * 检测画布路径是否与已有道路中心线重合（重复认领提示用，不阻止认领）。
 */
public final class RoadAdoptDuplicateDetector {

    private static final double LENGTH_RATIO_MIN = 0.9;
    private static final double LENGTH_RATIO_MAX = 1.1;

    private RoadAdoptDuplicateDetector() {
    }

    public static boolean overlapsExistingPath(RoadNetwork network, List<Vec2d> pathPoints) {
        if (network == null || pathPoints == null || pathPoints.size() < 2 || network.getEdges().isEmpty()) {
            return false;
        }
        for (RoadEdge edge : network.getEdges().values()) {
            List<Vec2d> edgePoints = edge.getCenterlinePoints();
            if (edgePoints == null || edgePoints.size() < 2) {
                continue;
            }
            if (polylineMatches(pathPoints, edgePoints, RoadNetworkBuilder.NODE_TOLERANCE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean polylineMatches(List<Vec2d> candidate, List<Vec2d> reference, double tolerance) {
        if (matchesOrientation(candidate, reference, tolerance, false)) {
            return deviationWithinTolerance(candidate, reference, tolerance);
        }
        if (matchesOrientation(candidate, reference, tolerance, true)) {
            return deviationWithinTolerance(candidate, reversePoints(reference), tolerance);
        }
        return false;
    }

    private static boolean matchesOrientation(
            List<Vec2d> candidate,
            List<Vec2d> reference,
            double tolerance,
            boolean reverseReference) {
        Vec2d refStart = reverseReference ? reference.getLast() : reference.getFirst();
        Vec2d refEnd = reverseReference ? reference.getFirst() : reference.getLast();
        return RoadGeometryUtils.pointsNear(candidate.getFirst(), refStart, tolerance)
            && RoadGeometryUtils.pointsNear(candidate.getLast(), refEnd, tolerance);
    }

    private static boolean deviationWithinTolerance(
            List<Vec2d> candidate,
            List<Vec2d> reference,
            double tolerance) {
        double candidateLength = RoadGeometryUtils.calculatePathLength(candidate);
        double referenceLength = RoadGeometryUtils.calculatePathLength(reference);
        if (candidateLength <= 1e-6 || referenceLength <= 1e-6) {
            return false;
        }
        double ratio = candidateLength / referenceLength;
        if (ratio < LENGTH_RATIO_MIN || ratio > LENGTH_RATIO_MAX) {
            return false;
        }
        return maxDistanceToPolyline(candidate, reference) <= tolerance;
    }

    private static double maxDistanceToPolyline(List<Vec2d> points, List<Vec2d> polyline) {
        double max = 0.0;
        for (Vec2d point : points) {
            max = Math.max(max, distanceToPolyline(point, polyline));
        }
        return max;
    }

    private static double distanceToPolyline(Vec2d point, List<Vec2d> polyline) {
        double min = Double.MAX_VALUE;
        for (int i = 1; i < polyline.size(); i++) {
            min = Math.min(min, distanceToSegment(point, polyline.get(i - 1), polyline.get(i)));
        }
        return min;
    }

    private static double distanceToSegment(Vec2d point, Vec2d start, Vec2d end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-12) {
            return point.distance(start);
        }
        double t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec2d projection = new Vec2d(start.x + t * dx, start.y + t * dy);
        return point.distance(projection);
    }

    private static List<Vec2d> reversePoints(List<Vec2d> points) {
        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i));
        }
        return reversed;
    }
}
