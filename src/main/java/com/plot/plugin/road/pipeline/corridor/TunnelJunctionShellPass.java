package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * 隧道路口圆角联合洞腔 / 边界侧墙 / 顶衬施工。
 * <p>
 * 仅对「逐列覆土充足」的封闭隧道列施工；浅覆土/露天街角不开挖、不封顶。
 * 最终体素归属由 {@link VoxelRole} 掩膜决定：落在 {@link VoxelRole#CAVITY_AIR} 的旧衬砌一律清除，
 * 仅 {@link VoxelRole#WALL_LINING}/{@link VoxelRole#ROOF_LINING} 可保留或写入衬砌。
 */
public final class TunnelJunctionShellPass {
    private static final double EPSILON = 1e-9;
    private static final String FALLBACK_LINING = "minecraft:stone_bricks";
    private static final String GRAVITY_RESTORE = "minecraft:stone";
    /** 洞顶以上至少还需多少覆土才视为封闭隧道列（含衬砌厚度外再留裕量）。 */
    private static final int MIN_EXTRA_COVER_ABOVE_ROOF = 1;

    /** 设计洞腔最终体素角色。 */
    public enum VoxelRole {
        CAVITY_AIR,
        WALL_LINING,
        ROOF_LINING
    }

    @FunctionalInterface
    public interface MaterialResolver {
        String resolve(String materialId);
    }

    private TunnelJunctionShellPass() {
    }

    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            TerrainSampler terrain,
            MaterialResolver materials,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        apply(
            network, edgeResults, nodeElevations, config, coordinateTransformer,
            unitsPerBlockEstimator, terrain, materials, previousBlockResolver, Map.of());
    }

    /**
     * @param junctionResults 已生成的路口结果；非空时优先复用其 {@link JunctionElevationField}，
     *                        使顶衬与圆角铺装共用同一路面基准，不再本地改写标高。
     */
    public static void apply(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            BiFunction<List<Vec2d>, List<PathSegment>, Double> unitsPerBlockEstimator,
            TerrainSampler terrain,
            MaterialResolver materials,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults) {
        apply(network, edgeResults, nodeElevations, config, coordinateTransformer,
            unitsPerBlockEstimator, terrain, materials, previousBlockResolver, junctionResults, null);
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
            TerrainSampler terrain,
            MaterialResolver materials,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults,
            Set<String> onlyNodeIds) {
        if (network == null || edgeResults == null || edgeResults.isEmpty()
                || config == null || terrain == null) {
            return;
        }
        Map<String, Integer> elevations = nodeElevations != null ? nodeElevations : Map.of();
        Map<String, RoadJunctionGenerator.JunctionBlocks> junctions =
            junctionResults != null ? junctionResults : Map.of();
        MaterialResolver resolvedMaterials = materials != null
            ? materials
            : material -> material != null && !material.isBlank() ? material : FALLBACK_LINING;
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(config);

        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 3) {
                continue;
            }
            if (onlyNodeIds != null && !onlyNodeIds.contains(node.getId())) {
                continue;
            }
            if (node.isGradeSeparated() || node.isStackGradeSeparated()) {
                continue;
            }
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches =
                TunnelJunctionSeedResolver.collectTunnelApproaches(
                    network,
                    node,
                    edgeResults,
                    elevations,
                    config,
                    (path, segments) -> unitsPerBlockEstimator != null
                        ? unitsPerBlockEstimator.apply(path, segments)
                        : 1.0);
            if (approaches.isEmpty()) {
                continue;
            }
            double scale = dominantScale(approaches);
            JunctionGeometryResult geometry = resolveJunctionGeometry(network, node, config, scale);
            if (geometry == null || !geometry.hasFillPolygon()) {
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks authoredJunction = junctions.get(node.getId());
            JunctionWorldFootprint footprint = resolveShellFootprint(
                authoredJunction, geometry, coordinateTransformer);
            if (footprint.isEmpty()) {
                continue;
            }
            TunnelJunctionSeedResolver.TunnelApproach primary = approaches.getFirst();
            ResolvedTunnelStyle style = primary.tunnelStyle();
            if (style == null) {
                continue;
            }
            String liningId = resolvedMaterials.resolve(style.liningMaterial());
            if (liningId == null || liningId.isBlank()) {
                liningId = FALLBACK_LINING;
            }
            int junctionY = elevations.getOrDefault(node.getId(), primary.roadY());
            // 与广场清障同源：更严净空（min）+ 更厚衬砌（max），禁止只取第一条进口。
            Integer resolvedClear = TunnelJunctionSeedResolver.resolveTunnelClearHeight(approaches);
            int clearHeight = resolvedClear != null ? Math.max(1, resolvedClear) : Math.max(1, style.clearHeight());
            int sideClearance = Math.max(0, style.sideClearance());
            for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
                if (approach != null && approach.tunnelStyle() != null) {
                    sideClearance = Math.max(sideClearance, Math.max(0, approach.tunnelStyle().sideClearance()));
                }
            }
            int liningThickness = Math.max(
                1, TunnelJunctionSeedResolver.resolveTunnelLiningThickness(approaches));
            Collection<RoadGenerationResult> carriers = approachCarriers(approaches, edgeResults);
            if (carriers.isEmpty()) {
                continue;
            }

            // 倒圆区顶衬必须跟下方路面 loft 同趋势，禁止整广场用单一 roadY 压成平顶。
            List<RoadEdge> connected = connectedEdges(network, node);
            RoadTerrainClearanceUtils.BlockColumnResolver columns =
                columnResolver(coordinateTransformer);
            var centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
            // 优先复用路口铺装阶段已确定的权威高程场；有权威场时跳过 fillet loft 重建。
            JunctionElevationField elevationField = resolveShellElevationField(
                authoredJunction,
                footprint,
                geometry,
                edgeResults,
                connected,
                network,
                columns,
                junctionY);
            ToIntFunction<ColumnKey> roadYAt = elevationField::surfaceY;

            // 候选洞腔列：铺装责任区 + 侧向净距；不再用 clearance 外扩格做开挖底盘。
            Set<ColumnKey> candidateColumns = expandPaveBySideClearance(footprint.paveColumns(), sideClearance);
            Map<ColumnKey, Integer> roadYByColumn = new HashMap<>();
            for (ColumnKey column : candidateColumns) {
                roadYByColumn.put(column, roadYAt.applyAsInt(column));
            }

            Set<ColumnKey> finalCavityColumns;
            JunctionConstructionContext authoredContext =
                authoredJunction != null ? authoredJunction.constructionContext() : null;
            if (authoredContext != null && authoredContext.hasShellCavityCache()) {
                // 复用路口生成阶段已算好的 enclosed / approachCavity / tunnelFamily，
                // 跳过壳层再跑一遍 collectOpenVolume + 逐列封闭判定。
                finalCavityColumns = resolveFinalCavityFromCache(
                    candidateColumns,
                    authoredContext.enclosedColumns(),
                    authoredContext.approachCavityColumns(),
                    authoredContext.tunnelFamilyColumns());
            } else {
                Set<ColumnKey> enclosedColumns = new LinkedHashSet<>();
                for (ColumnKey column : candidateColumns) {
                    Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                    int roadY = roadYByColumn.getOrDefault(column, roadYAt.applyAsInt(column));
                    // 开敞进口列与道路一致：不封顶。隧道进口列才按覆土封闭。
                    if (!TunnelJunctionSeedResolver.isTunnelFamilyAtPlan(
                            plan, connected, edgeResults, centerlines)) {
                        continue;
                    }
                    if (columnHasEnclosedCover(
                            terrain, plan, column, roadY, clearHeight, liningThickness, tunnelThreshold)) {
                        enclosedColumns.add(column);
                    }
                }
                // 同层：进口隧道洞腔 ∪ 圆角封闭列 → 最终洞腔；墙只出在并集外缘，避免连接处残留分隔柱。
                // 进口 open-volume 扫掠到的封闭列一律并入（不要求最近中心线为隧道族），
                // 避免圆角更靠近开敞进口时漏保护而被广场露天挖穿。
                List<Vec2d> polygon = geometry.fillPolygon().size() >= 3
                    ? geometry.fillPolygon()
                    : geometry.sharpPolygon();
                Set<ColumnKey> approachCavityColumns = cavityColumnsFromOpenVolume(
                    TunnelJunctionSeedResolver.collectOpenVolume(
                        approaches,
                        polygon,
                        carriers,
                        coordinateTransformer,
                        plan -> {
                            ColumnKey key = new ColumnKey(
                                columns.worldX(plan), columns.worldZ(plan));
                            return roadYAt.applyAsInt(key);
                        }));
                finalCavityColumns = new LinkedHashSet<>(enclosedColumns);
                for (ColumnKey column : approachCavityColumns) {
                    if (column == null || !candidateColumns.contains(column)) {
                        continue;
                    }
                    // 进口洞腔扫掠可能覆盖浅覆土/洞口列；只有仍封闭覆土的列才并入最终洞腔，
                    // 避免浅覆广场被走廊 open-volume 误开挖或封顶。
                    int roadY = roadYByColumn.computeIfAbsent(column, roadYAt::applyAsInt);
                    Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                    if (!columnHasEnclosedCover(
                            terrain, plan, column, roadY, clearHeight, liningThickness, tunnelThreshold)) {
                        continue;
                    }
                    finalCavityColumns.add(column);
                }
            }
            if (finalCavityColumns.isEmpty()) {
                continue;
            }

            // 墙体 = 最终洞腔外缘（仍需覆土）；禁止写到合法 ROAD 净空列。
            Set<ColumnKey> wallColumns = wallRingAround(finalCavityColumns, liningThickness);
            wallColumns.removeIf(column -> {
                if (hasRoadSurfaceColumn(carriers, column)) {
                    return true;
                }
                Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                int roadY = roadYByColumn.computeIfAbsent(column, roadYAt::applyAsInt);
                return !columnHasEnclosedCover(
                    terrain, plan, column, roadY, clearHeight, liningThickness, tunnelThreshold);
            });

            Map<VoxelRole, Set<BlockPos>> roles = buildRoleMasks(
                finalCavityColumns,
                wallColumns,
                roadYByColumn,
                clearHeight,
                liningThickness,
                terrain,
                footprint,
                coordinateTransformer);

            // Phase A/B — 先写墙/顶，再按掩膜清洞腔（旧衬砌落在 CAVITY_AIR 必清）。
            for (BlockPos pos : roles.get(VoxelRole.WALL_LINING)) {
                Vec2d plan = planPoint(footprint, new ColumnKey(pos.getX(), pos.getZ()), coordinateTransformer);
                if (yAtOrAboveNaturalSurface(terrain, plan, pos.getY())) {
                    continue;
                }
                if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                    continue;
                }
                writeLining(carriers, pos, liningId, previousBlockResolver);
            }
            for (BlockPos pos : roles.get(VoxelRole.ROOF_LINING)) {
                Vec2d plan = planPoint(footprint, new ColumnKey(pos.getX(), pos.getZ()), coordinateTransformer);
                if (yAtOrAboveNaturalSurface(terrain, plan, pos.getY())) {
                    continue;
                }
                if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                    continue;
                }
                writeLining(carriers, pos, liningId, previousBlockResolver);
            }
            for (ColumnKey column : finalCavityColumns) {
                if (wallColumns.contains(column)) {
                    continue;
                }
                Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                int surfaceY = terrain.sampleSurfaceY(plan);
                int roadY = roadYByColumn.getOrDefault(column, junctionY);
                int roofTop = roadY + clearHeight + liningThickness - 1;
                stabilizeBelowSurface(
                    carriers, column, roofTop, surfaceY, previousBlockResolver, terrain);
            }

            Set<BlockPos> keepLining = new LinkedHashSet<>();
            keepLining.addAll(roles.get(VoxelRole.WALL_LINING));
            keepLining.addAll(roles.get(VoxelRole.ROOF_LINING));
            for (BlockPos pos : roles.get(VoxelRole.CAVITY_AIR)) {
                Vec2d plan = planPoint(footprint, new ColumnKey(pos.getX(), pos.getZ()), coordinateTransformer);
                if (yAtOrAboveNaturalSurface(terrain, plan, pos.getY())) {
                    continue;
                }
                if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                    continue;
                }
                if (keepLining.contains(pos)) {
                    continue;
                }
                writeAir(carriers, pos, previousBlockResolver);
            }
        }
    }

    /**
     * 与 {@link RoadJunctionGenerator} / 走廊裁剪同一套硬质半宽 × 画布尺度 + 倒圆半径。
     */
    public static JunctionGeometryResult resolveJunctionGeometry(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            double canvasScale) {
        if (network == null || node == null || config == null) {
            return JunctionGeometryResult.empty();
        }
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                connected.add(edge);
            }
        }
        if (connected.isEmpty()) {
            return JunctionGeometryResult.empty();
        }
        double scale = canvasScale > EPSILON ? canvasScale : 1.0;
        ToDoubleFunction<RoadEdge> halfWidthCanvas = edge -> RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()) * scale;
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            halfWidthCanvas,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS) * scale);
        double cornerBlocks = RoadJunctionGenerator.resolveCornerRadius(node, network, config);
        return RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            connected,
            halfWidthCanvas,
            junctionRadius,
            Math.max(0.0, cornerBlocks) * scale,
            RoadJunctionCenterlineResolver.forNetwork(network),
            network,
            config,
            scale);
    }

    /**
     * 封闭隧道列：相对路面覆土达到隧道门槛，且自然地表盖过设计洞顶 + 衬砌 + 裕量。
     * 浅覆土/洞口列返回 false，不得开挖或封顶。
     */
    public static boolean columnHasEnclosedCover(
            TerrainSampler terrain,
            Vec2d plan,
            ColumnKey column,
            int roadY,
            int clearHeight,
            int liningThickness,
            int tunnelThreshold) {
        if (terrain == null || column == null) {
            return false;
        }
        if (plan == null) {
            plan = new Vec2d(column.worldX(), column.worldZ());
        }
        if (!terrain.isChunkLoaded(plan) && !terrain.isChunkLoaded(column.worldX(), column.worldZ())) {
            return false;
        }
        int roofY = roadY + Math.max(1, clearHeight);
        int lining = Math.max(1, liningThickness);
        int surfaceY = terrain.sampleSurfaceY(plan);
        int coverAboveRoad = surfaceY - roadY;
        int minCoverAboveRoad = Math.max(
            tunnelThreshold,
            clearHeight + lining + MIN_EXTRA_COVER_ABOVE_ROOF);
        if (coverAboveRoad < minCoverAboveRoad) {
            return false;
        }
        return surfaceY >= roofY + lining + MIN_EXTRA_COVER_ABOVE_ROOF - 1;
    }

    private static Map<VoxelRole, Set<BlockPos>> buildRoleMasks(
            Set<ColumnKey> enclosedColumns,
            Set<ColumnKey> wallColumns,
            Map<ColumnKey, Integer> roadYByColumn,
            int clearHeight,
            int liningThickness,
            TerrainSampler terrain,
            JunctionWorldFootprint footprint,
            ICoordinateService transformer) {
        Map<VoxelRole, Set<BlockPos>> roles = new EnumMap<>(VoxelRole.class);
        roles.put(VoxelRole.CAVITY_AIR, new LinkedHashSet<>());
        roles.put(VoxelRole.WALL_LINING, new LinkedHashSet<>());
        roles.put(VoxelRole.ROOF_LINING, new LinkedHashSet<>());
        int clear = Math.max(1, clearHeight);
        int lining = Math.max(1, liningThickness);

        for (ColumnKey column : wallColumns) {
            Vec2d plan = planPoint(footprint, column, transformer);
            int roadY = roadYByColumn.getOrDefault(column, 0);
            int roofY = roadY + clear;
            int surfaceY = terrain != null ? terrain.sampleSurfaceY(plan) : Integer.MAX_VALUE;
            for (int y = roadY; y <= roofY + lining - 1; y++) {
                if (y >= surfaceY) {
                    continue;
                }
                roles.get(VoxelRole.WALL_LINING).add(
                    new BlockPos(column.worldX(), y, column.worldZ()));
            }
        }
        for (ColumnKey column : enclosedColumns) {
            if (wallColumns.contains(column)) {
                continue;
            }
            Vec2d plan = planPoint(footprint, column, transformer);
            int roadY = roadYByColumn.getOrDefault(column, 0);
            int roofY = roadY + clear;
            int surfaceY = terrain != null ? terrain.sampleSurfaceY(plan) : Integer.MAX_VALUE;
            int cavityTop = Math.min(roofY - 1, surfaceY - 1);
            for (int y = roadY + 1; y <= cavityTop; y++) {
                roles.get(VoxelRole.CAVITY_AIR).add(
                    new BlockPos(column.worldX(), y, column.worldZ()));
            }
            for (int y = roofY; y < roofY + lining; y++) {
                if (y >= surfaceY) {
                    continue;
                }
                roles.get(VoxelRole.ROOF_LINING).add(
                    new BlockPos(column.worldX(), y, column.worldZ()));
            }
        }
        return roles;
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

    /** 铺装足迹按隧道侧向净距外扩；供壳层与 {@link JunctionConstructionContext} 共用。 */
    public static Set<ColumnKey> expandPaveBySideClearancePublic(
            Set<ColumnKey> pave, int sideClearance) {
        return expandPaveBySideClearance(pave, sideClearance);
    }

    private static Set<ColumnKey> expandPaveBySideClearance(Set<ColumnKey> pave, int sideClearance) {
        if (pave == null || pave.isEmpty()) {
            return Set.of();
        }
        if (sideClearance <= 0) {
            return new LinkedHashSet<>(pave);
        }
        Set<ColumnKey> expanded = new LinkedHashSet<>(pave);
        for (ColumnKey key : pave) {
            for (int dx = -sideClearance; dx <= sideClearance; dx++) {
                for (int dz = -sideClearance; dz <= sideClearance; dz++) {
                    expanded.add(new ColumnKey(key.worldX() + dx, key.worldZ() + dz));
                }
            }
        }
        return expanded;
    }

    /**
     * 由路口生成缓存拼壳层最终洞腔：
     * {@code expand(pave)×enclosed×(tunnelFamily∨approachCavity)}。
     * 不得直接用 {@link JunctionConstructionContext#protectedCavityColumns()}。
     */
    static Set<ColumnKey> resolveFinalCavityFromCache(
            Set<ColumnKey> candidateColumns,
            Set<ColumnKey> enclosedColumns,
            Set<ColumnKey> approachCavityColumns,
            Set<ColumnKey> tunnelFamilyColumns) {
        Set<ColumnKey> finalCavity = new LinkedHashSet<>();
        if (candidateColumns == null || candidateColumns.isEmpty()) {
            return finalCavity;
        }
        Set<ColumnKey> enclosed = enclosedColumns != null ? enclosedColumns : Set.of();
        Set<ColumnKey> approach = approachCavityColumns != null ? approachCavityColumns : Set.of();
        Set<ColumnKey> family = tunnelFamilyColumns != null ? tunnelFamilyColumns : Set.of();
        for (ColumnKey column : candidateColumns) {
            if (column == null || !enclosed.contains(column)) {
                continue;
            }
            if (family.contains(column) || approach.contains(column)) {
                finalCavity.add(column);
            }
        }
        return finalCavity;
    }

    static JunctionWorldFootprint resolveShellFootprint(
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

    static JunctionElevationField resolveShellElevationField(
            RoadJunctionGenerator.JunctionBlocks junctionBlocks,
            JunctionWorldFootprint footprint,
            JunctionFilletElevationLoft filletLoft,
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
        return JunctionElevationField.build(footprint, filletLoft, columns, junctionY);
    }

    /**
     * 有权威高程场时直接复用，避免壳层再构建 fillet loft；否则现场 build。
     */
    static JunctionElevationField resolveShellElevationField(
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

    static Set<ColumnKey> cavityColumnsFromOpenVolume(Set<BlockPos> openVolume) {
        Set<ColumnKey> columns = new LinkedHashSet<>();
        if (openVolume == null) {
            return columns;
        }
        for (BlockPos pos : openVolume) {
            if (pos != null) {
                columns.add(new ColumnKey(pos.getX(), pos.getZ()));
            }
        }
        return columns;
    }

    static boolean hasRoadSurfaceColumn(
            Collection<RoadGenerationResult> carriers, ColumnKey column) {
        if (carriers == null || column == null) {
            return false;
        }
        for (RoadGenerationResult result : carriers) {
            if (result == null || result.placementLayers == null) {
                continue;
            }
            for (Map.Entry<BlockPos, RoadSolidLayer> entry : result.placementLayers.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos == null || entry.getValue() != RoadSolidLayer.ROAD) {
                    continue;
                }
                if (pos.getX() == column.worldX() && pos.getZ() == column.worldZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 洞腔外侧一圈墙体列：边界列的 4-邻域中不属于洞腔的格子，厚度随 liningThickness。
     */
    static Set<ColumnKey> wallRingAround(Set<ColumnKey> enclosed, int liningThickness) {
        Set<ColumnKey> walls = new LinkedHashSet<>();
        int ring = Math.max(1, liningThickness);
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey key : enclosed) {
            boolean border = false;
            for (int[] dir : dirs) {
                if (!enclosed.contains(new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]))) {
                    border = true;
                    break;
                }
            }
            if (!border) {
                continue;
            }
            for (int[] dir : dirs) {
                for (int t = 1; t <= ring; t++) {
                    ColumnKey outside = new ColumnKey(
                        key.worldX() + dir[0] * t, key.worldZ() + dir[1] * t);
                    if (!enclosed.contains(outside)) {
                        walls.add(outside);
                    }
                }
            }
        }
        return walls;
    }

    private static double dominantScale(List<TunnelJunctionSeedResolver.TunnelApproach> approaches) {
        double scale = 1.0;
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            if (approach != null && approach.unitsPerBlock() > EPSILON) {
                scale = Math.max(scale, approach.unitsPerBlock());
            }
        }
        return scale;
    }

    private static Collection<RoadGenerationResult> approachCarriers(
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches,
            Map<String, RoadGenerationResult> edgeResults) {
        Set<RoadGenerationResult> carriers = new LinkedHashSet<>();
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            RoadGenerationResult result = edgeResults.get(approach.edge().getId());
            if (result != null) {
                carriers.add(result);
            }
        }
        return carriers;
    }

    private static Vec2d planPoint(
            JunctionWorldFootprint footprint,
            ColumnKey column,
            ICoordinateService transformer) {
        if (column == null) {
            return null;
        }
        if (footprint != null) {
            Vec2d plan = footprint.planPoint(column);
            if (plan != null) {
                return plan;
            }
        }
        WorldProjectionSnapshot snapshot = transformer != null ? transformer.captureProjection() : null;
        if (snapshot != null && snapshot.isValid()) {
            try {
                return snapshot.toCanvas(new Vec2d(column.worldX(), column.worldZ()));
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return new Vec2d(column.worldX(), column.worldZ());
    }

    private static boolean yAtOrAboveNaturalSurface(TerrainSampler terrain, Vec2d plan, int y) {
        if (terrain == null || plan == null) {
            return true;
        }
        return y >= terrain.sampleSurfaceY(plan);
    }

    /**
     * 仅在自然地表以下稳定重力块；绝不改写地表及其上方。
     */
    private static void stabilizeBelowSurface(
            Collection<RoadGenerationResult> carriers,
            ColumnKey column,
            int roofTop,
            int surfaceY,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver,
            TerrainSampler terrain) {
        if (terrain == null || carriers == null || carriers.isEmpty()) {
            return;
        }
        int maxY = Math.min(roofTop + 8, surfaceY - 1);
        for (int y = roofTop + 1; y <= maxY; y++) {
            BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
            if (!terrain.isGravityAffectedBlock(column.worldX(), y, column.worldZ())) {
                if (terrain.isSolidBlock(column.worldX(), y, column.worldZ())) {
                    break;
                }
                continue;
            }
            if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                continue;
            }
            writeLining(carriers, pos, GRAVITY_RESTORE, previousBlockResolver);
        }
    }

    private static void writeLining(
            Collection<RoadGenerationResult> carriers,
            BlockPos pos,
            String liningId,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        RoadGenerationResult carrier = carriers.iterator().next();
        String previous = TunnelJunctionConnectivityPass.resolvePreviousBlockId(
            pos, carrier.placementRecords.get(pos), carriers, previousBlockResolver);
        carrier.recordPlacementOverride(pos, previous, liningId);
        carrier.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        carrier.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
        if (!carrier.tunnelBlocks.contains(pos)) {
            carrier.tunnelBlocks.add(pos);
        }
        // 写入 VoxelRole 合法衬砌期望，供 ConnectivityPass 保护，禁止后续连通清障删掉新顶衬/侧墙。
        for (RoadGenerationResult peer : carriers) {
            if (peer != null) {
                peer.recordExpectedTunnelLining(pos);
            }
        }
    }

    private static void writeAir(
            Collection<RoadGenerationResult> carriers,
            BlockPos pos,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        // 每个进口边载体都清：旧横断面衬砌可能分散写在多条边上。
        for (RoadGenerationResult carrier : carriers) {
            if (carrier == null) {
                continue;
            }
            String previous = TunnelJunctionConnectivityPass.resolvePreviousBlockId(
                pos, carrier.placementRecords.get(pos), carriers, previousBlockResolver);
            carrier.recordPlacementOverride(pos, previous, "minecraft:air");
            carrier.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
            carrier.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
            if (!carrier.tunnelBlocks.contains(pos)) {
                carrier.tunnelBlocks.add(pos);
            }
            carrier.recordExpectedTunnelCavity(pos);
        }
    }
}
