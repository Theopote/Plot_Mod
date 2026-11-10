package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
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
 * Shared tunnel approach, portal seed, and open-volume resolution for junction passes.
 */
public final class TunnelJunctionSeedResolver {
    private static final double EPSILON = 1e-9;

    private TunnelJunctionSeedResolver() {
    }

    public record TunnelApproach(
            RoadEdge edge,
            String nodeId,
            List<Vec2d> centerline,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            ResolvedCrossSection crossSection,
            ResolvedTunnelStyle tunnelStyle,
            int roadY) {
    }

    public record UnderpassYBand(int minY, int maxY) {
        public static final UnderpassYBand NONE = new UnderpassYBand(0, 0);

        public boolean isActive() {
            return maxY >= minY;
        }
    }

    public static List<TunnelApproach> collectTunnelApproaches(
            RoadNetwork network,
            RoadNode node,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator) {
        List<TunnelApproach> approaches = new ArrayList<>();
        if (network == null || node == null || edgeResults == null || config == null) {
            return approaches;
        }
        Integer junctionY = nodeElevations != null ? nodeElevations.get(node.getId()) : null;
        if (junctionY == null || node.getPosition() == null) {
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
            if (!TunnelJunctionConnectivityPass.tunnelAtNode(
                    network, node, edge, result, config, centerline, segments, unitsPerBlock)) {
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
                edge, node.getId(), centerline, crossSections, unitsPerBlock,
                crossSection, tunnelStyle, junctionY));
        }
        return approaches;
    }

    public static List<TunnelPortalSeed> collectPortalSeeds(
            List<TunnelApproach> approaches,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer) {
        if (approaches == null || approaches.isEmpty() || polygon == null || polygon.size() < 3) {
            return List.of();
        }
        List<TunnelPortalSeed> seeds = new ArrayList<>();
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (TunnelApproach approach : approaches) {
            for (BlockPos pos : collectFirstFrameApproachAir(approach, polygon, coordinateTransformer)) {
                if (unique.add(pos)) {
                    seeds.add(new TunnelPortalSeed(approach.edge().getId(), pos));
                }
            }
        }
        return List.copyOf(seeds);
    }

    /**
     * 平交广场净空绝对上限：各隧道进口设计洞腔顶（roadY+clearHeight）的最小值。
     * <p>
     * 不含衬砌壳（{@code mask.maxY} 含 lining）；广场 {@code punchColumn} 无掩膜过滤，
     * 必须停在洞腔顶，否则会把洞顶衬砌与覆土当「上方障碍」掏穿成十字露天边坡。
     */
    public static Integer resolveTunnelCavityClearCap(List<TunnelApproach> approaches) {
        Integer clearHeight = resolveTunnelClearHeight(approaches);
        if (clearHeight == null || approaches == null || approaches.isEmpty()) {
            return null;
        }
        int cap = Integer.MAX_VALUE;
        for (TunnelApproach approach : approaches) {
            if (approach == null || approach.tunnelStyle() == null) {
                continue;
            }
            cap = Math.min(cap, approach.roadY() + clearHeight);
        }
        return cap == Integer.MAX_VALUE ? null : cap;
    }

    /**
     * 各隧道进口净空高度取更严（更小）者；用于逐列洞顶 = loft 路面 Y + clearHeight。
     */
    public static Integer resolveTunnelClearHeight(List<TunnelApproach> approaches) {
        if (approaches == null || approaches.isEmpty()) {
            return null;
        }
        int clear = Integer.MAX_VALUE;
        for (TunnelApproach approach : approaches) {
            if (approach == null || approach.tunnelStyle() == null) {
                continue;
            }
            clear = Math.clamp(approach.tunnelStyle().clearHeight(), 1, clear);
        }
        return clear == Integer.MAX_VALUE ? null : clear;
    }

    /**
     * 各隧道进口衬砌厚度取更严（更大）者；与壳侧封闭覆土判定对齐。
     */
    public static int resolveTunnelLiningThickness(List<TunnelApproach> approaches) {
        int lining = 1;
        if (approaches == null || approaches.isEmpty()) {
            return lining;
        }
        for (TunnelApproach approach : approaches) {
            if (approach == null || approach.tunnelStyle() == null) {
                continue;
            }
            lining = Math.max(lining, Math.max(1, approach.tunnelStyle().liningThickness()));
        }
        return lining;
    }

    /**
     * 边结果是否含隧道族施工（不限节点邻域）。仅用于洞顶 cap 兜底，不提升为隧道进口。
     */
    static boolean edgeHasTunnelFamilyConstruction(RoadGenerationResult result) {
        if (result == null) {
            return false;
        }
        if (result.constructionTypes != null) {
            for (var type : result.constructionTypes) {
                if (type != null && type.isTunnelFamily()) {
                    return true;
                }
            }
        }
        return result.tunnelBlocks != null && !result.tunnelBlocks.isEmpty();
    }

    /**
     * 进口窗口漏检时，用相连边的隧道预设推断广场洞顶 cap，避免无 cap 掏穿覆土。
     * 不把这些边登记为 {@link TunnelApproach}，以免误禁露天圆角边坡。
     */
    public static Integer inferTunnelCavityClearCapFromEdges(
            RoadNetwork network,
            List<RoadEdge> edges,
            Map<String, RoadGenerationResult> edgeResults,
            int junctionY,
            RoadSystemConfig config) {
        if (network == null || edges == null || edges.isEmpty() || edgeResults == null || config == null) {
            return null;
        }
        int cap = Integer.MAX_VALUE;
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            RoadGenerationResult result = edgeResults.get(edge.getId());
            if (!edgeHasTunnelFamilyConstruction(result)) {
                continue;
            }
            Road road = RoadModelUtils.resolveRoad(network, edge);
            ResolvedTunnelStyle style = TunnelStyle.resolve(road, config);
            if (style == null) {
                continue;
            }
            cap = Math.min(cap, junctionY + Math.max(1, style.clearHeight()));
        }
        return cap == Integer.MAX_VALUE ? null : cap;
    }

    public static UnderpassYBand resolveUnderpassYBand(List<TunnelApproach> approaches) {
        if (approaches == null || approaches.isEmpty()) {
            return UnderpassYBand.NONE;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TunnelApproach approach : approaches) {
            int[] band = approachYBand(approach);
            minY = Math.min(minY, band[0]);
            maxY = Math.max(maxY, band[1]);        }
        if (minY == Integer.MAX_VALUE) {
            return UnderpassYBand.NONE;
        }
        return new UnderpassYBand(minY, maxY);
    }

    public static Set<BlockPos> collectOpenVolume(
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

    public static List<BlockPos> collectApproachEntrySeeds(
            List<TunnelApproach> approaches,
            List<Vec2d> polygon,
            ICoordinateService coordinateTransformer) {
        List<BlockPos> seeds = new ArrayList<>();
        for (TunnelApproach approach : approaches) {
            seeds.addAll(collectFirstFrameApproachAir(approach, polygon, coordinateTransformer));
        }
        return seeds;
    }

    static boolean matchesAnyApproachYBand(List<TunnelApproach> approaches, int y) {
        for (TunnelApproach approach : approaches) {
            int[] band = approachYBand(approach);
            if (y >= band[0] && y <= band[1]) {
                return true;
            }
        }
        return false;
    }

    public static int[] approachYBand(TunnelApproach approach) {
        TunnelCrossSectionMask mask = buildMask(approach);
        if (mask == null) {
            return new int[] {approach.roadY(), approach.roadY()};
        }
        return new int[] {mask.minY(), mask.maxY()};
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
        if (dilatedAir.isEmpty() || edgeResults == null) {
            return positions;
        }
        for (RoadGenerationResult result : edgeResults) {
            if (result == null || result.placementRecords == null) {
                continue;
            }
            for (var entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (!dilatedAir.contains(pos)) {
                    continue;
                }
                if (!matchesAnyApproachYBand(approaches, pos.getY())) {
                    continue;
                }
                if (!TunnelJunctionConnectivityPass.isTunnelLiningRecord(
                        result, pos, entry.getValue())) {
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

    static List<PathSegment> segmentsFromNode(
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
        return planPointForBlock(pos, coordinateTransformer);
    }

    /** 世界方块 → 画布平面点（连通/壳层地表保护共用）。 */
    public static Vec2d planPointForBlock(BlockPos pos, ICoordinateService coordinateTransformer) {
        if (pos == null) {
            return new Vec2d(0, 0);
        }
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
