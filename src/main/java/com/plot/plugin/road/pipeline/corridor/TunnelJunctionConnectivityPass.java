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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 平交路口处合并多条隧道边的洞身空腔，移除相互阻挡的衬砌。
 */
public final class TunnelJunctionConnectivityPass {
    private static final double EPSILON = 1e-9;

    private TunnelJunctionConnectivityPass() {
    }

    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator) {
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
                continue;
            }
            List<Vec2d> polygon = buildJunctionPolygon(network, node, config);
            if (polygon.size() < 3) {
                continue;
            }
            Set<BlockPos> openVolume = collectOpenVolume(
                approaches,
                polygon,
                edgeResults.values(),
                coordinateTransformer);
            if (openVolume.isEmpty()) {
                continue;
            }
            openVolume(edgeResults.values(), openVolume);
        }
    }

    private static boolean qualifiesForConnectivity(RoadNode node) {
        return node != null
            && node.getDegree() >= 3
            && !node.isGradeSeparated()
            && !node.isStackGradeSeparated();
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
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            RoadGenerationResult result = edgeResults.get(edgeId);
            if (edge == null || result == null || !tunnelAtNode(edge, node, result)) {
                continue;
            }
            List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(
                network, edge, config.getPathSampleDistance());
            if (centerline.size() < 2) {
                continue;
            }
            CrossSectionBuildContext crossSections = CrossSectionBuildContext.forEdge(network, edge, config);
            ResolvedCrossSection crossSection = crossSections.resolve(crossSections.segmentStartStation());
            Road road = RoadModelUtils.resolveRoad(network, edge);
            ResolvedTunnelStyle tunnelStyle = TunnelStyle.resolve(road, config);
            List<PathSegment> segments = segmentsFromNode(centerline, edge, node.getId());
            double unitsPerBlock = unitsPerBlockEstimator != null
                ? unitsPerBlockEstimator.apply(centerline, segments)
                : 1.0;
            if (!Double.isFinite(unitsPerBlock) || unitsPerBlock <= EPSILON) {
                unitsPerBlock = 1.0;
            }
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

    private static boolean tunnelAtNode(RoadEdge edge, RoadNode node, RoadGenerationResult result) {
        if (result.tunnelLength <= EPSILON && result.tunnelCount <= 0 && result.tunnelBlocks.isEmpty()) {
            return false;
        }
        if (result.constructionTypes.isEmpty()) {
            return result.tunnelLength > EPSILON || result.tunnelCount > 0;
        }
        if (result.constructionTypes.stream().anyMatch(RoadConstructionType::isTunnelFamily)) {
            return true;
        }
        return !result.tunnelBlocks.isEmpty();
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
        Set<BlockPos> openVolume = new HashSet<>();
        for (TunnelApproach approach : approaches) {
            openVolume.addAll(collectSampledApproachAir(approach, polygon, coordinateTransformer));
        }
        openVolume.addAll(collectBlockingLining(approaches, polygon, edgeResults, coordinateTransformer));
        return openVolume;
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
            ICoordinateService coordinateTransformer) {
        Set<BlockPos> positions = new HashSet<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TunnelApproach approach : approaches) {
            TunnelCrossSectionMask mask = buildMask(approach);
            if (mask == null) {
                continue;
            }
            minY = Math.min(minY, mask.minY());
            maxY = Math.max(maxY, mask.maxY());
        }
        if (minY > maxY) {
            return positions;
        }
        for (RoadGenerationResult result : edgeResults) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getY() < minY || pos.getY() > maxY) {
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

    private static void openVolume(Collection<RoadGenerationResult> edgeResults, Set<BlockPos> openVolume) {
        for (BlockPos pos : openVolume) {
            boolean hasAir = false;
            for (RoadGenerationResult result : edgeResults) {
                if (replaceTunnelLiningWithAir(result, pos)) {
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
            RoadGenerationResult carrier = edgeResults.iterator().next();
            carrier.recordPlacementOverride(pos, null, "minecraft:air");
            carrier.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
            carrier.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
            if (!carrier.tunnelBlocks.contains(pos)) {
                carrier.tunnelBlocks.add(pos);
            }
        }
    }

    private static boolean replaceTunnelLiningWithAir(RoadGenerationResult result, BlockPos pos) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing == null) {
            return false;
        }
        if (!isTunnelLining(result, pos, existing)) {
            return "minecraft:air".equals(existing.newBlockId);
        }
        result.recordPlacementOverride(pos, existing.previousBlockId, "minecraft:air");
        result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
        return true;
    }

    private static boolean isTunnelLining(
            RoadGenerationResult result,
            BlockPos pos,
            BlockRecord record) {
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        Integer priority = result.placementMaterialPriorities.get(pos);
        if (priority != null && priority >= RoadSolidMaterialPriority.DECORATIVE) {
            return false;
        }
        RoadSolidLayer layer = result.placementLayers.get(pos);
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
