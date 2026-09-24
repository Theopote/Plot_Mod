package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.earthwork.RoadEarthworkCorridorResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/** 从路网/路径解析走廊多边形与中心线（画布叠加层用）。 */
public final class RoadOverlayGeometry {

    private RoadOverlayGeometry() {
    }

    public static List<Vec2d> resolveRoadCorridor(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        List<Vec2d> centerline = resolveRoadCenterline(network, road);
        if (centerline.size() < 2) {
            return List.of();
        }
        double halfWidth = resolveRoadHalfWidth(network, road, config);
        if (halfWidth <= 0.0) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, halfWidth);
    }

    public static List<Vec2d> resolvePathCorridor(Shape path, RoadSystemConfig config) {
        List<Vec2d> centerline = RoadGeometryUtils.extractShapePoints(path);
        if (centerline.size() < 2 || config == null) {
            return List.of();
        }
        double halfWidth = resolveConfigCorridorHalfWidth(config);
        if (halfWidth <= 0.0) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, halfWidth);
    }

    /** 认领候选路径走廊半宽（与已生成道路同一套横断面解析）。 */
    public static double resolveConfigCorridorHalfWidth(RoadSystemConfig config) {
        if (config == null) {
            return 0.0;
        }
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        double halfWidth = section.carriagewayHalfWidth() + section.outerBandWidth();
        if (section.includeDrain) {
            halfWidth += 1.0;
        }
        return Math.max(0.5, halfWidth);
    }

    public static List<Vec2d> resolveRoadCenterline(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        return RoadCenterlineShapeValidator.buildChainedCenterline(network, road);
    }

    public static double resolveRoadHalfWidth(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (network == null || road == null || config == null) {
            return 0.0;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(network, road);
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                return RoadEarthworkCorridorResolver.resolveCorridorHalfWidth(network, edge, config, 0);
            }
        }
        return Math.max(0.5, resolveConfigCorridorHalfWidth(config));
    }

    public static boolean containsPoint(List<Vec2d> polygon, double x, double y) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        int count = polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);
            if (((pi.y > y) != (pj.y > y))
                    && (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y + 1e-12) + pi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static List<Vec2d> mergeCenterlines(List<Vec2d> target, List<Vec2d> segment) {
        if (segment == null || segment.isEmpty()) {
            return target != null ? target : List.of();
        }
        List<Vec2d> merged = target != null ? new ArrayList<>(target) : new ArrayList<>();
        int startIndex = 0;
        if (!merged.isEmpty()
                && RoadGeometryUtils.pointsNear(
                    merged.getLast(),
                    segment.getFirst(),
                    com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE)) {
            startIndex = 1;
        }
        for (int i = startIndex; i < segment.size(); i++) {
            merged.add(segment.get(i).copy());
        }
        return merged;
    }

    /** 单分段中心线（认领候选等场景）。 */
    public static List<Vec2d> resolveShapeCenterline(Shape path) {
        return RoadGeometryUtils.extractShapePoints(path);
    }

    /** 沿道路链拼接 plan 中心线（供方向箭头等）。 */
    public static List<Vec2d> resolvePlanCenterline(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        List<Vec2d> merged = new ArrayList<>();
        for (String segmentId : RoadSegmentOrdering.orderedSegmentIds(network, road)) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            merged = mergeCenterlines(merged, RoadPlanGeometry.resolveEdgeCenterline(network, edge));
        }
        return List.copyOf(merged);
    }
}
