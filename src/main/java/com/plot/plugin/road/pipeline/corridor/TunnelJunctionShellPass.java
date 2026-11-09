package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.crosssection.TunnelStructureGenerator;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

/**
 * 隧道路口圆角联合洞腔 / 弧形侧墙 / 顶衬施工。
 * <p>
 * {@link TunnelJunctionConnectivityPass} 只清阻挡连通的衬砌；本 Pass 在连通前按路口圆角外缘
 * 重建侧墙与洞顶，并仅在设计洞腔内写空气，避免露天街角强行盖顶。
 */
public final class TunnelJunctionShellPass {
    private static final double EPSILON = 1e-9;
    private static final String FALLBACK_LINING = "minecraft:stone_bricks";
    private static final String GRAVITY_RESTORE = "minecraft:stone";

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
        if (network == null || edgeResults == null || edgeResults.isEmpty()
                || config == null || terrain == null) {
            return;
        }
        Map<String, Integer> elevations = nodeElevations != null ? nodeElevations : Map.of();
        MaterialResolver resolvedMaterials = materials != null
            ? materials
            : material -> material != null && !material.isBlank() ? material : FALLBACK_LINING;

        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 3) {
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
            JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, coordinateTransformer);
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
            int roadY = primary.roadY();
            int clearHeight = Math.max(1, style.clearHeight());
            int sideClearance = Math.max(0, style.sideClearance());
            int liningThickness = Math.max(1, style.liningThickness());
            int roofY = roadY + clearHeight;
            Collection<RoadGenerationResult> carriers = approachCarriers(approaches, edgeResults);
            if (carriers.isEmpty()) {
                continue;
            }

            Set<ColumnKey> cavityColumns = expandCavityColumns(footprint, sideClearance);
            Set<ColumnKey> wallColumns = new LinkedHashSet<>();
            if (!geometry.fillets().isEmpty()) {
                wallColumns.addAll(collectFilletWallColumns(
                    geometry.fillets(), sideClearance, liningThickness, scale, coordinateTransformer));
            } else {
                wallColumns.addAll(perimeterColumns(cavityColumns));
            }

            // Phase A/B — 侧墙与顶衬先于洞腔开挖，避免重力块坠入。
            for (ColumnKey column : wallColumns) {
                Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                placeWallColumn(
                    carriers, column, plan, roadY, roofY, liningThickness, liningId,
                    terrain, previousBlockResolver);
            }
            for (ColumnKey column : cavityColumns) {
                if (wallColumns.contains(column)) {
                    continue;
                }
                Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                placeRoofIfCovered(
                    carriers, column, plan, roofY, liningThickness, liningId,
                    terrain, previousBlockResolver);
            }

            // Phase C — 仅清设计洞腔内部空气，不动合法侧墙。
            for (ColumnKey column : cavityColumns) {
                if (wallColumns.contains(column)) {
                    continue;
                }
                Vec2d plan = planPoint(footprint, column, coordinateTransformer);
                clearCavityColumn(
                    carriers, column, plan, roadY, roofY, terrain, previousBlockResolver);
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

    public static List<Vec2d> resolveJunctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            double canvasScale) {
        JunctionGeometryResult geometry = resolveJunctionGeometry(network, node, config, canvasScale);
        if (geometry.fillPolygon().size() >= 3) {
            return geometry.fillPolygon();
        }
        return geometry.sharpPolygon();
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

    private static Set<ColumnKey> expandCavityColumns(JunctionWorldFootprint footprint, int sideClearance) {
        Set<ColumnKey> base = new LinkedHashSet<>(footprint.clearance());
        base.addAll(footprint.paveColumns());
        if (sideClearance <= 0) {
            return base;
        }
        Set<ColumnKey> expanded = new LinkedHashSet<>(base);
        for (ColumnKey key : base) {
            for (int dx = -sideClearance; dx <= sideClearance; dx++) {
                for (int dz = -sideClearance; dz <= sideClearance; dz++) {
                    expanded.add(new ColumnKey(key.worldX() + dx, key.worldZ() + dz));
                }
            }
        }
        return expanded;
    }

    private static Set<ColumnKey> collectFilletWallColumns(
            List<JunctionGeometryResult.FilletCorner> fillets,
            int sideClearance,
            int liningThickness,
            double scale,
            ICoordinateService transformer) {
        Set<ColumnKey> walls = new LinkedHashSet<>();
        double outward = Math.max(0.5, sideClearance + liningThickness * 0.5) * (scale > EPSILON ? scale : 1.0);
        for (JunctionGeometryResult.FilletCorner fillet : fillets) {
            if (fillet == null || fillet.center() == null || fillet.radius() <= EPSILON) {
                continue;
            }
            double sweep = fillet.sweepRadians();
            if (!Double.isFinite(sweep) || Math.abs(sweep) < EPSILON) {
                sweep = FilletArcSweep.resolveShort(
                    fillet.center(), fillet.tangentStart(), fillet.tangentEnd());
            }
            if (Math.abs(sweep) < EPSILON) {
                continue;
            }
            double startAngle = Math.atan2(
                fillet.tangentStart().y - fillet.center().y,
                fillet.tangentStart().x - fillet.center().x);
            int segs = Math.max(8, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 16.0)));
            for (int i = 0; i <= segs; i++) {
                double t = (double) i / segs;
                Vec2d onArc = FilletArcSweep.pointOnArc(
                    fillet.center(), fillet.radius(), startAngle + sweep * t);
                // 圆心在铺装外侧：朝圆心方向为进入山体/楔形的外法向
                Vec2d towardRock = fillet.center().subtract(onArc);
                if (towardRock.lengthSquared() < 1e-12) {
                    continue;
                }
                Vec2d unit = towardRock.normalize();
                for (int layer = 0; layer < Math.max(1, liningThickness); layer++) {
                    Vec2d wallPoint = onArc.add(unit.multiply(outward + layer * (scale > EPSILON ? scale : 1.0)));
                    walls.add(JunctionWorldFootprint.keyOf(wallPoint, transformer));
                }
            }
        }
        return walls;
    }

    private static Set<ColumnKey> perimeterColumns(Set<ColumnKey> columns) {
        Set<ColumnKey> perimeter = new LinkedHashSet<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey key : columns) {
            for (int[] dir : dirs) {
                if (!columns.contains(new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]))) {
                    perimeter.add(key);
                    break;
                }
            }
        }
        return perimeter;
    }

    private static Vec2d planPoint(
            JunctionWorldFootprint footprint,
            ColumnKey column,
            ICoordinateService transformer) {
        Vec2d plan = footprint.planPoint(column);
        if (plan != null) {
            return plan;
        }
        return new Vec2d(column.worldX(), column.worldZ());
    }

    private static void placeWallColumn(
            Collection<RoadGenerationResult> carriers,
            ColumnKey column,
            Vec2d plan,
            int roadY,
            int roofY,
            int liningThickness,
            String liningId,
            TerrainSampler terrain,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        for (int y = roadY; y <= roofY + liningThickness - 1; y++) {
            BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
            if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                continue;
            }
            if (!TunnelStructureGenerator.shouldPlaceLining(
                    terrain, column.worldX(), y, column.worldZ(), plan)) {
                continue;
            }
            writeLining(carriers, pos, liningId, previousBlockResolver);
        }
        stabilizeAbove(carriers, column, plan, roofY + liningThickness - 1, terrain, previousBlockResolver);
    }

    private static void placeRoofIfCovered(
            Collection<RoadGenerationResult> carriers,
            ColumnKey column,
            Vec2d plan,
            int roofY,
            int liningThickness,
            String liningId,
            TerrainSampler terrain,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        boolean anyLining = false;
        for (int y = roofY; y < roofY + liningThickness; y++) {
            BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
            if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                continue;
            }
            if (!TunnelStructureGenerator.shouldPlaceLining(
                    terrain, column.worldX(), y, column.worldZ(), plan)) {
                continue;
            }
            writeLining(carriers, pos, liningId, previousBlockResolver);
            anyLining = true;
        }
        if (anyLining) {
            stabilizeAbove(carriers, column, plan, roofY + liningThickness - 1, terrain, previousBlockResolver);
        }
    }

    private static void clearCavityColumn(
            Collection<RoadGenerationResult> carriers,
            ColumnKey column,
            Vec2d plan,
            int roadY,
            int roofY,
            TerrainSampler terrain,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        // 路面以上、洞顶以下清为空气；路面层留给路口铺装
        for (int y = roadY + 1; y < roofY; y++) {
            BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
            if (TunnelJunctionConnectivityPass.hasProtectedPlacement(carriers, pos)) {
                continue;
            }
            // 已有合法衬砌（本 Pass 刚写的墙/顶）不删
            if (hasStructuralTunnelLining(carriers, pos)) {
                continue;
            }
            writeAir(carriers, pos, previousBlockResolver);
        }
    }

    private static void stabilizeAbove(
            Collection<RoadGenerationResult> carriers,
            ColumnKey column,
            Vec2d plan,
            int roofTop,
            TerrainSampler terrain,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        if (terrain == null) {
            return;
        }
        for (int dy = 1; dy <= 8; dy++) {
            int y = roofTop + dy;
            BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
            if (!terrain.isChunkLoaded(column.worldX(), column.worldZ())) {
                break;
            }
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

    private static boolean hasStructuralTunnelLining(
            Collection<RoadGenerationResult> carriers,
            BlockPos pos) {
        for (RoadGenerationResult result : carriers) {
            BlockRecord record = result.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = result.placementLayers.get(pos);
            Integer priority = result.placementMaterialPriorities.get(pos);
            if (layer == RoadSolidLayer.TUNNEL
                    && priority != null
                    && priority >= RoadSolidMaterialPriority.STRUCTURAL) {
                return true;
            }
        }
        return false;
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
    }

    private static void writeAir(
            Collection<RoadGenerationResult> carriers,
            BlockPos pos,
            TunnelJunctionConnectivityPass.PreviousBlockResolver previousBlockResolver) {
        RoadGenerationResult carrier = carriers.iterator().next();
        String previous = TunnelJunctionConnectivityPass.resolvePreviousBlockId(
            pos, carrier.placementRecords.get(pos), carriers, previousBlockResolver);
        carrier.recordPlacementOverride(pos, previous, "minecraft:air");
        carrier.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        carrier.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.AIR);
        if (!carrier.tunnelBlocks.contains(pos)) {
            carrier.tunnelBlocks.add(pos);
        }
    }
}
