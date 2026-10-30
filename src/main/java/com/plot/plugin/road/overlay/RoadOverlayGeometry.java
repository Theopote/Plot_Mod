package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.earthwork.RoadEarthworkCorridorResolver;
import com.plot.plugin.road.geometry.RoadCanvasScale;
import com.plot.plugin.road.geometry.RoadCorridorGeometry;
import com.plot.plugin.road.geometry.RoadCorridorGeometryBuilder;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** 从路网/路径解析走廊多边形与中心线（画布叠加层用）。 */
public final class RoadOverlayGeometry {

    private RoadOverlayGeometry() {
    }

    public static List<Vec2d> resolveRoadCorridor(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        return resolveRoadCorridorGeometry(network, road, config, coordinates, false).primaryFillContour();
    }

    public static RoadCorridorGeometry resolveRoadCorridorGeometry(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        return resolveRoadCorridorGeometry(network, road, config, coordinates, false);
    }

    public static RoadCorridorGeometry resolveRoadCorridorGeometry(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            boolean includeSlopeBatterOverlay) {
        if (road != null && VariableCrossSectionResolver.hasVariableSections(road)) {
            RoadCorridorGeometry variable = resolveVariableRoadCorridorGeometry(
                network, road, config, coordinates, includeSlopeBatterOverlay);
            if (variable.primaryFillContour().size() >= 3) {
                return variable;
            }
        }
        List<Vec2d> centerline = resolvePlanCenterline(network, road);
        if (centerline.size() < 2) {
            centerline = resolveRoadCenterline(network, road);
        }
        if (centerline.size() < 2 || road == null) {
            return new RoadCorridorGeometry(List.of(), List.of(), List.of(), List.of(), false, List.of());
        }
        double halfWidth = resolveRoadHalfWidth(
            network, road, config, centerline, coordinates, includeSlopeBatterOverlay);
        if (halfWidth <= 0.0) {
            return new RoadCorridorGeometry(centerline, List.of(), List.of(), List.of(), false, List.of());
        }
        boolean closed = road.getTopologyMode() == RoadTopologyMode.LOOP
            || RoadCorridorGeometryBuilder.isGeometricallyClosed(centerline);
        return RoadEarthworkCorridorResolver.buildCorridorGeometry(centerline, halfWidth, closed);
    }

    /**
     * 可变横断面：按桩号采样半宽生成阶梯/平滑走廊多边形。
     */
    static RoadCorridorGeometry resolveVariableRoadCorridorGeometry(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            boolean includeSlopeBatterOverlay) {
        if (network == null || road == null || config == null) {
            return new RoadCorridorGeometry(List.of(), List.of(), List.of(), List.of(), false, List.of());
        }
        List<PlanCenterlineSample> samples = samplePlanWithVariableStations(network, road);
        if (samples.size() < 2) {
            return new RoadCorridorGeometry(List.of(), List.of(), List.of(), List.of(), false, List.of());
        }
        List<Vec2d> centerline = samples.stream().map(PlanCenterlineSample::position).toList();
        List<Double> halfWidths = new ArrayList<>(samples.size());
        for (PlanCenterlineSample sample : samples) {
            ResolvedCrossSection section = VariableCrossSectionResolver.resolve(
                network, road, sample.canonicalStation(), config);
            double halfWidthBlocks = resolveOverlayHalfWidthBlocks(
                section, config, includeSlopeBatterOverlay);
            halfWidths.add(scaleHalfWidthToCanvas(halfWidthBlocks, centerline, coordinates));
        }
        boolean closed = road.getTopologyMode() == RoadTopologyMode.LOOP
            || RoadCorridorGeometryBuilder.isGeometricallyClosed(centerline);
        return RoadCorridorGeometryBuilder.build(centerline, halfWidths, closed);
    }

