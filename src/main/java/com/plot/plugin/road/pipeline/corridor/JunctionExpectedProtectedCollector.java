package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.GradeSeparationClearance;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects upper-road protected placements from edge results before junction merge.
 */
public final class JunctionExpectedProtectedCollector {
    private JunctionExpectedProtectedCollector() {
    }

    public record UpperRoadProtectionBounds(List<Vec2d> polygon, int floorY) {
    }

    public static List<BlockPos> collect(
            RoadNetwork network,
            RoadNode node,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            GradeSeparationBuildContextFactory.ElevatedRoadIdResolver elevatedRoadIdResolver) {
        return collect(network, node, edgeResults, nodeElevations, config, coordinateTransformer,
            elevatedRoadIdResolver, Set.of());
    }

    public static List<BlockPos> collect(
            RoadNetwork network,
            RoadNode node,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            GradeSeparationBuildContextFactory.ElevatedRoadIdResolver elevatedRoadIdResolver,
            Set<ColumnKey> constructionColumns) {
        UpperRoadProtectionBounds bounds = resolveUpperRoadBounds(
            network, node, nodeElevations, config, elevatedRoadIdResolver);
        if (bounds == null || edgeResults == null || edgeResults.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> protectedBlocks = new LinkedHashSet<>();
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network) : node.getElevatedRoadId();
        int underpassY = nodeElevations.get(node.getId());
        for (var resultEntry : edgeResults.entrySet()) {
            var edge = network.getEdge(resultEntry.getKey());
            if (edge == null || (node.isStackGradeSeparated() ? !node.isStackUpperEdge(edge.getId())
                    : !java.util.Objects.equals(elevatedRoadId, edge.getRoadId()))) continue;
            RoadGenerationResult result = resultEntry.getValue();
            if (result == null || result.placementRecords == null) {
                continue;
            }
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (pos == null || record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                if (pos.getY() <= underpassY) {
                    continue;
                }
                RoadSolidLayer layer = result.placementLayers.get(pos);
                if (!isProtectedLayer(layer)) {
                    continue;
                }
                Vec2d planPoint = blockToPlan(pos, coordinateTransformer);
                if (constructionColumns.contains(new ColumnKey(pos.getX(), pos.getZ()))
                        || RoadGeometryUtils.pointInPolygon(planPoint, bounds.polygon())) {
                    protectedBlocks.add(pos.toImmutable());
                }
            }
        }
        return List.copyOf(protectedBlocks);
    }

    public static UpperRoadProtectionBounds resolveUpperRoadBounds(
            RoadNetwork network,
            RoadNode node,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            GradeSeparationBuildContextFactory.ElevatedRoadIdResolver elevatedRoadIdResolver) {
        if (network == null || node == null || config == null || nodeElevations == null) {
            return null;
        }
        if (!node.isGradeSeparated() && !node.isStackGradeSeparated()) {
            return null;
        }
        Integer underpassY = nodeElevations.get(node.getId());
        if (underpassY == null) {
            return null;
        }
        double clearance;
        if (node.isStackGradeSeparated()) {
            String roadId = null;
            for (String edgeId : node.getConnectedEdgeIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge != null && edge.getRoadId() != null) {
                    roadId = edge.getRoadId();
                    break;
                }
            }
            clearance = GradeSeparationClearance.resolve(
                node.getStackCrossingClearance(),
                roadId != null ? network.getRoad(roadId) : null,
                config);
        } else {
            String elevatedRoadId = elevatedRoadIdResolver != null
                ? elevatedRoadIdResolver.resolve(node, network)
                : node.getElevatedRoadId();
            clearance = GradeSeparationClearance.forNode(
                node.getCrossingClearance(), node, network, elevatedRoadId, config);
        }
        int floorY = underpassY + (int) Math.ceil(Math.max(1.0, clearance));
        List<Vec2d> polygon = resolveProtectionPolygon(
            network, node, config, elevatedRoadIdResolver);
        if (polygon.size() < 3) {
            return null;
        }
        return new UpperRoadProtectionBounds(List.copyOf(polygon), floorY);
    }

    private static List<Vec2d> resolveProtectionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            GradeSeparationBuildContextFactory.ElevatedRoadIdResolver elevatedRoadIdResolver) {
        if (node.isStackGradeSeparated()) {
            return stackJunctionPolygon(network, node, config);
        }
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network)
            : node.getElevatedRoadId();
        return junctionPolygon(network, node, config, elevatedRoadId);
    }

    private static List<Vec2d> junctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            String elevatedRoadId) {
        List<RoadEdge> underpass = new ArrayList<>();
        List<RoadEdge> allArms = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            allArms.add(connectedEdge);
            if (elevatedRoadId != null
                    && connectedEdge.getRoadId() != null
                    && elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            underpass.add(connectedEdge);
        }
        List<Vec2d> polygon = fillPolygonForEdges(network, node, config, underpass);
        if (polygon.size() >= 3) {
            return polygon;
        }
        // 立交过滤上层后仅两臂时区域式不造虚构平交广场；保护范围改用全部进口（含上层）足迹。
        polygon = fillPolygonForEdges(network, node, config, allArms);
        if (polygon.size() >= 3) {
            return polygon;
        }
        return simpleProtectionEnvelope(node, underpass.isEmpty() ? allArms : underpass, network, config);
    }

    private static List<Vec2d> fillPolygonForEdges(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            List<RoadEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            0.0,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }

    /** 与路口生成器简单包络同口径：按最大硬表面半宽扩圆，保证上层甲板仍有保护列。 */
    private static List<Vec2d> simpleProtectionEnvelope(
            RoadNode node,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (node == null || node.getPosition() == null || edges == null || edges.isEmpty()) {
            return List.of();
        }
        int maxWidth = 5;
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            maxWidth = Math.max(
                maxWidth,
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks());
        }
        int radius = maxWidth + 2;
        Vec2d center = node.getPosition();
        int segments = Math.max(12, radius * 4);
        List<Vec2d> polygon = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            polygon.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)));
        }
        return polygon;
    }

    private static List<Vec2d> stackJunctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config) {
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge != null) {
                connected.add(connectedEdge);
            }
        }
        if (connected.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            0.0,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }

    private static boolean isProtectedLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.SUBGRADE
            || layer == RoadSolidLayer.GUARDRAIL
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING;
    }

    private static Vec2d blockToPlan(BlockPos pos, ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            Vec2d world = new Vec2d(pos.getX(), pos.getZ());
            var snapshot = coordinateTransformer.captureProjection();
            if (snapshot != null && snapshot.isValid()) {
                return snapshot.toCanvas(world);
            }
        }
        return new Vec2d(pos.getX(), pos.getZ());
    }
}
