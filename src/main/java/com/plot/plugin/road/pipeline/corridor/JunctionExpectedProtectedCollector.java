package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
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
        double clearance = node.isStackGradeSeparated()
            ? (node.getStackCrossingClearance() != null
                ? node.getStackCrossingClearance()
                : config.getDefaultCrossingClearance())
            : (node.getCrossingClearance() != null
                ? node.getCrossingClearance()
                : config.getDefaultCrossingClearance());
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
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            if (elevatedRoadId != null
                    && connectedEdge.getRoadId() != null
                    && elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            connected.add(connectedEdge);
        }
        if (connected.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));
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
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }

    private static boolean isProtectedLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.GUARDRAIL
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN;
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
