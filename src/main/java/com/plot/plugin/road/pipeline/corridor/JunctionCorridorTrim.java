package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Computes how far an edge corridor should stay away from junction nodes.
 */
public final class JunctionCorridorTrim {
    private static final double EPSILON = 1e-9;

    private JunctionCorridorTrim() {
    }

    public static JunctionCorridorBuildContext forEdge(
            RoadNetwork network,
            RoadEdge edge,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config,
            double unitsPerBlock) {
        if (network == null || edge == null || crossSection == null) {
            return JunctionCorridorBuildContext.NONE;
        }
        double edgeLength = edge.getLength();
        if (edgeLength <= EPSILON) {
            return JunctionCorridorBuildContext.NONE;
        }

        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        RoadNode startNode = network.getNode(edge.getStartNodeId());
        RoadNode endNode = network.getNode(edge.getEndNodeId());
        double skipStart = 0.0;
        double skipEnd = 0.0;
        List<List<Vec2d>> polygons = new ArrayList<>();
        boolean preserveClearance = false;

        if (shouldTrimAtEndpoint(network, edge, startNode)) {
            // skipDistance 已按画布尺度返回（半宽/核区随 scale）
            skipStart = skipDistanceAtNode(network, edge, startNode, crossSection, config, scale);
            polygons.addAll(exclusionPolygons(network, startNode, config, scale));
            preserveClearance |= isGradeSeparatedLowerApproach(network, edge, startNode);
        }
        if (shouldTrimAtEndpoint(network, edge, endNode)) {
            skipEnd = skipDistanceAtNode(network, edge, endNode, crossSection, config, scale);
            polygons.addAll(exclusionPolygons(network, endNode, config, scale));
            preserveClearance |= isGradeSeparatedLowerApproach(network, edge, endNode);
        }

        skipStart = clampSkip(skipStart, edgeLength);
        skipEnd = clampSkip(skipEnd, edgeLength);
        if (skipStart + skipEnd > edgeLength - EPSILON) {
            double half = edgeLength / 2.0;
            skipStart = Math.min(skipStart, half);
            skipEnd = Math.min(skipEnd, half);
        }
        return new JunctionCorridorBuildContext(
            new StationFacilityJunctionTrim.FacilityEndpointTrim(skipStart, skipEnd),
            List.copyOf(polygons),
            preserveClearance);
    }

    /** Lower road at a grade-sep node: keep underpass clearance when plaza surface is handed to junction. */
    static boolean isGradeSeparatedLowerApproach(RoadNetwork network, RoadEdge edge, RoadNode node) {
        if (node == null || edge == null) {
            return false;
        }
        if (node.isStackGradeSeparated()) {
            return !node.isStackUpperEdge(edge.getId());
        }
        if (!node.isGradeSeparated()) {
            return false;
        }
        String elevatedRoadId = node.getElevatedRoadId();
        return elevatedRoadId == null || !elevatedRoadId.equals(edge.getRoadId());
    }

    static boolean shouldTrimAtEndpoint(RoadNetwork network, RoadEdge edge, RoadNode node) {
        if (node == null || node.getDegree() < 3) {
            return false;
        }
        if (node.isStackGradeSeparated() || node.isGradeSeparated()) {
            // 立交/STACK：走廊保留下层路面掩膜，避免 exclusion 与路口填充多边形不一致
            // 留下「十」字形原地形空洞；路口铺装通过图层优先级叠加。
            return false;
        }
        return true;
    }

