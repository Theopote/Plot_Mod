package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
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
import com.plot.plugin.road.tunnel.TunnelColumnConstruction;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

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
        apply(network, edgeResults, nodeElevations, config, coordinateTransformer,
            unitsPerBlockEstimator, null, null, null);
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
            null,
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
        apply(
            network,
            edgeResults,
            nodeElevations,
            config,
            coordinateTransformer,
            unitsPerBlockEstimator,
            previousBlockResolver,
            reportSink,
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
            Map<String, JunctionConstructionReport> reportSink,
            TerrainSampler terrain) {
        apply(
            network,
            edgeResults,
            nodeElevations,
            config,
            coordinateTransformer,
            unitsPerBlockEstimator,
            previousBlockResolver,
            reportSink,
            terrain,
            null,
            null);
    }

    /**
     * @param onlyNodeIds 非空时只处理这些路口（分帧预览用）；null 表示全网。
     */
    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            PreviousBlockResolver previousBlockResolver,
            Map<String, JunctionConstructionReport> reportSink,
            TerrainSampler terrain,
            Set<String> onlyNodeIds) {
        apply(
            network,
            edgeResults,
            nodeElevations,
            config,
            coordinateTransformer,
            unitsPerBlockEstimator,
            previousBlockResolver,
            reportSink,
            terrain,
            onlyNodeIds,
            null);
    }

    /**
     * @param onlyNodeIds 非空时只处理这些路口（分帧预览用）；null 表示全网。
     * @param junctionResults 已生成的路口结果；非空时优先复用足迹与权威高程场，避免连通阶段重建。
     */
    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            PreviousBlockResolver previousBlockResolver,
            Map<String, JunctionConstructionReport> reportSink,
            TerrainSampler terrain,
            Set<String> onlyNodeIds,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults) {
        if (network == null || edgeResults == null || edgeResults.isEmpty() || config == null) {
            return;
        }
        Map<String, Integer> elevations = nodeElevations != null ? nodeElevations : Map.of();
        Map<String, RoadJunctionGenerator.JunctionBlocks> junctions =
            junctionResults != null ? junctionResults : Map.of();
        for (RoadNode node : network.getNodes().values()) {
            if (!qualifiesForConnectivity(node)) {
                continue;
            }
            if (onlyNodeIds != null && !onlyNodeIds.contains(node.getId())) {
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
            double canvasScale = 1.0;
            for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
                if (approach != null && approach.unitsPerBlock() > EPSILON) {
                    canvasScale = Math.max(canvasScale, approach.unitsPerBlock());
                }
            }
            // 几何只算一次：多边形从同一结果派生，避免 resolveJunctionPolygon 再跑一遍。
            JunctionGeometryResult geometry = TunnelJunctionShellPass.resolveJunctionGeometry(
                network, node, config, canvasScale);
            List<Vec2d> polygon = polygonFromGeometry(geometry);
            if (polygon.size() < 3) {
                mergeTunnelReport(reportSink, node.getId(), 0, 0, false, 0);
                continue;
            }

            int blockedAir = 0;
            int protectedConflicts = 0;
            boolean connectivityApplied = false;
            int disconnectedComponents = 0;

            Integer clearHeight = TunnelJunctionSeedResolver.resolveTunnelClearHeight(approaches);
            RoadJunctionGenerator.JunctionBlocks authored = junctions.get(node.getId());
            List<RoadEdge> connected = connectedEdges(network, node);
            int junctionY = elevations.getOrDefault(node.getId(), approaches.getFirst().roadY());
            JunctionWorldFootprint footprint = resolveConnectivityFootprint(
                authored, geometry, coordinateTransformer);
            RoadTerrainClearanceUtils.BlockColumnResolver columns = columnResolver(coordinateTransformer);
            JunctionElevationField elevationField = resolveConnectivityElevationField(
                authored,
                footprint,
                geometry,
                edgeResults,
                connected,
                network,
                columns,
                junctionY);
            ToIntFunction<Vec2d> roadYAt = elevationField::surfaceY;
            int resolvedClear = clearHeight != null ? Math.max(1, clearHeight) : 0;

            JunctionConstructionContext authoredContext =
                authored != null ? authored.constructionContext() : null;
            for (List<TunnelJunctionSeedResolver.TunnelApproach> layerApproaches
                    : groupApproachesBySpatialBand(approaches)) {
                Collection<RoadGenerationResult> approachResults =
                    approachEdgeResults(layerApproaches, edgeResults);
                // 优先复用路口生成时缓存的采样空气，仅刷新壳层后的阻挡衬砌。
                Set<BlockPos> openVolume = resolveConnectivityOpenVolume(
                    authoredContext,
                    layerApproaches,
                    polygon,
                    approachResults,
                    coordinateTransformer,
                    roadYAt);
                // 膨胀可能把空气顶到自然地表或设计洞顶以上；连通清障必须同时夹地表与洞腔顶。
                openVolume = retainBelowDesignEnvelope(
                    openVolume, terrain, coordinateTransformer, roadYAt, resolvedClear);
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

    /**
     * 连通清障体积硬夹：{@code Y < 自然地表}，且在提供路面标高场时 {@code Y < roadY + clearHeight}。
     */
    static Set<BlockPos> retainBelowDesignEnvelope(
            Set<BlockPos> volume,
            TerrainSampler terrain,
            ICoordinateService coordinateTransformer,
            ToIntFunction<Vec2d> roadYAt,
            int clearHeight) {
        if (volume == null || volume.isEmpty()) {
            return volume != null ? volume : Set.of();
        }
        if (terrain == null && (roadYAt == null || clearHeight <= 0)) {
            return volume;
        }
        Set<BlockPos> kept = new LinkedHashSet<>();
        // 同列大量体素共享地表采样；按 ColumnKey 缓存避免重复 sampleSurfaceY。
        Map<ColumnKey, Integer> surfaceYByColumn = new HashMap<>();
        Map<ColumnKey, Boolean> closedCavityByColumn = new HashMap<>();
        for (BlockPos pos : volume) {
            if (pos == null) {
                continue;
            }
            Vec2d plan = TunnelJunctionSeedResolver.planPointForBlock(pos, coordinateTransformer);
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            if (terrain != null) {
                if (!terrain.isChunkLoaded(plan) && !terrain.isChunkLoaded(pos.getX(), pos.getZ())) {
                    continue;
                }
                int surfaceY = surfaceYByColumn.computeIfAbsent(column, key ->
                    terrain.sampleSurfaceY(plan != null ? plan : new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5)));
                if (pos.getY() >= surfaceY) {
                    continue;
                }
            }
            if (roadYAt != null && clearHeight > 0 && plan != null) {
                int roadY = roadYAt.applyAsInt(plan);
                int cavityTop = roadY + clearHeight;
                // 洞腔顶为 roadY+clearHeight；空气格不得触及或超过该标高（衬砌起始）。
                if (pos.getY() >= cavityTop) {
                    continue;
                }
                // 浅覆土／洞口过渡列禁止连通清障掏出未授权地下洞腔（与走廊 Mode 一致）。
                int designedRoofTopY = cavityTop;
                if (terrain != null) {
                    boolean allowsClosed = closedCavityByColumn.computeIfAbsent(column, key ->
                        TunnelColumnConstruction.allowsClosedCavity(terrain, plan, designedRoofTopY));
                    if (!allowsClosed) {
                        continue;
                    }
                }
            }
            kept.add(pos);
        }
        return kept;
    }

    /**
     * 有按边采样空气缓存时：拼合本空间带进口 → 按当前多边形裁剪 → 刷新衬砌。
     * 缓存缺失则回退全量 {@link TunnelJunctionSeedResolver#collectOpenVolume}。
     */
    static Set<BlockPos> resolveConnectivityOpenVolume(
            JunctionConstructionContext authoredContext,
            List<TunnelJunctionSeedResolver.TunnelApproach> layerApproaches,
            List<Vec2d> polygon,
            Collection<RoadGenerationResult> approachResults,
            ICoordinateService coordinateTransformer,
            ToIntFunction<Vec2d> roadYAt) {
        Set<BlockPos> cachedSampled = sampledAirFromContext(authoredContext, layerApproaches);
        if (cachedSampled != null) {
            return TunnelJunctionSeedResolver.assembleOpenVolumeFromSampledAir(
                cachedSampled,
                layerApproaches,
                polygon,
                approachResults,
                coordinateTransformer);
        }
        return TunnelJunctionSeedResolver.collectOpenVolume(
            layerApproaches,
            polygon,
            approachResults,
            coordinateTransformer,
            roadYAt);
    }

    static Set<BlockPos> sampledAirFromContext(
            JunctionConstructionContext authoredContext,
            List<TunnelJunctionSeedResolver.TunnelApproach> layerApproaches) {
        if (authoredContext == null || !authoredContext.hasSampledApproachAirCache()) {
            return null;
        }
        if (layerApproaches == null || layerApproaches.isEmpty()) {
            return null;
        }
        List<String> edgeIds = new ArrayList<>(layerApproaches.size());
        for (TunnelJunctionSeedResolver.TunnelApproach approach : layerApproaches) {
            if (approach == null || approach.edge() == null || approach.edge().getId() == null) {
                return null;
            }
            edgeIds.add(approach.edge().getId());
        }
        return authoredContext.sampledApproachAirForEdges(edgeIds);
    }

    private static List<RoadEdge> connectedEdges(RoadNetwork network, RoadNode node) {
        List<RoadEdge> connected = new ArrayList<>();
        if (network == null || node == null) {
            return connected;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                connected.add(edge);
            }
        }
        return connected;
    }

    static List<Vec2d> polygonFromGeometry(JunctionGeometryResult geometry) {
        if (geometry == null) {
            return List.of();
        }
        if (geometry.fillPolygon().size() >= 3) {
            return geometry.fillPolygon();
        }
        return geometry.sharpPolygon();
    }

    static JunctionWorldFootprint resolveConnectivityFootprint(
            RoadJunctionGenerator.JunctionBlocks junctionBlocks,
            JunctionGeometryResult geometry,
            ICoordinateService coordinateTransformer) {
        if (junctionBlocks != null && junctionBlocks.constructionContext() != null) {
            JunctionWorldFootprint authored = junctionBlocks.constructionContext().footprint();
            if (authored != null && !authored.isEmpty()) {
                return authored;
            }
        }
        return JunctionWorldFootprint.from(geometry, coordinateTransformer);
    }

    /**
     * 优先复用路口生成阶段权威高程场；缺失时才重建 fillet loft + elevation field。
     */
    static JunctionElevationField resolveConnectivityElevationField(
            RoadJunctionGenerator.JunctionBlocks junctionBlocks,
            JunctionWorldFootprint footprint,
            JunctionGeometryResult geometry,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> connected,
            RoadNetwork network,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        if (junctionBlocks != null) {
            JunctionElevationField authored = junctionBlocks.elevationField();
            if (authored != null
                    && authored.targetYByColumn() != null
                    && !authored.targetYByColumn().isEmpty()) {
                return authored;
            }
        }
        JunctionFilletElevationLoft filletLoft = JunctionFilletElevationLoft.build(
            geometry != null ? geometry.fillets() : List.of(),
            edgeResults,
            connected,
            network,
            columns,
            junctionY);
        return JunctionElevationField.build(footprint, filletLoft, columns, junctionY);
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver columnResolver(
            ICoordinateService transformer) {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                if (planPoint == null) {
                    return 0;
                }
                return RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer).getX();
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                if (planPoint == null) {
                    return 0;
                }
                return RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer).getZ();
            }
        };
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
     * <p>
     * 窗口需覆盖洞门过渡带与短段 ROAD→TUNNEL 衔接；过窄会漏检隧道路口，广场按露天打穿覆土。
     * 仍禁止整边远处隧道毒化开敞平交（见 {@code OpenJunctionNotPoisonedByDistantTunnelTest}）。
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
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            List.of(edge),
            e -> RoadModelUtils.getEffectiveWidth(network, e, config) / 2.0,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS));
        // 窗口 = 路口半径 / 洞门过渡带 / 首段长：覆盖紧邻洞门，但不把远处洞身算进开敞平交。
        double window = Math.max(
            junctionRadius,
            Math.max(
                RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS * scale,
                approachSegments.getFirst().distance));

        boolean fromStart = edge.getStartNodeId().equals(node.getId());
        double accumulated = 0.0;
        if (result.constructionTypes == null || result.constructionTypes.isEmpty()) {
            return false;
        }
        for (int i = 0; i < approachSegments.size(); i++) {
            if (accumulated > window + EPSILON) {
                break;
            }
            int typeIndex = fromStart ? i : (centerline.size() - 2 - i);
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(
                result.constructionTypes, typeIndex);
            if (type != null && type.isTunnelFamily()) {
                return true;
            }
            accumulated += Math.max(0.0, approachSegments.get(i).distance);
        }
        return false;
    }

    /**
     * 路口进口是否应按隧道处理：几何段窗口 {@link #tunnelAtNode}，或施工类型数组从节点端
     * 向外的有限站距窗口（几何段过粗时 ROAD→TUNNEL 短 stub 仍能识别）。
     * <p>
     * {@link TunnelJunctionSeedResolver#collectTunnelApproaches} 与 fail-closed 证据判定必须共用此口径，
     * 否则会出现「有近节点隧道证据但 approach 为空」误拦建造。
     */
    static boolean hasTunnelApproachEvidenceAtNode(
            RoadNetwork network,
            RoadNode node,
            RoadEdge edge,
            RoadGenerationResult result,
            RoadSystemConfig config,
            List<Vec2d> centerline,
            List<PathSegment> approachSegments,
            double unitsPerBlock) {
        if (tunnelAtNode(network, node, edge, result, config, centerline, approachSegments, unitsPerBlock)) {
            return true;
        }
        return JunctionConstructionContext.hasTunnelFamilyWithinEntryTypeWindow(
            edge, node != null ? node.getId() : null, result, config, node);
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
                    result.recordExpectedTunnelCavity(pos);
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
            carrier.tunnelBlocks.add(pos);
            carrier.recordExpectedTunnelCavity(pos);
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
        result.recordExpectedTunnelCavity(pos);
        return true;
    }

    static boolean hasProtectedPlacement(Collection<RoadGenerationResult> edgeResults, BlockPos pos) {
        if (edgeResults == null) {
            return false;
        }
        if (isExpectedJunctionShellLining(edgeResults, pos)) {
            return true;
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

    /**
     * {@link TunnelJunctionShellPass} 写入的合法 WALL/ROOF 衬砌：连通清障不得删除。
     */
    static boolean isExpectedJunctionShellLining(
            Collection<RoadGenerationResult> edgeResults,
            BlockPos pos) {
        if (edgeResults == null || pos == null) {
            return false;
        }
        for (RoadGenerationResult result : edgeResults) {
            if (result != null
                    && result.expectedTunnelLining != null
                    && result.expectedTunnelLining.contains(pos)) {
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
        // 圆角壳最终角色为 WALL/ROOF 的衬砌不是「阻挡连通的旧墙」
        if (result != null
                && result.expectedTunnelLining != null
                && result.expectedTunnelLining.contains(pos)) {
            return false;
        }
        RoadSolidLayer layer = null;
        if (result != null) {
            layer = result.placementLayers.get(pos);
        }
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
        Integer priority = null;
        if (result != null) {
            priority = result.placementMaterialPriorities.get(pos);
        }
        if (priority != null && priority >= RoadSolidMaterialPriority.DECORATIVE) {
            return false;
        }
        if (layer == RoadSolidLayer.TUNNEL) {
            return true;
        }
        return Objects.requireNonNull(result).tunnelBlocks.contains(pos);
    }
}
