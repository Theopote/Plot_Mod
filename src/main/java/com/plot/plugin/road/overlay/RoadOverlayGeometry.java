package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.earthwork.RoadEarthworkCorridorResolver;
import com.plot.plugin.road.geometry.RoadCanvasScale;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
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
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        List<Vec2d> centerline = resolvePlanCenterline(network, road);
        if (centerline.size() < 2) {
            centerline = resolveRoadCenterline(network, road);
        }
        if (centerline.size() < 2) {
            return List.of();
        }
        double halfWidth = resolveRoadHalfWidth(network, road, config, centerline, coordinates);
        if (halfWidth <= 0.0) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, halfWidth);
    }

    public static List<Vec2d> resolvePathCorridor(
            Shape path,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        List<Vec2d> centerline = RoadGeometryUtils.extractShapePoints(path);
        if (centerline.size() < 2 || config == null) {
            return List.of();
        }
        double halfWidth = resolveConfigCorridorHalfWidth(config, centerline, coordinates);
        if (halfWidth <= 0.0) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, halfWidth);
    }

    /** 认领候选路径走廊半宽（画布坐标，含边坡外缘估计）。 */
    public static double resolveConfigCorridorHalfWidth(
            RoadSystemConfig config,
            List<Vec2d> centerline,
            ICoordinateService coordinates) {
        if (config == null) {
            return 0.0;
        }
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        double halfWidthBlocks = RoadCorridorWidth.overlayHalfWidthBlocks(section, config);
        return scaleHalfWidthToCanvas(halfWidthBlocks, centerline, coordinates);
    }

    /** 硬质路面半宽（方块数，不含画布缩放）。 */
    public static double resolveConfigPavementHalfWidthBlocks(RoadSystemConfig config) {
        if (config == null) {
            return 0.0;
        }
        return RoadCorridorWidth.pavementHalfWidthBlocks(ResolvedCrossSection.fromConfig(config));
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
            RoadSystemConfig config,
            List<Vec2d> centerline,
            ICoordinateService coordinates) {
        if (network == null || road == null || config == null) {
            return 0.0;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(network, road);
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                ResolvedCrossSection section = RoadModelUtils.resolveCrossSection(network, edge, config);
                double halfWidthBlocks = RoadCorridorWidth.overlayHalfWidthBlocks(section, config);
                return scaleHalfWidthToCanvas(halfWidthBlocks, centerline, coordinates);
            }
        }
        return resolveConfigCorridorHalfWidth(config, centerline, coordinates);
    }

    private static double scaleHalfWidthToCanvas(
            double halfWidthBlocks,
            List<Vec2d> centerline,
            ICoordinateService coordinates) {
        if (halfWidthBlocks <= 0.0) {
            return 0.0;
        }
        if (coordinates == null || centerline == null || centerline.size() < 2) {
            return halfWidthBlocks;
        }
        RoadCanvasScale scale = RoadCanvasScale.capture(coordinates, centerline);
        return scale.uniformBlocksToCanvas(halfWidthBlocks, centerline);
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

    public static boolean polygonContainedInRect(
            List<Vec2d> polygon,
            double minX,
            double minY,
            double maxX,
            double maxY) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        for (Vec2d point : polygon) {
            if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) {
                return false;
            }
        }
        return true;
    }

    public static boolean polygonIntersectsRect(
            List<Vec2d> polygon,
            double minX,
            double minY,
            double maxX,
            double maxY) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        for (Vec2d point : polygon) {
            if (point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY) {
                return true;
            }
        }
        if (containsPoint(polygon, minX, minY)
                || containsPoint(polygon, maxX, minY)
                || containsPoint(polygon, maxX, maxY)
                || containsPoint(polygon, minX, maxY)) {
            return true;
        }
        return segmentsIntersectRect(polygon, minX, minY, maxX, maxY);
    }

    private static boolean segmentsIntersectRect(
            List<Vec2d> polygon,
            double minX,
            double minY,
            double maxX,
            double maxY) {
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Vec2d a = polygon.get(i);
            Vec2d b = polygon.get((i + 1) % count);
            if (segmentIntersectsSegment(a, b, new Vec2d(minX, minY), new Vec2d(maxX, minY))
                    || segmentIntersectsSegment(a, b, new Vec2d(maxX, minY), new Vec2d(maxX, maxY))
                    || segmentIntersectsSegment(a, b, new Vec2d(maxX, maxY), new Vec2d(minX, maxY))
                    || segmentIntersectsSegment(a, b, new Vec2d(minX, maxY), new Vec2d(minX, minY))) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentIntersectsSegment(Vec2d a, Vec2d b, Vec2d c, Vec2d d) {
        double denominator = (b.x - a.x) * (d.y - c.y) - (b.y - a.y) * (d.x - c.x);
        if (Math.abs(denominator) < 1e-12) {
            return false;
        }
        double ua = ((d.x - c.x) * (a.y - c.y) - (d.y - c.y) * (a.x - c.x)) / denominator;
        double ub = ((b.x - a.x) * (a.y - c.y) - (b.y - a.y) * (a.x - c.x)) / denominator;
        return ua >= 0.0 && ua <= 1.0 && ub >= 0.0 && ub <= 1.0;
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
