package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 路口造型相关的纯几何计算（不依赖 Minecraft World）
 */
public final class RoadJunctionGeometry {
    public static final double DEFAULT_JUNCTION_RADIUS = 3.0;
    private static final double VERTEX_DEDUP_EPSILON = 0.05;

    private RoadJunctionGeometry() {
    }

    /**
     * 从各相连边在路口处的左右边界角点构建闭合多边形（按极角排序）
     */
    public static List<Vec2d> collectPolygonVertices(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius) {
        if (nodeId == null || edges == null || halfWidthResolver == null || edges.isEmpty()) {
            return List.of();
        }

        Vec2d center = resolveNodeCenter(nodeId, edges);
        double effectiveRadius = resolveEffectiveJunctionRadius(edges, halfWidthResolver, junctionRadius);
        List<Vec2d> corners = new ArrayList<>();

        for (RoadEdge edge : edges) {
            collectApproachCorners(nodeId, center, edge, halfWidthResolver.applyAsDouble(edge),
                effectiveRadius, corners);
        }

        corners = dedupeNearbyVertices(corners);
        if (corners.size() < 3) {
            return corners;
        }

        List<Vec2d> sorted = sortVerticesByPolarAngle(corners, center);
        if (!isSimplePolygon(sorted)) {
            List<Vec2d> hull = convexHull(corners);
            sorted = hull.size() >= 3 ? sortVerticesByPolarAngle(hull, center) : sorted;
        }
        if (!RoadGeometryUtils.pointInPolygon(center, sorted)) {
            List<Vec2d> withCenter = new ArrayList<>(corners);
            withCenter.add(center);
            List<Vec2d> hull = convexHull(withCenter);
            if (hull.size() >= 3) {
                sorted = sortVerticesByPolarAngle(hull, center);
            }
        }
        return sorted;
    }

    static void collectApproachCorners(
            String nodeId,
            Vec2d center,
            RoadEdge edge,
            double halfWidth,
            double junctionRadius,
            List<Vec2d> out) {
        if (edge == null || center == null || out == null || halfWidth <= 0) {
            return;
        }

        Vec2d direction = computeApproachDirection(edge, nodeId);
        if (direction.lengthSquared() < 1e-12) {
            return;
        }

        Vec2d unit = direction.normalize();
        Vec2d leftNormal = unit.perpendicular();
        Vec2d mouth = center.add(unit.multiply(junctionRadius));
        out.add(mouth.add(leftNormal.multiply(halfWidth)));
        out.add(mouth.subtract(leftNormal.multiply(halfWidth)));
    }

    static Vec2d computeApproachDirection(RoadEdge edge, String nodeId) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() < 2) {
            return new Vec2d(0, 0);
        }
        if (edge.getStartNodeId().equals(nodeId)) {
            return points.get(1).subtract(points.getFirst());
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return points.get(points.size() - 2).subtract(points.getLast());
        }
        return new Vec2d(0, 0);
    }

    static Vec2d resolveNodeCenter(String nodeId, List<RoadEdge> edges) {
        for (RoadEdge edge : edges) {
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points == null || points.isEmpty()) {
                continue;
            }
            if (edge.getStartNodeId().equals(nodeId)) {
                return points.getFirst();
            }
            if (edge.getEndNodeId().equals(nodeId)) {
                return points.getLast();
            }
        }
        return new Vec2d(0, 0);
    }

    static double resolveEffectiveJunctionRadius(
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double baseRadius) {
        double maxHalfWidth = 0;
        for (RoadEdge edge : edges) {
            maxHalfWidth = Math.max(maxHalfWidth, halfWidthResolver.applyAsDouble(edge));
        }
        return Math.max(baseRadius, maxHalfWidth + 1.0);
    }

    static List<Vec2d> sortVerticesByPolarAngle(List<Vec2d> vertices, Vec2d center) {
        if (vertices == null || vertices.isEmpty()) {
            return List.of();
        }
        List<Vec2d> sorted = new ArrayList<>(vertices);
        sorted.sort(Comparator.comparingDouble(v -> Math.atan2(v.y - center.y, v.x - center.x)));
        return sorted;
    }

    static List<Vec2d> dedupeNearbyVertices(List<Vec2d> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return List.of();
        }
        List<Vec2d> deduped = new ArrayList<>();
        for (Vec2d vertex : vertices) {
            boolean duplicate = false;
            for (Vec2d existing : deduped) {
                if (existing.distanceSquared(vertex) <= VERTEX_DEDUP_EPSILON * VERTEX_DEDUP_EPSILON) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(vertex);
            }
        }
        return deduped;
    }

    static boolean isSimplePolygon(List<Vec2d> vertices) {
        int count = vertices.size();
        if (count < 3) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            Vec2d a1 = vertices.get(i);
            Vec2d a2 = vertices.get((i + 1) % count);
            for (int j = i + 1; j < count; j++) {
                if (j == i || j == (i + 1) % count || (i + 1) % count == j || (j + 1) % count == i) {
                    continue;
                }
                Vec2d b1 = vertices.get(j);
                Vec2d b2 = vertices.get((j + 1) % count);
                if (segmentsIntersectProperly(a1, a2, b1, b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    static List<Vec2d> convexHull(List<Vec2d> points) {
        if (points == null || points.size() <= 3) {
            return points == null ? List.of() : new ArrayList<>(points);
        }

        List<Vec2d> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble((Vec2d p) -> p.x).thenComparingDouble(p -> p.y));

        List<Vec2d> lower = new ArrayList<>();
        for (Vec2d point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<Vec2d> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Vec2d point = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(Vec2d origin, Vec2d a, Vec2d b) {
        return (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x);
    }

    private static boolean segmentsIntersectProperly(Vec2d a1, Vec2d a2, Vec2d b1, Vec2d b2) {
        double d1 = cross(b1, b2, a1);
        double d2 = cross(b1, b2, a2);
        double d3 = cross(a1, a2, b1);
        double d4 = cross(a1, a2, b2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
            && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    public static List<Vec2d> extractNearNodeSegment(RoadEdge edge, String nodeId, double maxDistance) {
        if (edge == null || nodeId == null) {
            return List.of();
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return List.of();
        }

        boolean atStart = edge.getStartNodeId().equals(nodeId);
        List<Vec2d> segment = new ArrayList<>();
        if (atStart) {
            segment.add(points.getFirst());
            segment.add(points.get(Math.min(1, points.size() - 1)));
            if (points.size() > 2) {
                segment.add(RoadGeometryUtils.pointAlongPolylineFrom(points.getFirst(), points, maxDistance));
            }
        } else {
            List<Vec2d> reversed = new ArrayList<>(points);
            Collections.reverse(reversed);
            segment.add(RoadGeometryUtils.pointAlongPolylineFrom(points.getLast(), reversed, maxDistance));
            segment.add(points.get(Math.max(0, points.size() - 2)));
            segment.add(points.getLast());
        }
        return segment;
    }

    /**
     * 在路口中心附近生成简化圆形包络的画布坐标采样点
     */
    public static List<Vec2d> collectSimpleEnvelopePoints(Vec2d center, int radius) {
        List<Vec2d> points = new ArrayList<>();
        if (center == null || radius <= 0) {
            return points;
        }

        int rSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= rSquared) {
                    points.add(new Vec2d(center.x + dx, center.y + dz));
                }
            }
        }
        return points;
    }
}