    private static List<PlanCenterlineSample> samplePlanWithVariableStations(
            RoadNetwork network,
            Road road) {
        List<PlanCenterlineSample> base = new ArrayList<>(
            RoadPlanGeometry.resolveRoadCenterlineSamples(network, road));
        if (base.isEmpty()) {
            return List.of();
        }
        double total = RoadPlanGeometry.canonicalLength(network, road);
        TreeSet<Double> stations = new TreeSet<>();
        for (PlanCenterlineSample sample : base) {
            stations.add(sample.canonicalStation());
        }
        if (road.getVariableCrossSections() != null) {
            for (StationCrossSection entry : road.getVariableCrossSections().sortedStations()) {
                double station = Math.clamp(entry.getStation(), 0.0, total);
                stations.add(station);
            }
        }
        stations.add(0.0);
        stations.add(total);

        List<PlanCenterlineSample> merged = new ArrayList<>();
        for (double station : stations) {
            RoadPlanGeometry.pointAtStation(network, road, station).ifPresent(point -> {
                if (merged.isEmpty()
                        || merged.getLast().position().distance(point) > 1e-6
                        || Math.abs(merged.getLast().canonicalStation() - station) > 1e-6) {
                    if (!merged.isEmpty()
                            && merged.getLast().position().distance(point) <= 1e-6) {
                        merged.set(merged.size() - 1, new PlanCenterlineSample(point, station));
                    } else {
                        merged.add(new PlanCenterlineSample(point, station));
                    }
                }
            });
        }
        return merged.size() >= 2 ? List.copyOf(merged) : List.copyOf(base);
    }

    public static List<Vec2d> resolvePathCorridor(
            Shape path,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        return resolvePathCorridorGeometry(path, config, coordinates, false).primaryFillContour();
    }

    public static RoadCorridorGeometry resolvePathCorridorGeometry(
            Shape path,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        return resolvePathCorridorGeometry(path, config, coordinates, false);
    }

    public static RoadCorridorGeometry resolvePathCorridorGeometry(
            Shape path,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            boolean includeSlopeBatterOverlay) {
        List<Vec2d> centerline = RoadGeometryUtils.extractShapePoints(path);
        if (centerline.size() < 2 || config == null) {
            return new RoadCorridorGeometry(List.of(), List.of(), List.of(), List.of(), false, List.of());
        }
        double halfWidth = resolveConfigCorridorHalfWidth(
            config, centerline, coordinates, includeSlopeBatterOverlay);
        if (halfWidth <= 0.0) {
            return new RoadCorridorGeometry(centerline, List.of(), List.of(), List.of(), false, List.of());
        }
        boolean closed = isShapeTopologyClosed(path)
            || RoadCorridorGeometryBuilder.isGeometricallyClosed(centerline);
        return RoadEarthworkCorridorResolver.buildCorridorGeometry(centerline, halfWidth, closed);
    }

    /**
     * Optional outer ring for "show slope batter width": earthwork envelope beyond pavement.
     * Returns null when slope is disabled or not wider than the pavement corridor.
     */
    public static RoadCorridorGeometry resolveRoadSlopeBatterGeometry(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            RoadCorridorGeometry pavementCorridor) {
        RoadCorridorGeometry slope = resolveRoadCorridorGeometry(
            network, road, config, coordinates, true);
        return usableSlopeBatterGeometry(pavementCorridor, slope);
    }

    public static RoadCorridorGeometry resolvePathSlopeBatterGeometry(
            Shape path,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            RoadCorridorGeometry pavementCorridor) {
        RoadCorridorGeometry slope = resolvePathCorridorGeometry(path, config, coordinates, true);
        return usableSlopeBatterGeometry(pavementCorridor, slope);
    }

    static RoadCorridorGeometry usableSlopeBatterGeometry(
            RoadCorridorGeometry pavementCorridor,
            RoadCorridorGeometry slopeCorridor) {
        if (slopeCorridor == null || slopeCorridor.primaryFillContour().size() < 3) {
            return null;
        }
        if (pavementCorridor == null || pavementCorridor.primaryFillContour().size() < 3) {
            return slopeCorridor;
        }
        // Skip a duplicate ring when earthwork envelope collapses to pavement.
        // Compare lateral half-width (not polygon radius): long roads are dominated by length.
        if (representativeHalfWidth(slopeCorridor) <= representativeHalfWidth(pavementCorridor) + 0.25) {
            return null;
        }
        return slopeCorridor;
    }