    /**
     * 走廊排除区：区域式优先用重叠核 + 圆角补铺（不含进口路臂），
     * 使路臂保留各道路原材质；旧路径回退完整锐角足迹。
     * 几何与 {@link RoadJunctionGenerator} 相同画布尺度，避免排除区偏小、走廊盖住街角倒圆。
     */
    private static List<List<Vec2d>> exclusionPolygons(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            double canvasScale) {
        JunctionGeometryResult geometry = resolveJunctionGeometry(
            network, node, config, /*cornerRadiusBlocks*/ 0.0, canvasScale);
        List<List<Vec2d>> polygons = new ArrayList<>();
        if (geometry.hasRegionZones()) {
            polygons.add(geometry.overlapRegion());
            double cornerBlocks = RoadJunctionGenerator.resolveCornerRadius(node, network, config);
            if (cornerBlocks > 1e-6) {
                JunctionGeometryResult filleted = resolveJunctionGeometry(
                    network, node, config, cornerBlocks, canvasScale);
                for (List<Vec2d> addition : filleted.filletAdditions()) {
                    if (addition != null && addition.size() >= 3) {
                        polygons.add(addition);
                    }
                }
                // 补铺 gore 与生成侧 collectFilletGoreCells 对齐，避免走廊盖住街角外扩
                for (JunctionGeometryResult.FilletCorner fillet : filleted.fillets()) {
                    List<Vec2d> gore = RoadJunctionGenerator.filletGorePolygon(fillet);
                    if (gore.size() >= 3) {
                        polygons.add(gore);
                    }
                }
            }
            return polygons;
        }
        if (geometry.sharpPolygon().size() >= 3) {
            polygons.add(geometry.sharpPolygon());
        } else if (geometry.fillPolygon().size() >= 3) {
            polygons.add(geometry.fillPolygon());
        }
        return polygons;
    }

    private static double skipDistanceAtNode(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode node,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config,
            double canvasScale) {
        double scale = canvasScale > 1e-9 ? canvasScale : 1.0;
        List<RoadEdge> connected = connectedEdges(
            network,
            node,
            node.isGradeSeparated() ? node.getElevatedRoadId() : null);
        if (connected.isEmpty()) {
            return Math.max(
                1.0,
                (crossSection.carriagewayWidth + crossSection.outerBandBlockCount() + 0.5) * scale);
        }
        JunctionGeometryResult geometry = resolveJunctionGeometry(network, node, config, 0.0, scale);
        if (geometry.hasRegionZones() && node.getPosition() != null) {
            Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
            Vec2d approach = RoadJunctionGeometry.computeApproachDirection(
                centerlines.apply(edge), edge, node.getId());
            if (approach.lengthSquared() > 1e-12) {
                Vec2d unit = approach.normalize();
                double along = 0.0;
                for (Vec2d corner : geometry.overlapRegion()) {
                    along = Math.max(along, Math.abs(corner.subtract(node.getPosition()).dot(unit)));
                }
                return Math.max(scale, along + 0.75 * scale);
            }
        }
        ToDoubleFunction<RoadEdge> halfWidthCanvas = canvasHalfWidth(network, config, scale);
        return RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            halfWidthCanvas,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS) * scale);
    }

    private static JunctionGeometryResult resolveJunctionGeometry(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            double cornerRadiusBlocks,
            double canvasScale) {
        String elevatedRoadId = node.isGradeSeparated() ? node.getElevatedRoadId() : null;
        List<RoadEdge> connected = connectedEdges(network, node, elevatedRoadId);
        if (connected.isEmpty()) {
            return JunctionGeometryResult.empty();
        }
        double scale = canvasScale > 1e-9 ? canvasScale : 1.0;
        ToDoubleFunction<RoadEdge> halfWidthCanvas = canvasHalfWidth(network, config, scale);
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            halfWidthCanvas,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS) * scale);
        return RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            connected,
            halfWidthCanvas,
            junctionRadius,
            Math.max(0.0, cornerRadiusBlocks) * scale,
            RoadJunctionCenterlineResolver.forNetwork(network),
            network,
            config,
            scale);
    }

    private static ToDoubleFunction<RoadEdge> canvasHalfWidth(
            RoadNetwork network,
            RoadSystemConfig config,
            double canvasScale) {
        double scale = canvasScale > 1e-9 ? canvasScale : 1.0;
        return edge -> RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()) * scale;
    }

    private static List<RoadEdge> connectedEdges(
            RoadNetwork network,
            RoadNode node,
            String excludedRoadId) {
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (excludedRoadId != null
                    && edge.getRoadId() != null
                    && excludedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            edges.add(edge);
        }
        return edges;
    }

    private static double clampSkip(double skip, double edgeLength) {
        if (!Double.isFinite(skip) || skip <= 0.0) {
            return 0.0;
        }
        return Math.min(skip, edgeLength);
    }

}
