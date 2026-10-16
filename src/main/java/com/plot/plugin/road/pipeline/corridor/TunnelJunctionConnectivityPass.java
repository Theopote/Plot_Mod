package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 平交路口处合并多条隧道边的洞身空腔，移除相互阻挡的衬砌。
 */
public final class TunnelJunctionConnectivityPass {
    private static final double EPSILON = 1e-9;

    /**
     * Resolves the pre-construction block id at a world position (typically
     * {@link com.plot.api.world.IBlockProjectionService#getBlockIdAt}).
     * Must return the real world/snapshot id — never invent a material such as stone.
     */
    @FunctionalInterface
    public interface PreviousBlockResolver {
        String resolve(BlockPos pos);
    }

    private TunnelJunctionConnectivityPass() {
    }

    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator) {
        apply(network, edgeResults, nodeElevations, config, coordinateTransformer, unitsPerBlockEstimator, null, null);
    }

    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            PreviousBlockResolver previousBlockResolver) {
        apply(
            network,
            edgeResults,
            nodeElevations,
            config,
            coordinateTransformer,
            unitsPerBlockEstimator,
            previousBlockResolver,
            null);
    }

    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            PreviousBlockResolver previousBlockResolver,
            Map<String, JunctionConstructionReport> reportSink) {
        if (network == null || edgeResults == null || edgeResults.isEmpty() || config == null) {
            return;
        }
        Map<String, Integer> elevations = nodeElevations != null ? nodeElevations : Map.of();
        for (RoadNode node : network.getNodes().values()) {
            if (!qualifiesForConnectivity(node)) {
                continue;
            }
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches =
                TunnelJunctionSeedResolver.collectTunnelApproaches(
                network,
                node,
                edgeResults,
                elevations,
                config,
                unitsPerBlockEstimator);
            if (approaches.size() < 2) {
                mergeTunnelReport(reportSink, node.getId(), 0, 0, false, 0);
                continue;
            }
            List<Vec2d> polygon = buildJunctionPolygon(network, node, config);
            if (polygon.size() < 3) {
                mergeTunnelReport(reportSink, node.getId(), 0, 0, false, 0);
                continue;
            }

            int blockedAir = 0;
            int protectedConflicts = 0;
            boolean connectivityApplied = false;
            int disconnectedComponents = 0;

            for (List<TunnelJunctionSeedResolver.TunnelApproach> layerApproaches
                    : groupApproachesBySpatialBand(approaches)) {
                Collection<RoadGenerationResult> approachResults =
                    approachEdgeResults(layerApproaches, edgeResults);
                Set<BlockPos> openVolume = TunnelJunctionSeedResolver.collectOpenVolume(
                    layerApproaches,
                    polygon,
                    approachResults,
                    coordinateTransformer);
                if (openVolume.isEmpty()) {
                    continue;
                }
                connectivityApplied = true;
                OpenVolumeStats stats = openVolume(
                    approachResults, openVolume, previousBlockResolver);
                blockedAir += stats.blockedByProtection;
                protectedConflicts += stats.protectedConflicts;

                List<BlockPos> seeds = TunnelJunctionSeedResolver.collectApproachEntrySeeds(
                    layerApproaches, polygon, coordinateTransformer);
                TunnelJunctionAirConnectivityValidator.Result flood =
                    TunnelJunctionAirConnectivityValidator.validate(openVolume, seeds);
                disconnectedComponents = Math.max(disconnectedComponents, flood.disconnectedComponentCount());
            }

            mergeTunnelReport(
                reportSink,
                node.getId(),
                blockedAir,
                protectedConflicts,
                connectivityApplied,
                disconnectedComponents);
        }
    }

    private static void mergeTunnelReport(
            Map<String, JunctionConstructionReport> reportSink,
            String nodeId,
            int blockedAir,
            int protectedConflicts,
            boolean connectivityApplied,
            int disconnectedComponents) {
        if (reportSink == null) {
            return;
        }
        JunctionConstructionReport overlay = JunctionConstructionReport.empty(nodeId)
            .withTunnelConnectivity(
                blockedAir,
                protectedConflicts,
                connectivityApplied,
                disconnectedComponents);
        reportSink.merge(nodeId, overlay, JunctionConstructionReport::merge);
    }

    private static List<List<TunnelJunctionSeedResolver.TunnelApproach>> groupApproachesBySpatialBand(
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches) {
        List<List<TunnelJunctionSeedResolver.TunnelApproach>> groups = new ArrayList<>();
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            int[] band = TunnelJunctionSeedResolver.approachYBand(approach);
            int clearHeight = approach.tunnelStyle().clearHeight();
            boolean placed = false;
            for (List<TunnelJunctionSeedResolver.TunnelApproach> group : groups) {
                if (groupSharesSpatialBand(approach, band, clearHeight, group)) {
                    group.add(approach);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<TunnelJunctionSeedResolver.TunnelApproach> newGroup = new ArrayList<>();
                newGroup.add(approach);
                groups.add(newGroup);
            }
        }
        return groups.stream().filter(group -> group.size() >= 2).toList();
    }

    private static boolean groupSharesSpatialBand(
            TunnelJunctionSeedResolver.TunnelApproach candidate,
            int[] candidateBand,
            int candidateClearHeight,
            List<TunnelJunctionSeedResolver.TunnelApproach> group) {
        for (TunnelJunctionSeedResolver.TunnelApproach member : group) {
            int[] memberBand = TunnelJunctionSeedResolver.approachYBand(member);
            int minOverlap = Math.min(candidateClearHeight, member.tunnelStyle().clearHeight());
            if (bandsOverlap(candidateBand, memberBand, minOverlap)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bandsOverlap(int[] a, int[] b, int minOverlap) {
        int overlapStart = Math.max(a[0], b[0]);
        int overlapEnd = Math.min(a[1], b[1]);
        return overlapEnd - overlapStart + 1 >= minOverlap;
    }

    private static Collection<RoadGenerationResult> approachEdgeResults(
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches,
            Map<String, RoadGenerationResult> edgeResults) {
        Set<RoadGenerationResult> results = new LinkedHashSet<>();
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            RoadGenerationResult result = edgeResults.get(approach.edge().getId());
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    private static boolean qualifiesForConnectivity(RoadNode node) {
        return node != null && node.getDegree() >= 3;
    }

    /**
     * True only when tunnel-family construction exists within the node approach window,
     * not merely anywhere on the edge.
     */
    static boolean tunnelAtNode(
            RoadNetwork network,
            RoadNode node,
            RoadEdge edge,
            RoadGenerationResult result,
            RoadSystemConfig config,
            List<Vec2d> centerline,
            List<PathSegment> approachSegments,
            double unitsPerBlock) {
        if (node == null || edge == null || result == null || centerline == null || approachSegments.isEmpty()) {
            return false;
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            List.of(edge),
            e -> RoadModelUtils.getEffectiveWidth(network, e, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double window = Math.max(
            junctionRadius,
            Math.max(
                RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS * unitsPerBlock,
                approachSegments.getFirst().distance));

        boolean fromStart = edge.getStartNodeId().equals(node.getId());
        double accumulated = 0.0;
        if (!result.constructionTypes.isEmpty()) {
            for (int i = 0; i < approachSegments.size() && accumulated <= window + EPSILON; i++) {
                accumulated += approachSegments.get(i).distance;
                int typeIndex = fromStart ? i : (centerline.size() - 2 - i);
                RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(
                    result.constructionTypes, typeIndex);
                if (type.isTunnelFamily()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<Vec2d> buildJunctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config) {
        List<RoadEdge> connected = connectedEdges(network, node);
        if (connected.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
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

    private record OpenVolumeStats(int blockedByProtection, int protectedConflicts) {
    }

    private static OpenVolumeStats openVolume(
            Collection<RoadGenerationResult> edgeResults,
            Set<BlockPos> openVolume,
            PreviousBlockResolver previousBlockResolver) {
        if (edgeResults.isEmpty()) {
            return new OpenVolumeStats(0, 0);
        }
        int blocked = 0;
        int protectedConflicts = 0;
        for (BlockPos pos : openVolume) {
            if (hasProtectedPlacement(edgeResults, pos)) {
                protectedConflicts++;
                blocked++;
                continue;
            }
            boolean hasAir = false;
            for (RoadGenerationResult result : edgeResults) {
                if (replaceTunnelLiningWithAir(result, pos, edgeResults, previousBlockResolver)) {
                    hasAir = true;
                    continue;
                }
                BlockRecord existing = result.placementRecords.get(pos);
                if (existing != null && "minecraft:air".equals(existing.newBlockId)) {
                    hasAir = true;
                }
            }
            if (hasAir) {
                continue;
            }
            String previous = resolvePreviousBlockId(pos, null, edgeResults, previousBlockResolver);
            RoadGenerationResult carrier = edgeResults.iterator().next();
            carrier.recordPlacementOverride(pos, previous, "minecraft:air");
            carrier.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
            carrier.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
            if (!carrier.tunnelBlocks.contains(pos)) {
                carrier.tunnelBlocks.add(pos);
            }
        }
        return new OpenVolumeStats(blocked, protectedConflicts);
    }

    static String resolvePreviousBlockId(
            BlockPos pos,
            BlockRecord existing,
            Collection<RoadGenerationResult> edgeResults,
            PreviousBlockResolver previousBlockResolver) {
        String fromExisting = validPrevious(existing != null ? existing.previousBlockId : null);
        if (fromExisting != null) {
            return fromExisting;
        }
        if (edgeResults != null) {
            for (RoadGenerationResult result : edgeResults) {
                BlockRecord record = result.placementRecords.get(pos);
                String fromPeer = validPrevious(record != null ? record.previousBlockId : null);
                if (fromPeer != null) {
                    return fromPeer;
                }
            }
        }
        if (previousBlockResolver != null) {
            return validPrevious(previousBlockResolver.resolve(pos));
        }
        return null;
    }

    private static String validPrevious(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        return blockId;
    }

    private static boolean replaceTunnelLiningWithAir(
            RoadGenerationResult result,
            BlockPos pos,
            Collection<RoadGenerationResult> edgeResults,
            PreviousBlockResolver previousBlockResolver) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing == null) {
            return false;
        }
        if (!isTunnelLiningRecord(result, pos, existing)) {
            return "minecraft:air".equals(existing.newBlockId);
        }
        String previous = resolvePreviousBlockId(pos, existing, edgeResults, previousBlockResolver);
        if (validPrevious(existing.previousBlockId) == null && previous != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, previous, "minecraft:air"));
        } else {
            result.recordPlacementOverride(pos, previous, "minecraft:air");
        }
        result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
        return true;
    }

    static boolean hasProtectedPlacement(Collection<RoadGenerationResult> edgeResults, BlockPos pos) {
        if (edgeResults == null) {
            return false;
        }
        for (RoadGenerationResult result : edgeResults) {
            BlockRecord record = result.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = result.placementLayers.get(pos);
            if (layer == null) {
                continue;
            }
            if (layer == RoadSolidLayer.ROAD
                    || layer == RoadSolidLayer.BRIDGE
                    || layer == RoadSolidLayer.MARKING
                    || layer == RoadSolidLayer.SIDEWALK
                    || layer == RoadSolidLayer.SHOULDER
                    || layer == RoadSolidLayer.BIKE_LANE
                    || layer == RoadSolidLayer.MEDIAN
                    || layer == RoadSolidLayer.GUARDRAIL) {
                return true;
            }
        }
        return false;
    }

    static boolean isTunnelLiningRecord(
            RoadGenerationResult result,
            BlockPos pos,
            BlockRecord record) {
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = result.placementLayers.get(pos);
        if (layer == RoadSolidLayer.ROAD
                || layer == RoadSolidLayer.BRIDGE
                || layer == RoadSolidLayer.MARKING
                || layer == RoadSolidLayer.SIDEWALK
                || layer == RoadSolidLayer.SHOULDER
                || layer == RoadSolidLayer.BIKE_LANE
                || layer == RoadSolidLayer.MEDIAN
                || layer == RoadSolidLayer.GUARDRAIL) {
            return false;
        }
        Integer priority = result.placementMaterialPriorities.get(pos);
        if (priority != null && priority >= RoadSolidMaterialPriority.DECORATIVE) {
            return false;
        }
        if (layer == RoadSolidLayer.TUNNEL) {
            return true;
        }
        return result.tunnelBlocks.contains(pos);
    }
}
