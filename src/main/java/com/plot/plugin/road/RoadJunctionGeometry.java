package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 路口造型相关的纯几何计算（不依赖 Minecraft World）
 */
public final class RoadJunctionGeometry {
    public static final double DEFAULT_JUNCTION_RADIUS = 3.0;

    private RoadJunctionGeometry() {
    }

    /**
     * 从各相连边的偏移边界收集路口多边形顶点
     */
    public static List<Vec2d> collectPolygonVertices(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius) {
        List<Vec2d> polygon = new ArrayList<>();
        if (nodeId == null || edges == null || halfWidthResolver == null) {
            return polygon;
        }

        for (RoadEdge edge : edges) {
            List<Vec2d> nearSegment = extractNearNodeSegment(edge, nodeId, junctionRadius);
            if (nearSegment.size() < 2) {
                continue;
            }
            double halfWidth = halfWidthResolver.applyAsDouble(edge);
            List<Vec2d> offset = OffsetHandler.offsetPolyline(nearSegment, halfWidth);
            if (!offset.isEmpty()) {
                polygon.add(offset.getLast());
            }
        }
        return polygon;
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
