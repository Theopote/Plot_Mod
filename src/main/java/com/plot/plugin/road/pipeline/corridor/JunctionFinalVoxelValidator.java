package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Post-merge validation: aggregate placement records must match junction design intent.
 */
public final class JunctionFinalVoxelValidator {
    private JunctionFinalVoxelValidator() {
    }

    public record PostMergeReport(
            int missingSurfaceColumns,
            int protectedLosses,
            int disconnectedTunnelComponents,
            int crossLayerLeaks) {
        public static final PostMergeReport CLEAN = new PostMergeReport(0, 0, 0, 0);
    }

    public static PostMergeReport validateNode(
            RoadNetwork network,
            RoadNode node,
            RoadGenerationResult aggregate,
            int junctionY,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer) {
        if (network == null || node == null || aggregate == null || config == null) {
            return PostMergeReport.CLEAN;
        }
        List<RoadEdge> edges = connectedEdges(network, node);
        if (edges.isEmpty()) {
            return PostMergeReport.CLEAN;
        }
        List<Vec2d> polygon = buildPolygon(network, node, edges, config);
        if (polygon.size() < 3) {
            return PostMergeReport.CLEAN;
        }
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(polygon, coordinateTransformer);
        int missingSurface = 0;
        int protectedLosses = 0;
        Set<BlockPos> tunnelAir = new HashSet<>();
        for (Vec2d cell : cells) {
            BlockPos surfacePos = toBlockPos(cell, junctionY, coordinateTransformer);
            if (!hasSurfaceLayer(aggregate, surfacePos)) {
                missingSurface++;
            }
            collectTunnelAirInColumn(aggregate, surfacePos, tunnelAir);
        }
        protectedLosses = countProtectedLosses(aggregate, tunnelAir);
        int crossLayerLeaks = countCrossLayerLeaks(aggregate, node, junctionY);
        int disconnected = 0;
        if (tunnelAir.size() > 1) {
            TunnelJunctionAirConnectivityValidator.Result flood =
                TunnelJunctionAirConnectivityValidator.validate(tunnelAir, new ArrayList<>(tunnelAir));
            disconnected = flood.disconnectedComponentCount();
        }
        return new PostMergeReport(missingSurface, protectedLosses, disconnected, crossLayerLeaks);
    }

    private static boolean hasSurfaceLayer(RoadGenerationResult aggregate, BlockPos pos) {
        BlockRecord record = aggregate.placementRecords.get(pos);
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = aggregate.placementLayers.get(pos);
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN;
    }

    private static void collectTunnelAirInColumn(
            RoadGenerationResult aggregate,
            BlockPos surfacePos,
            Set<BlockPos> tunnelAir) {
        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != surfacePos.getX() || pos.getZ() != surfacePos.getZ()) {
                continue;
            }
            BlockRecord record = entry.getValue();
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                RoadSolidLayer layer = aggregate.placementLayers.get(pos);
                if (layer == RoadSolidLayer.TUNNEL || aggregate.tunnelBlocks.contains(pos)) {
                    tunnelAir.add(pos);
                }
            }
        }
    }

    private static int countProtectedLosses(RoadGenerationResult aggregate, Set<BlockPos> tunnelAir) {
        int losses = 0;
        for (BlockPos pos : tunnelAir) {
            if (TunnelJunctionConnectivityPass.hasProtectedPlacement(List.of(aggregate), pos)) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                RoadSolidLayer layer = aggregate.placementLayers.get(pos);
                if (layer == RoadSolidLayer.ROAD || layer == RoadSolidLayer.BRIDGE) {
                    losses++;
                }
            }
        }
        return losses;
    }

    private static int countCrossLayerLeaks(RoadGenerationResult aggregate, RoadNode node, int junctionY) {
        if (!node.isGradeSeparated()) {
            return 0;
        }
        int leaks = 0;
        int upperBandStart = junctionY + 6;
        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() < upperBandStart) {
                continue;
            }
            BlockRecord record = entry.getValue();
            if (record == null || !"minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.TUNNEL) {
                leaks++;
            }
        }
        return leaks;
    }

    private static List<Vec2d> buildPolygon(
            RoadNetwork network,
            RoadNode node,
            List<RoadEdge> edges,
            RoadSystemConfig config) {
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network, config.getPathSampleDistance()));
    }

    private static List<RoadEdge> connectedEdges(RoadNetwork network, RoadNode node) {
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private static BlockPos toBlockPos(Vec2d planPoint, int y, ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            return RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer).withY(y);
        }
        return new BlockPos((int) Math.round(planPoint.x), y, (int) Math.round(planPoint.y));
    }
}