    private static double representativeHalfWidth(RoadCorridorGeometry geometry) {
        List<Vec2d> centerline = geometry.centerline();
        List<Vec2d> left = geometry.leftBoundary();
        if (centerline.size() >= 1 && left.size() >= 1) {
            return left.getFirst().distance(centerline.getFirst());
        }
        List<Vec2d> contour = geometry.primaryFillContour();
        if (centerline.size() < 2 || contour.isEmpty()) {
            return 0.0;
        }
        double max = 0.0;
        for (Vec2d point : contour) {
            max = Math.max(max, distanceToPolyline(point, centerline));
        }
        return max;
    }

    private static double distanceToPolyline(Vec2d point, List<Vec2d> polyline) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d a = polyline.get(i);
            Vec2d b = polyline.get(i + 1);
            Vec2d ab = b.subtract(a);
            double lengthSq = ab.lengthSquared();
            double t = lengthSq < 1e-12 ? 0.0 : Math.clamp(point.subtract(a).dot(ab) / lengthSq, 0.0, 1.0);
            Vec2d closest = a.add(ab.multiply(t));
            best = Math.min(best, point.distance(closest));
        }
        return best;
    }

    static boolean isShapeTopologyClosed(Shape shape) {
        if (shape == null) {
            return false;
        }
        return switch (shape) {
            case PolylineShape polyline -> polyline.isClosed();
            case BezierCurveShape bezier -> bezier.isClosed();
            case Polygon polygon -> polygon.isClosed();
            default -> false;
        };
    }

    /** 认领候选路径走廊半宽（画布坐标，默认硬质路面；可选含边坡外缘）。 */
    public static double resolveConfigCorridorHalfWidth(
            RoadSystemConfig config,
            List<Vec2d> centerline,
            ICoordinateService coordinates) {
        return resolveConfigCorridorHalfWidth(config, centerline, coordinates, false);
    }

    public static double resolveConfigCorridorHalfWidth(
            RoadSystemConfig config,
            List<Vec2d> centerline,
            ICoordinateService coordinates,
            boolean includeSlopeBatterOverlay) {
        if (config == null) {
            return 0.0;
        }
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        double halfWidthBlocks = resolveOverlayHalfWidthBlocks(section, config, includeSlopeBatterOverlay);
        return scaleHalfWidthToCanvas(halfWidthBlocks, centerline, coordinates);
    }

    /** 画布叠加层半宽（方块数）：硬质路面，或含边坡外缘估计。 */
    public static double resolveOverlayHalfWidthBlocks(
            ResolvedCrossSection section,
            RoadSystemConfig config,
            boolean includeSlopeBatterOverlay) {
        if (section == null) {
            return 0.0;
        }
        if (includeSlopeBatterOverlay) {
            return RoadCorridorWidth.overlayHalfWidthBlocks(section, config);
        }
        return RoadCorridorWidth.pavementHalfWidthBlocks(section);
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
        return resolveRoadHalfWidth(network, road, config, centerline, coordinates, false);
    }

    public static double resolveRoadHalfWidth(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            List<Vec2d> centerline,
            ICoordinateService coordinates,
            boolean includeSlopeBatterOverlay) {
        if (network == null || road == null || config == null) {
            return 0.0;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(network, road);
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                ResolvedCrossSection section = RoadModelUtils.resolveCrossSection(network, edge, config);
                double halfWidthBlocks = resolveOverlayHalfWidthBlocks(
                    section, config, includeSlopeBatterOverlay);
                return scaleHalfWidthToCanvas(halfWidthBlocks, centerline, coordinates);
            }
        }
        return resolveConfigCorridorHalfWidth(
            config, centerline, coordinates, includeSlopeBatterOverlay);
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

    public static boolean containsPoint(RoadCorridorGeometry geometry, double x, double y) {
        if (geometry == null || geometry.primaryFillContour().size() < 3) {
            return false;
        }
        Vec2d point = new Vec2d(x, y);
        if (geometry.closed() && geometry.innerHole().size() >= 3) {
            return RegionGeometry.of(geometry.outerContour(), List.of(geometry.innerHole())).contains(point);
        }
        return containsPoint(geometry.primaryFillContour(), x, y);
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
