package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 简单多边形的 Straight Skeleton 近似。
 * <p>
 * 坡屋顶高度场使用骨架波前时间： interior 点的高度与到边界的欧氏距离成正比（HIP），
 * 或通过屋脊方向上的檐口距离（GABLE）。同时提取局部极大值作为骨架节点/边。
 */
public final class StraightSkeleton {
    private static final double PARALLEL_TOLERANCE = 0.15;

    private StraightSkeleton() {
    }

    public record SkeletonNode(int id, Vec2d point, double time) {
    }

    public record SkeletonEdge(int startId, int endId) {
    }

    public record Result(
            List<Vec2d> polygon,
            List<SkeletonNode> nodes,
            List<SkeletonEdge> edges,
            double maxTime,
            boolean success) {

        public static Result failed() {
            return new Result(List.of(), List.of(), List.of(), 0.0, false);
        }

        /** 骨架波前时间：点到边界的最短欧氏距离。 */
        public double skeletalTime(Vec2d point) {
            return distanceToBoundary(point, polygon);
        }

        public double maxSkeletalTime() {
            return maxTime;
        }

        /** 屋脊主方向：取最长边方向。 */
        public Vec2d primaryRidgeDirection() {
            return longestEdgeDirection(polygon);
        }

        /**
         * 双坡檐口距离：沿垂直于屋脊方向，到平行于屋脊的边界边的最短距离。
         */
        public double gableEaveDistance(Vec2d point, Vec2d ridgeDirection) {
            if (polygon.size() < 3 || ridgeDirection == null || ridgeDirection.lengthSquared() < PolygonUtils.DEFAULT_EPSILON) {
                return skeletalTime(point);
            }
            Vec2d ridge = ridgeDirection.normalize();
            Vec2d perpendicular = PolygonUtils.leftNormal(ridge);
            double minDistance = Double.MAX_VALUE;
            int count = polygon.size();
            for (int i = 0; i < count; i++) {
                Vec2d start = polygon.get(i);
                Vec2d end = polygon.get((i + 1) % count);
                Vec2d edge = end.subtract(start);
                double edgeLength = edge.length();
                if (edgeLength < PolygonUtils.DEFAULT_EPSILON) {
                    continue;
                }
                double alignment = Math.abs(cross(ridge, edge)) / edgeLength;
                if (alignment > PARALLEL_TOLERANCE) {
                    continue;
                }
                double distance = Math.abs(dot(point.subtract(start), perpendicular));
                minDistance = Math.min(minDistance, distance);
            }
            if (minDistance == Double.MAX_VALUE) {
                return skeletalTime(point);
            }
            return minDistance;
        }
    }

    public static Result compute(List<Vec2d> points) {
        List<Vec2d> polygon = PolygonNormalizer.normalizeOutline(points);
        PolygonValidator.ValidationResult validation = PolygonValidator.validateSimplePolygon(polygon);
        if (!validation.valid() || polygon.size() < 3) {
            return Result.failed();
        }
        if (PolygonUtils.absoluteArea(polygon) < PolygonUtils.MIN_AREA) {
            return Result.failed();
        }

        List<Vec2d> samples = PolygonRasterizer.collectCellCenters(polygon);
        if (samples.isEmpty()) {
            return Result.failed();
        }

        double maxTime = 0.0;
        List<SamplePoint> interior = new ArrayList<>();
        for (Vec2d sample : samples) {
            if (!PolygonBoolean.contains(polygon, sample)) {
                continue;
            }
            double time = distanceToBoundary(sample, polygon);
            maxTime = Math.max(maxTime, time);
            interior.add(new SamplePoint(sample, time));
        }
        Vec2d centroid = computeCentroid(polygon);
        if (PolygonBoolean.contains(polygon, centroid)) {
            maxTime = Math.max(maxTime, distanceToBoundary(centroid, polygon));
        }
        if (interior.isEmpty()) {
            return Result.failed();
        }

        List<SkeletonNode> nodes = extractRidgeNodes(interior, maxTime);
        List<SkeletonEdge> edges = connectAdjacentNodes(nodes);
        return new Result(
            List.copyOf(polygon),
            List.copyOf(nodes),
            List.copyOf(edges),
            maxTime,
            true
        );
    }

