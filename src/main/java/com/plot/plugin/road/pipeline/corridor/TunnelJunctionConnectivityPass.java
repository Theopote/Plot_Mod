package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelStyle;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
            List<TunnelApproach> approaches = collectTunnelApproaches(
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

            for (List<TunnelApproach> layerApproaches : groupApproachesBySpatialBand(approaches)) {
                Collection<RoadGenerationResult> approachResults =
                    approachEdgeResults(layerApproaches, edgeResults);
                Set<BlockPos> openVolume = collectOpenVolume(
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

                List<BlockPos> seeds = collectApproachEntrySeeds(
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

    private static List<List<TunnelApproach>> groupApproachesBySpatialBand(List<TunnelApproach> approaches) {
        List<List<TunnelApproach>> groups = new ArrayList<>();
        for (TunnelApproach approach : approaches) {
            int[] band = approachYBand(approach);
            int clearHeight = approach.tunnelStyle().clearHeight();
            boolean placed = false;
            for (List<TunnelApproach> group : groups) {
                if (groupSharesSpatialBand(approach, band, clearHeight, group)) {
                    group.add(approach);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<TunnelApproach> newGroup = new ArrayList<>();
                newGroup.add(approach);
                groups.add(newGroup);
            }
        }
        return groups.stream().filter(group -> group.size() >= 2).toList();
    }

    private static int[] approachYBand(TunnelApproach approach) {
        TunnelCrossSectionMask mask = buildMask(approach);
        if (mask == null) {
            return new int[] {approach.roadY(), approach.roadY()};
        }
        return new int[] {mask.minY(), mask.maxY()};
    }

    private static boolean groupSharesSpatialBand(
            TunnelApproach candidate,
            int[] candidateBand,
            int candidateClearHeight,
            List<TunnelApproach> group) {
        for (TunnelApproach member : group) {
            int[] memberBand = approachYBand(member);
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
            List<TunnelApproach> approaches,
            Map<String, RoadGenerationResult> edgeResults) {
        Set<RoadGenerationResult> results = new LinkedHashSet<>();
        for (TunnelApproach approach : approaches) {
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

    private static List<TunnelApproach> collectTunnelApproaches(
            RoadNetwork network,
            RoadNode node,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator) {
        List<TunnelApproach> approaches = new ArrayList<>();
        Integer junctionY = nodeElevations.get(node.getId());
        if (junctionY == null) {
            return approaches;
        }
        Vec2d nodeCenter = node.getPosition();
        if (nodeCenter == null) {
            return approaches;
        }
        String elevatedRoadId = node.isGradeSeparated() ? node.getElevatedRoadId() : null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            RoadGenerationResult result = edgeResults.get(edgeId);
            if (edge == null || result == null) {
                continue;
            }
            List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(
                network, edge, config.getPathSampleDistance());
            if (centerline.size() < 2) {
                continue;
            }
            List<PathSegment> segments = segmentsFromNode(centerline, edge, node.getId());
            double unitsPerBlock = unitsPerBlockEstimator != null
                ? unitsPerBlockEstimator.apply(centerline, segments)
                : 1.0;
            if (!Double.isFinite(unitsPerBlock) || unitsPerBlock <= EPSILON) {
                unitsPerBlock = 1.0;
            }
            if (!tunnelAtNode(network, node, edge, result, config, centerline, segments, unitsPerBlock)) {
                continue;
            }
            if (node.isStackGradeSeparated() && node.isStackUpperEdge(edgeId)) {
                continue;
            }
            if (elevatedRoadId != null
                    && edge.getRoadId() != null
                    && elevatedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            CrossSectionBuildContext crossSections = CrossSectionBuildContext.forEdge(network, edge, config);
            ResolvedCrossSection crossSection = crossSections.resolve(crossSections.segmentStartStation());
            Road road = RoadModelUtils.resolveRoad(network, edge);
            ResolvedTunnelStyle tunnelStyle = TunnelStyle.resolve(road, config);
            approaches.add(new TunnelApproach(
                edge,
                node.getId(),
                centerline,
                crossSections,
                unitsPerBlock,
                crossSection,
                tunnelStyle,
                junctionY));
        }
        return approaches;
    }

    private static List<PathSegment> segmentsFromNode(
            List<Vec2d> centerline,
            RoadEdge edge,
            String nodeId) {
        List<PathSegment> segments = new ArrayList<>();
        if (edge.getStartNodeId().equals(nodeId)) {
            for (int i = 0; i < centerline.size() - 1; i++) {
                segments.add(new PathSegment(centerline.get(i), centerline.get(i + 1)));
            }
            return segments;
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            for (int i = centerline.size() - 1; i > 0; i--) {
                segments.add(new PathSegment(centerline.get(i), centerline.get(i - 1)));
            }
        }
        return segments;
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

    private static Set<BlockPos> collectOpenVolume(
            List<TunnelApproach> approaches,
            List<Vec2d> polygon,
            Collection<RoadGenerationResult> edgeResults,
            ICoordinateService coordinateTransformer) {
        Set<BlockPos> sampledAir = new HashSet<>();
        for (TunnelApproach approach : approaches) {
            sampledAir.addAll(collectSampledApproachAir(approach, polygon, coordinateTransformer));
        }
        Set<BlockPos> dilatedAir = dilateBlockPositions(sampledAir);
        Set<BlockPos> openVolume = new HashSet<>(sampledAir);
        openVolume.addAll(collectBlockingLining(
            approaches, polygon, edgeResults, dilatedAir, coordinateTransformer));
        return openVolume;
    }

    private static Set<BlockPos> dilateBlockPositions(Set<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return Set.of();
        }
        Set<BlockPos> dilated = new HashSet<>(positions);
        int[][] offsets = {
            {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1},
            {-1, -1, 0}, {-1, 1, 0}, {1, -1, 0}, {1, 1, 0},
            {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1},
            {0, -1, -1}, {0, -1, 1}, {0, 1, -1}, {0, 1, 1}
        };
        for (BlockPos pos : positions) {
            for (int[] offset : offsets) {
                dilated.add(pos.add(offset[0], offset[1], offset[2]));
            }
        }
        return dilated;
    }

    private static Set<BlockPos> collectSampledApproachAir(
            TunnelApproach approach,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer) {
        Set<BlockPos> positions = new HashSet<>();
        TunnelCrossSectionMask mask = buildMask(approach);
        if (mask == null) {
            return positions;
        }
        boolean chainForward = approach.crossSections().samplingOriented().forward();
        double scale = approach.unitsPerBlock();
        boolean leftPolygon = false;
        for (PathSegment segment : segmentsFromNode(approach.centerline(), approach.edge(), approach.nodeId())) {
            if (leftPolygon) {
                break;
            }
            Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                if (j > 0 && !RoadGeometryUtils.pointInPolygon(center, polygon)) {
                    leftPolygon = true;
                    break;
                }
                projectMaskAir(mask, center, leftNormal, scale, polygon, coordinateTransformer, positions);
            }
        }
        return positions;
    }

    private static Set<BlockPos> collectBlockingLining(
            List<TunnelApproach> approaches,
            List<Vec2d> polygon,
            Collection<RoadGenerationResult> edgeResults,
            Set<BlockPos> dilatedAir,
            ICoordinateService coordinateTransformer) {
        Set<BlockPos> positions = new HashSet<>();
        if (dilatedAir.isEmpty()) {
            return positions;
        }
        for (RoadGenerationResult result : edgeResults) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (!dilatedAir.contains(pos)) {
                    continue;
                }
                if (!matchesAnyApproachYBand(approaches, pos.getY())) {
                    continue;
                }
                if (!isTunnelLining(result, pos, entry.getValue())) {
                    continue;
                }
                Vec2d planPoint = blockToPlan(pos, coordinateTransformer);
                if (!RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
                    continue;
                }
                positions.add(pos);
            }
        }
        return positions;
    }

    private static boolean matchesAnyApproachYBand(List<TunnelApproach> approaches, int y) {
        for (TunnelApproach approach : approaches) {
            int[] band = approachYBand(approach);
            if (y >= band[0] && y <= band[1]) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> collectApproachEntrySeeds(
            List<TunnelApproach> approaches,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer) {
        List<BlockPos> seeds = new ArrayList<>();
        for (TunnelApproach approach : approaches) {
            seeds.addAll(collectFirstFrameApproachAir(approach, polygon, coordinateTransformer));
        }
        return seeds;
    }

    private static List<BlockPos> collectFirstFrameApproachAir(
            TunnelApproach approach,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer) {
        List<BlockPos> positions = new ArrayList<>();
        TunnelCrossSectionMask mask = buildMask(approach);
        if (mask == null) {
            return positions;
        }
        List<PathSegment> segments = segmentsFromNode(approach.centerline(), approach.edge(), approach.nodeId());
        if (segments.isEmpty()) {
            return positions;
        }
        PathSegment segment = segments.getFirst();
        boolean chainForward = approach.crossSections().samplingOriented().forward();
        Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
        double scale = approach.unitsPerBlock();
        Set<BlockPos> unique = new HashSet<>();
        projectMaskAir(mask, segment.start, leftNormal, scale, polygon, coordinateTransformer, unique);
        positions.addAll(unique);
        return positions;
    }

    private static TunnelCrossSectionMask buildMask(TunnelApproach approach) {
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(approach.crossSection());
        if (envelopeWidth <= 0) {
            return null;
        }
        return TunnelCrossSectionMask.build(
            approach.tunnelStyle().shape(),
            envelopeWidth,
            approach.tunnelStyle().sideClearance(),
            approach.tunnelStyle().clearHeight(),
            approach.tunnelStyle().liningThickness(),
            approach.roadY(),
            false);
    }

    private static void projectMaskAir(
            TunnelCrossSectionMask mask,
            Vec2d center,
            Vec2d leftNormal,
            double scale,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer,
            Set<BlockPos> positions) {
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        mask.forEach((lateral, y, kind) -> {
            if (kind != TunnelCrossSectionMask.CellKind.AIR) {
                return;
            }
            Vec2d planPoint = center.add(normal.multiply(lateral * scale));
            if (!RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
                return;
            }
            positions.add(toBlockPos(planPoint, y, coordinateTransformer));
        });
    }

    private static BlockPos toBlockPos(Vec2d planPoint, int y, ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            return RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer).withY(y);
        }
        return new BlockPos((int) Math.round(planPoint.x), y, (int) Math.round(planPoint.y));
    }

    private static Vec2d blockToPlan(BlockPos pos, ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            Vec2d world = new Vec2d(pos.getX(), pos.getZ());
            com.plot.api.world.WorldProjectionSnapshot snapshot = coordinateTransformer.captureProjection();
            if (snapshot != null && snapshot.isValid()) {
                return snapshot.toCanvas(world);
            }
        }
        return new Vec2d(pos.getX(), pos.getZ());
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
        if (!isTunnelLining(result, pos, existing)) {
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

    private static boolean isTunnelLining(
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

    private record TunnelApproach(
            RoadEdge edge,
            String nodeId,
            List<Vec2d> centerline,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            ResolvedCrossSection crossSection,
            ResolvedTunnelStyle tunnelStyle,
            int roadY) {
    }
}