    public static double distanceToBoundary(Vec2d point, List<Vec2d> polygon) {
        if (point == null || polygon == null || polygon.size() < 3) {
            return 0.0;
        }
        double minDistance = Double.MAX_VALUE;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % count);
            minDistance = Math.min(minDistance, distancePointToSegment(point, start, end));
        }
        return minDistance == Double.MAX_VALUE ? 0.0 : minDistance;
    }

    private static List<SkeletonNode> extractRidgeNodes(List<SamplePoint> interior, double maxTime) {
        if (interior.isEmpty()) {
            return List.of();
        }
        double threshold = Math.max(0.5, maxTime * 0.85);
        List<SkeletonNode> nodes = new ArrayList<>();
        int id = 0;
        for (SamplePoint sample : interior) {
            if (sample.time + 1e-6 < threshold) {
                continue;
            }
            if (!isLocalMaximum(sample, interior)) {
                continue;
            }
            nodes.add(new SkeletonNode(id++, sample.point.copy(), sample.time));
        }
        if (nodes.isEmpty()) {
            SamplePoint peak = interior.stream()
                .max(java.util.Comparator.comparingDouble(SamplePoint::time))
                .orElse(interior.getFirst());
            nodes.add(new SkeletonNode(0, peak.point.copy(), peak.time));
        }
        return nodes;
    }

    private static boolean isLocalMaximum(SamplePoint candidate, List<SamplePoint> interior) {
        for (SamplePoint neighbor : interior) {
            if (neighbor == candidate) {
                continue;
            }
            if (neighbor.point.distance(candidate.point) > 1.25) {
                continue;
            }
            if (neighbor.time > candidate.time + 1e-6) {
                return false;
            }
        }
        return true;
    }

    private static List<SkeletonEdge> connectAdjacentNodes(List<SkeletonNode> nodes) {
        List<SkeletonEdge> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            SkeletonNode a = nodes.get(i);
            SkeletonNode closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }
                SkeletonNode b = nodes.get(j);
                double distance = a.point().distance(b.point());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = b;
                }
            }
            if (closest != null && closestDistance <= 2.5) {
                String key = edgeKey(a.id(), closest.id());
                if (seen.add(key)) {
                    edges.add(new SkeletonEdge(a.id(), closest.id()));
                }
            }
        }
        return edges;
    }

    private static Vec2d longestEdgeDirection(List<Vec2d> polygon) {
        Vec2d best = new Vec2d(1, 0);
        double bestLength = 0.0;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % count);
            Vec2d edge = end.subtract(start);
            double length = edge.length();
            if (length > bestLength) {
                bestLength = length;
                best = edge.normalize();
            }
        }
        return bestLength > PolygonUtils.DEFAULT_EPSILON ? best : new Vec2d(1, 0);
    }

    private static double distancePointToSegment(Vec2d point, Vec2d start, Vec2d end) {
        Vec2d segment = end.subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < PolygonUtils.DEFAULT_EPSILON) {
            return point.distance(start);
        }
        double t = dot(point.subtract(start), segment) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec2d projection = start.add(segment.multiply(t));
        return point.distance(projection);
    }

    private static double dot(Vec2d a, Vec2d b) {
        return a.x * b.x + a.y * b.y;
    }

    private static double cross(Vec2d a, Vec2d b) {
        return a.x * b.y - a.y * b.x;
    }

    private static Vec2d computeCentroid(List<Vec2d> polygon) {
        double sumX = 0.0;
        double sumY = 0.0;
        for (Vec2d point : polygon) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Vec2d(sumX / polygon.size(), sumY / polygon.size());
    }

    private static String edgeKey(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return min + ":" + max;
    }

    private record SamplePoint(Vec2d point, double time) {
    }
}
