package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.TunnelColumnConstruction;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Stamps full cross-section surface bands and shoulder batters inside junction fill polygons.
 * Shoulder batters route through {@link SlopeProfileSample} → {@link SlopeSurfaceConsolidator}
 * → consolidated column emission (same path as corridor edges).
 */
public final class JunctionCrossSectionFill {
    private JunctionCrossSectionFill() {
    }

    public interface MaterialResolver {
        String resolve(String materialId);

        BlockPos canvasToBlockPos(Vec2d planPoint);
    }

    /**
     * 边坡列清障上限：封闭隧道列返回洞顶 cap；开敞列返回 null（露天到地表）；
     * 立交列可返回上层承重约束。禁止整路口共用一个 maxClearY。
     */
    @FunctionalInterface
    public interface ColumnClearCapResolver {
        Integer resolve(ColumnKey column, Vec2d planPoint, int designSurfaceY);
    }

    /**
     * @return true when a surface layer was stamped at {@code junctionY}
     */
    public static boolean fillCell(
            RoadSolidModel solids,
            Vec2d cellPoint,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId) {
        return fillCell(
            solids, cellPoint, junctionY, nodeId, edges, network, config, materials, columnResolver,
            unitsPerBlock, terrain, tunnelThreshold, bridgeThreshold, fillMaterialId, null);
    }

    public static boolean fillCell(
            RoadSolidModel solids,
            Vec2d cellPoint,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId,
            Integer maxClearY) {
        return fillCell(
            solids, cellPoint, junctionY, nodeId, edges, network, config, materials, columnResolver,
            unitsPerBlock, terrain, tunnelThreshold, bridgeThreshold, fillMaterialId, maxClearY, junctionY);
    }

    /**
     * @param surfaceY 本格路面标高；倒圆补铺可为路缘放样值，核区一般为 {@code junctionY}
     */
    public static boolean fillCell(
            RoadSolidModel solids,
            Vec2d cellPoint,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId,
            Integer maxClearY,
            int surfaceY) {
        return fillCell(solids, cellPoint, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, tunnelThreshold, bridgeThreshold, fillMaterialId,
            maxClearY, surfaceY, RoadJunctionCenterlineResolver.forNetwork(network));
    }

    /** Reuse a generation-local centerline resolver across every plaza voxel. */
    public static boolean fillCell(
            RoadSolidModel solids, Vec2d cellPoint, int junctionY, String nodeId,
            List<RoadEdge> edges, RoadNetwork network, RoadSystemConfig config, MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver, double unitsPerBlock,
            TerrainSampler terrain, int tunnelThreshold, int bridgeThreshold, String fillMaterialId,
            Integer maxClearY, int surfaceY, Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        if (solids == null || cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null) {
            return false;
        }
        // fail-closed：禁止破坏性施工时不得先铺装再只停开挖，避免路面埋进未清天然山体却 stamped=true。
        if (maxClearY != null && maxClearY == JunctionConstructionContext.CLEAR_DENIED) {
            return false;
        }
        int stampY = surfaceY;

        EdgeProjection projection = nearestEdgeProjection(cellPoint, nodeId, edges, centerlineResolver);
        RoadEdge widestEdge = selectWidestEdge(edges, network, config, nodeId);
        ResolvedCrossSection widestSection = widestEdge != null
            ? RoadModelUtils.resolveCrossSectionAtNode(network, widestEdge, nodeId, config)
            : null;
        if (projection == null) {
            stampPlazaRoad(solids, cellPoint, stampY, widestSection, widestEdge, materials);
            gradeColumn(
                solids, cellPoint, stampY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold,
                maxClearY);
            return true;
        }

        ResolvedCrossSection crossSection = resolveApproachCrossSection(
            network, projection, nodeId, config, centerlineResolver, unitsPerBlock);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d leftNormal = approachLeftNormal(projection.edge(), nodeId, centerlineResolver);
        double lateralDistance = cellPoint.subtract(projection.projectedCenter()).dot(leftNormal);
        int lateral = (int) Math.round(lateralDistance / scale);

        Optional<ResolvedCrossSection.SurfaceBandSpec> band = crossSection.surfaceBandAtLateral(lateral);
        if (band.isPresent()) {
            ResolvedCrossSection.SurfaceBandSpec spec = band.get();
            // 行车道：路口统一用最宽道路材质；路肩/人行道仍跟最近进口断面
            if (spec.layer() == RoadSolidLayer.ROAD && widestSection != null) {
                stampPlazaRoad(solids, cellPoint, stampY, widestSection, widestEdge, materials);
            } else {
                stampSurfaceBand(
                    solids, cellPoint, stampY, spec, projection.edge().getId(), materials);
            }
        } else {
            // Polygon cell outside nearest approach band: still pave the junction interior.
            stampPlazaRoad(solids, cellPoint, stampY, widestSection, widestEdge, materials);
        }
        gradeColumn(
            solids, cellPoint, stampY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold,
            maxClearY);
        return true;
    }

    public static RoadSolidLayer resolveSurfaceLayer(
            Vec2d cellPoint, String nodeId, List<RoadEdge> edges, RoadNetwork network,
            RoadSystemConfig config, double unitsPerBlock,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        if (cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null) {
            return RoadSolidLayer.ROAD;
        }
        EdgeProjection projection = nearestEdgeProjection(cellPoint, nodeId, edges, centerlineResolver);
        if (projection == null) {
            return RoadSolidLayer.ROAD;
        }
        ResolvedCrossSection crossSection = resolveApproachCrossSection(
            network, projection, nodeId, config, centerlineResolver, unitsPerBlock);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d leftNormal = approachLeftNormal(projection.edge(), nodeId, centerlineResolver);
        double lateralDistance = cellPoint.subtract(projection.projectedCenter()).dot(leftNormal);
        int lateral = (int) Math.round(lateralDistance / scale);
        Optional<ResolvedCrossSection.SurfaceBandSpec> band = crossSection.surfaceBandAtLateral(lateral);
        if (band.isPresent()) {
            return band.get().layer();
        }
        return RoadSolidLayer.ROAD;
    }

    public static BlockPos surfaceBlockPos(
            Vec2d cellPoint,
            int cellY,
            MaterialResolver materials) {
        if (materials == null) {
            return new BlockPos((int) Math.round(cellPoint.x), cellY, (int) Math.round(cellPoint.y));
        }
        return materials.canvasToBlockPos(cellPoint).withY(cellY);
    }

    /**
     * @param tunnelApproachEdgeIds 隧道进口边：禁止开敞路肩边坡；圆弧边坡仍按逐角覆土判定。
     */
    public static void stampApproachSlopeBatters(
            RoadSolidModel solids,
            Vec2d junctionCenter,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int bridgeThreshold,
            Integer maxClearY,
            List<JunctionGeometryResult.FilletCorner> fillets,
            JunctionElevationField elevationField,
            Set<String> tunnelApproachEdgeIds) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold,
            constantClearCap(maxClearY), fillets,
            elevationField, tunnelApproachEdgeIds, Set.of());
    }

    /**
     * 路口边坡：仅从路缘圆角真实外弧向外生成，不再从交叉点中心打独立路肩射线
     * （中心+半宽会侵入邻路/圆角补铺）。{@code pavedColumns} 内世界列禁止写入边坡。
     * <p>
     * 整路口 {@code maxClearY} 仅作兼容；混合隧道路口请改用
     *  逐列决策。
     */
    public static void stampApproachSlopeBatters(
            RoadSolidModel solids,
            Vec2d junctionCenter,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int bridgeThreshold,
            Integer maxClearY,
            List<JunctionGeometryResult.FilletCorner> fillets,
            JunctionElevationField elevationField,
            Set<String> tunnelApproachEdgeIds,
            Set<ColumnKey> pavedColumns) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold,
            constantClearCap(maxClearY), fillets, elevationField, tunnelApproachEdgeIds, pavedColumns);
    }

    /**
     * @param columnClearCap 逐列清障上限；混合隧道路口必须按列区分封闭/开敞，不得整路口共用洞顶 cap。
     */
    public static void stampApproachSlopeBatters(
            RoadSolidModel solids,
            Vec2d junctionCenter,
            int junctionY,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            TerrainSampler terrain,
            int bridgeThreshold,
            ColumnClearCapResolver columnClearCap,
            List<JunctionGeometryResult.FilletCorner> fillets,
            JunctionElevationField elevationField,
            Set<String> tunnelApproachEdgeIds,
            Set<ColumnKey> pavedColumns) {
        if (solids == null || junctionCenter == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null
                || columnResolver == null) {
            return;
        }
        // junctionCenter / tunnelApproachEdgeIds 保留于签名以兼容调用方；中心路肩已取消。
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int maxHorizontalRun = RoadCorridorWidth.slopeBatterMaxRunBlocks(config);
        List<SlopeProfileSample> samples = new ArrayList<>(collectFilletArcBatterSamples(
            fillets, junctionY, edges, network, config, materials, scale, maxHorizontalRun,
            terrain, columnResolver, 0L, elevationField, tunnelApproachEdgeIds));

        Set<ColumnKey> forbidden = pavedColumns != null ? pavedColumns : Set.of();
        if (!forbidden.isEmpty()) {
            samples.removeIf(sample -> sample != null && forbidden.contains(sample.columnKey()));
        }
        if (samples.isEmpty()) {
            return;
        }
        RoadCorridorPlan slopePlan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(slopePlan, samples, terrain);
        emitConsolidatedSlopeColumns(
            solids, slopePlan, terrain, columnResolver, bridgeThreshold,
            columnClearCap != null ? columnClearCap : constantClearCap(null), forbidden);
    }

    static ColumnClearCapResolver constantClearCap(Integer maxClearY) {
        return (column, planPoint, designSurfaceY) -> maxClearY;
    }

    /**
     * 沿路缘圆角短弧向外打边坡：圆心在草地侧，弧上点外法向指向草地（{@code center - arcPoint}）。
     * 相邻圆弧射线之间用 {@link SlopeBandRasterizer#fillBetweenRays} 补三角空隙。
     * 深覆土（隧道路角）跳过开敞放坡，交给隧道壳。
     */
    private static List<SlopeProfileSample> collectFilletArcBatterSamples(
            List<JunctionGeometryResult.FilletCorner> fillets,
            int junctionY,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            MaterialResolver materials,
            double scale,
            int maxHorizontalRun,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            long rayIdBase,
            JunctionElevationField elevationField,
            Set<String> tunnelApproachEdgeIds) {
        if (fillets == null || fillets.isEmpty() || materials == null) {
            return List.of();
        }
        boolean hasTunnelApproaches = tunnelApproachEdgeIds != null && !tunnelApproachEdgeIds.isEmpty();
        int clearHeight = Math.max(1, RoadConstructionHeuristics.tunnelThreshold(config) + 1);
        int liningThickness = 1;
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        List<SlopeProfileSample> samples = new ArrayList<>();
        long rayId = rayIdBase + 1_000L;
        for (int filletIndex = 0; filletIndex < fillets.size(); filletIndex++) {
            JunctionGeometryResult.FilletCorner fillet = fillets.get(filletIndex);
            if (fillet == null
                    || fillet.center() == null
                    || fillet.tangentStart() == null
                    || fillet.tangentEnd() == null
                    || fillet.radius() <= 1e-6) {
                continue;
            }
            ResolvedCrossSection crossSection = resolveFilletBatterCrossSection(
                fillet, edges, network, config, centerlines);
            if (crossSection == null || !crossSection.includeSlopeBatter) {
                continue;
            }
            String fillBlockId = materials.resolve(crossSection.fillSlopeMaterial);
            String cutBlockId = crossSection.cutSlopeMaterial != null && !crossSection.cutSlopeMaterial.isBlank()
                ? materials.resolve(crossSection.cutSlopeMaterial)
                : null;
            double sweep = fillet.sweepRadians();
            if (!Double.isFinite(sweep) || Math.abs(sweep) < 1e-9) {
                sweep = FilletArcSweep.resolveShort(
                    fillet.center(), fillet.tangentStart(), fillet.tangentEnd());
            }
            if (Math.abs(sweep) < 1e-9 || Math.abs(sweep) > Math.PI + 1e-6) {
                continue;
            }
            double arcLengthBlocks = fillet.radius() * Math.abs(sweep) / Math.max(scale, 1e-9);
            int segs = Math.max(3, (int) Math.ceil(arcLengthBlocks));
            double startAngle = Math.atan2(
                fillet.tangentStart().y - fillet.center().y,
                fillet.tangentStart().x - fillet.center().x);
            // 单一连续剖面：起坡标高贯穿整段，跑距止于圆心前，禁止二段重置 arcY / 穿心。
            int batterRun = maxFilletBatterRunBlocks(fillet.radius(), scale, maxHorizontalRun);
            List<IndexedShoulderRay> arcRays = new ArrayList<>();
            for (int i = 0; i <= segs; i++) {
                double t = (double) i / segs;
                Vec2d onArc = FilletArcSweep.pointOnArc(
                    fillet.center(), fillet.radius(), startAngle + sweep * t);
                Vec2d outward = fillet.center().subtract(onArc);
                if (outward.lengthSquared() < 1e-12) {
                    continue;
                }
                outward = outward.normalize();
                int arcY = elevationField != null ? elevationField.surfaceY(onArc) : junctionY;
                if (!filletSampleAllowsOpenBatter(
                        onArc, arcY, terrain, hasTunnelApproaches, clearHeight, liningThickness)) {
                    continue;
                }
                ShoulderBatterBuild build = buildShoulderBatter(
                    onArc, outward, arcY, 0,
                    terrain, scale, outward, fillBlockId, cutBlockId,
                    crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, batterRun,
                    columnResolver,
                    new SlopeRayId(rayId++, SlopeRayId.ShoulderSide.LEFT));
                samples.addAll(build.samples());
                if (build.ray() != null) {
                    arcRays.add(new IndexedShoulderRay(i, build.ray()));
                }
            }
            for (int i = 1; i < arcRays.size(); i++) {
                IndexedShoulderRay prev = arcRays.get(i - 1);
                IndexedShoulderRay curr = arcRays.get(i);
                if (curr.sampleIndex() != prev.sampleIndex() + 1) {
                    continue;
                }
                if (!SlopeBandRasterizer.canConnectBand(prev.ray(), curr.ray())) {
                    continue;
                }
                samples.addAll(SlopeBandRasterizer.fillBetweenRays(
                    prev.ray(), curr.ray(), terrain, columnResolver));
            }
        }
        return samples;
    }

    /**
     * 圆角外弧边坡最大水平跑距：不超过半径内侧一格，避免射线穿心；
     * 同时受走廊 {@code maxHorizontalRun} 上限。不再另开「续坡」段重置起坡标高。
     */
    static int maxFilletBatterRunBlocks(double radius, double scale, int maxHorizontalRun) {
        double safeScale = scale > 1e-9 ? scale : 1.0;
        int radiusBlocks = Math.max(1, (int) Math.floor(Math.max(0.0, radius) / safeScale));
        int beforeCenter = Math.max(1, radiusBlocks - 1);
        return Math.clamp(maxHorizontalRun, 1, beforeCenter);
    }

    private record IndexedShoulderRay(int sampleIndex, SlopeBandRasterizer.ShoulderRay ray) {
    }

    /**
     * 开敞边坡逐列判定：无隧道进口时允许；有隧道进口时仅非 {@link TunnelColumnConstruction.Mode#ENCLOSED} 列放坡。
     * 不再用自然覆土阈值把开敞路堑误判成隧道。
     */
    static boolean filletSampleAllowsOpenBatter(
            Vec2d planPoint,
            int roadY,
            TerrainSampler terrain,
            boolean hasTunnelApproaches,
            int clearHeight,
            int liningThickness) {
        if (planPoint == null || terrain == null) {
            return false;
        }
        if (!terrain.isChunkLoaded(planPoint)) {
            return false;
        }
        if (!hasTunnelApproaches) {
            return true;
        }
        int roofTop = roadY + Math.max(1, clearHeight) + Math.max(1, liningThickness) - 1;
        TunnelColumnConstruction.Mode mode = TunnelColumnConstruction.resolve(
            false, terrain, planPoint, roofTop);
        return mode != TunnelColumnConstruction.Mode.ENCLOSED;
    }

    static boolean filletCornerAllowsOpenBatter(
            JunctionGeometryResult.FilletCorner fillet,
            int junctionY,
            TerrainSampler terrain,
            int tunnelThreshold,
            double scale,
            boolean hasTunnelApproaches) {
        if (fillet == null || fillet.center() == null || terrain == null) {
            return false;
        }
        Vec2d probe = fillet.center();
        if (fillet.tangentStart() != null && fillet.tangentEnd() != null && fillet.radius() > 1e-6) {
            double sweep = fillet.sweepRadians();
            if (!Double.isFinite(sweep) || Math.abs(sweep) < 1e-9) {
                sweep = FilletArcSweep.resolveShort(
                    fillet.center(), fillet.tangentStart(), fillet.tangentEnd());
            }
            double startAngle = Math.atan2(
                fillet.tangentStart().y - fillet.center().y,
                fillet.tangentStart().x - fillet.center().x);
            probe = FilletArcSweep.pointOnArc(
                fillet.center(), fillet.radius(), startAngle + sweep * 0.5);
        }
        int clearHeight = Math.max(1, tunnelThreshold + 1);
        return filletSampleAllowsOpenBatter(probe, junctionY, terrain, hasTunnelApproaches, clearHeight, 1);
    }

    static ResolvedCrossSection resolveFilletBatterCrossSection(
            JunctionGeometryResult.FilletCorner fillet,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            Function<RoadEdge, List<Vec2d>> centerlines) {
        if (fillet == null || edges == null || network == null || config == null) {
            return null;
        }
        RoadEdge startOwner = JunctionFilletElevationLoft.resolveTangentOwner(
            fillet.tangentStart(), edges, centerlines);
        RoadEdge endOwner = JunctionFilletElevationLoft.resolveTangentOwner(
            fillet.tangentEnd(), edges, centerlines);
        ResolvedCrossSection startCs = startOwner != null
            ? RoadModelUtils.resolveCrossSection(network, startOwner, config) : null;
        ResolvedCrossSection endCs = endOwner != null
            ? RoadModelUtils.resolveCrossSection(network, endOwner, config) : null;
        boolean startOn = startCs != null && startCs.includeSlopeBatter;
        boolean endOn = endCs != null && endCs.includeSlopeBatter;
        if (!startOn && !endOn) {
            return startCs != null ? startCs : endCs;
        }
        if (startOn && !endOn) {
            return startCs;
        }
        if (endOn && !startOn) {
            return endCs;
        }
        // 两侧都开：取更宽者材料
        int startW = startCs != null ? startCs.totalHardSurfaceWidthBlocks() : 0;
        int endW = endCs != null ? endCs.totalHardSurfaceWidthBlocks() : 0;
        return endW > startW ? endCs : startCs;
    }

    private record ShoulderBatterBuild(
            List<SlopeProfileSample> samples,
            SlopeBandRasterizer.ShoulderRay ray) {
        private ShoulderBatterBuild {
            samples = samples != null ? List.copyOf(samples) : List.of();
        }
    }

    private static ShoulderBatterBuild buildShoulderBatter(
            Vec2d center,
            Vec2d corridorLeftNormal,
            int targetY,
            int signedOuterLateral,
            TerrainSampler terrain,
            double scale,
            Vec2d outwardNormal,
            String fillBlockId,
            String cutBlockId,
            float fillRatio,
            float cutRatio,
            int maxHorizontalRun,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            SlopeRayId rayId) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        Vec2d leftNormal = normalize(corridorLeftNormal);
        Vec2d normal = normalize(outwardNormal);
        Vec2d outerEdge = center.add(leftNormal.multiply(signedOuterLateral * scale));
        if (!terrain.isChunkLoaded(outerEdge)) {
            return new ShoulderBatterBuild(samples, null);
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return new ShoulderBatterBuild(samples, null);
        }
        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        if (slopeRatio <= 0f) {
            return new ShoulderBatterBuild(samples, null);
        }
        int usefulHorizontalRun = Math.max(1, maxHorizontalRun);
        String materialId = isFill ? fillBlockId : cutBlockId;
        if (materialId == null || materialId.isBlank()) {
            return new ShoulderBatterBuild(samples, null);
        }

        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            targetY,
            profileDirection,
            horizontalOffset -> {
                Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
                if (!terrain.isChunkLoaded(samplePoint)) {
                    return targetY;
                }
                return terrain.sampleSurfaceY(samplePoint);
            },
            slopeRatio,
            usefulHorizontalRun);

        if (profile.size() <= 1) {
            return new ShoulderBatterBuild(samples, null);
        }

        boolean meetsTerrain = slopeProfileMeetsTerrain(profile, isFill, outerEdge, normal, scale, terrain);
        boolean widthCapped = !meetsTerrain && profile.getLast()[0] >= usefulHorizontalRun;
        SlopeConstructionOutcome outcome = SlopeConstructionOutcome.classify(meetsTerrain, widthCapped);
        boolean allowWaterEmbankment = false;

        for (int step = 0; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
            if (!terrain.isChunkLoaded(samplePoint)) {
                continue;
            }
            if (RoadEarthworkPolicy.suppressFillOverWater(
                    isFill, allowWaterEmbankment, terrain.findExposedWaterSurface(samplePoint))) {
                continue;
            }
            ColumnKey key = new ColumnKey(columnResolver.worldX(samplePoint), columnResolver.worldZ(samplePoint));
            samples.add(new SlopeProfileSample(
                key, samplePoint, slopeHeight, isFill, materialId, rayId,
                horizontalOffset, slopeRatio, outcome, allowWaterEmbankment));
        }
        int maxProfileOffset = profile.stream().mapToInt(p -> p[0]).max().orElse(0);
        maxProfileOffset = Math.min(maxProfileOffset, maxHorizontalRun);
        SlopeBandRasterizer.ShoulderRay ray = new SlopeBandRasterizer.ShoulderRay(
            rayId,
            outerEdge,
            normal,
            SlopeBandRasterizer.heightTableFromProfile(profile, maxProfileOffset),
            isFill,
            materialId,
            slopeRatio,
            outcome,
            allowWaterEmbankment,
            false,
            scale);
        return new ShoulderBatterBuild(samples, ray);
    }

    private static boolean slopeProfileMeetsTerrain(
            List<int[]> profile,
            boolean isFill,
            Vec2d outerEdge,
            Vec2d outwardNormal,
            double scale,
            TerrainSampler terrain) {
        if (profile == null || profile.size() <= 1) {
            return true;
        }
        int[] last = profile.getLast();
        int horizontalOffset = last[0];
        int slopeHeight = last[1];
        Vec2d samplePoint = outerEdge.add(outwardNormal.multiply(horizontalOffset * scale));
        if (!terrain.isChunkLoaded(samplePoint)) {
            return false;
        }
        int groundHeight = terrain.sampleSurfaceY(samplePoint);
        if (isFill) {
            return slopeHeight <= groundHeight + 1;
        }
        return slopeHeight >= groundHeight - 1;
    }

    private static void emitConsolidatedSlopeColumns(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            int bridgeThreshold,
            ColumnClearCapResolver columnClearCap,
            Set<ColumnKey> pavedColumns) {
        Set<ColumnKey> forbidden = pavedColumns != null ? pavedColumns : Set.of();
        ColumnClearCapResolver clearCap = columnClearCap != null
            ? columnClearCap
            : constantClearCap(null);
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            if (plan.isSlopeColumnIncomplete(key)) {
                continue;
            }
            if (forbidden.contains(key)) {
                continue;
            }
            SlopeColumnCell slope = entry.getValue();
            Vec2d planPoint = slope.planPoint();
            int worldX = columnResolver.worldX(planPoint);
            int worldZ = columnResolver.worldZ(planPoint);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            int groundY = terrain.sampleSurfaceY(planPoint);
            int designY = slope.hasFill()
                ? slope.fillSurfaceY()
                : slope.hasCut() ? slope.cutSurfaceY() : groundY;
            Integer maxClearY = clearCap.resolve(key, planPoint, designY);
            boolean waterBlocksFill = RoadEarthworkPolicy.suppressFillOverWater(
                true,
                false,
                terrain.findExposedWaterSurface(planPoint));
            boolean retainingTip = slope.needsRetainingFinish();
            // 路口圆角边坡不授权水面堤道；与走廊 suppressFillOverWater 一致。
            if (slope.hasFill() && !waterBlocksFill) {
                int fillTop = slope.fillSurfaceY();
                if (maxClearY != null) {
                    fillTop = Math.min(fillTop, maxClearY);
                }
                if (fillTop - groundY <= bridgeThreshold && fillTop > groundY) {
                    String fillId = slope.fillMaterialId();
                    if (fillId != null && !fillId.isBlank()) {
                        for (int y = groundY + 1; y <= fillTop; y++) {
                            solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillId);
                        }
                    }
                }
            }
            if (slope.hasCut() && groundY > slope.cutSurfaceY()) {
                // 隧道净空上限下禁止开敞切坡挖穿地表；浅覆土直接跳过挖方。
                if (maxClearY != null && groundY <= maxClearY) {
                    continue;
                }
                // 开敞边坡：清除含自然地表顶层；封闭隧道仍走 resolveTunnelSolidPunchTop。
                int cutTop = maxClearY != null
                    ? JunctionTerrainClearance.resolveTunnelSolidPunchTop(
                        slope.cutSurfaceY(), groundY, maxClearY)
                    : groundY;
                int cutFaceY = maxClearY != null
                    ? Math.min(slope.cutSurfaceY(), maxClearY - 1)
                    : slope.cutSurfaceY();
                if (cutTop > cutFaceY) {
                    for (int y = cutFaceY + 1; y <= cutTop; y++) {
                        if (y > groundY) {
                            break;
                        }
                        // 挡墙将占据 faceY..groundY-1；同柱清障 AIR 不得抢占。
                        if (retainingTip && y >= cutFaceY && y < groundY) {
                            continue;
                        }
                        solids.add(
                            planPoint,
                            y,
                            RoadSolidLayer.TUNNEL,
                            "minecraft:air",
                            RoadSolidMaterialPriority.AIR);
                    }
                    String batterId = slope.cutMaterialId();
                    if (batterId != null && !batterId.isBlank() && cutFaceY < cutTop && cutFaceY < groundY) {
                        solids.add(planPoint, cutFaceY, RoadSolidLayer.SUBGRADE, batterId);
                    }
                    if (maxClearY == null) {
                        // 开敞街角边坡：摘装饰与悬空残留；不得按隧道覆土保护把自然实心留下。
                        JunctionTerrainClearance.clearFloatingAndDecorationsAbove(
                            solids, planPoint, terrain, worldX, worldZ, cutFaceY, groundY,
                            Math.max(terrain.sampleColumnTopY(planPoint), groundY + 24), false);
                    }
                }
            }
            // 宽度截断收尾：与走廊 emitRetainingFinish 同一竖向支撑，避免半坡悬空。
            if (retainingTip && !(slope.hasFill() && waterBlocksFill)) {
                String fallback = slope.hasCut()
                    ? slope.cutMaterialId()
                    : slope.fillMaterialId();
                RoadCorridorSolidEmitter.emitRetainingFinish(
                    solids, slope, planPoint, groundY, fallback, null);
            }
        }
    }

    private static void stampSurfaceBand(
            RoadSolidModel solids,
            Vec2d cellPoint,
            int junctionY,
            ResolvedCrossSection.SurfaceBandSpec spec,
            String seedKey,
            MaterialResolver materials) {
        String materialId;
        if (spec.materialMix() != null) {
            BlockPos pos = materials.canvasToBlockPos(cellPoint).withY(junctionY);
            materialId = MaterialMixResolver.resolve(
                spec.materialMix(), pos, seedKey, materials::resolve);
        } else {
            materialId = materials.resolve(spec.materialId());
        }
        solids.add(cellPoint, junctionY, spec.layer(), materialId);
    }

    /** 广场行车道：采用最宽进口道路的车行道材质。 */
    private static void stampPlazaRoad(
            RoadSolidModel solids,
            Vec2d cellPoint,
            int junctionY,
            ResolvedCrossSection widestSection,
            RoadEdge widestEdge,
            MaterialResolver materials) {
        if (widestSection != null && widestSection.carriagewayMaterial != null && materials != null) {
            BlockPos pos = materials.canvasToBlockPos(cellPoint).withY(junctionY);
            String seed = widestEdge != null ? widestEdge.getId() : "plaza";
            String materialId = MaterialMixResolver.resolve(
                widestSection.carriagewayMaterial, pos, seed, materials::resolve);
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD, materialId);
            return;
        }
        solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
    }

    static RoadEdge selectWidestEdge(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            String nodeId) {
        return JunctionMaterialPolicy.selectMaterialOwner(
            edges, network, config, nodeId, edge -> true);
    }

    /** 在进口投影点对应的局部桩号解析横断面（变宽路段条带分类）。 */
    private static ResolvedCrossSection resolveApproachCrossSection(
            RoadNetwork network,
            EdgeProjection projection,
            String nodeId,
            RoadSystemConfig config,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            double unitsPerBlock) {
        if (projection == null || projection.edge() == null) {
            return ResolvedCrossSection.fromConfig(config);
        }
        double alongCanvas = distanceAlongFromNode(
            centerlineResolver.apply(projection.edge()),
            projection.edge(),
            nodeId,
            projection.projectedCenter());
        // along 与桩号域一致：画布距离；unitsPerBlock 仅供调用方横向分带使用
        return RoadModelUtils.resolveCrossSectionAlongApproach(
            network, projection.edge(), nodeId, alongCanvas, config);
    }

    private static double distanceAlongFromNode(
            List<Vec2d> centerline,
            RoadEdge edge,
            String nodeId,
            Vec2d projectedCenter) {
        if (centerline == null || centerline.size() < 2 || projectedCenter == null || edge == null) {
            return 0.0;
        }
        boolean fromStart = nodeId != null && nodeId.equals(edge.getStartNodeId());
        double along = 0.0;
        if (fromStart) {
            for (int i = 0; i < centerline.size() - 1; i++) {
                Vec2d a = centerline.get(i);
                Vec2d b = centerline.get(i + 1);
                Vec2d foot = RoadGeometryUtils.projectPointOnSegment(a, b, projectedCenter);
                double seg = a.distance(b);
                double toFoot = a.distance(foot);
                if (foot.distance(projectedCenter) < 1e-4 || toFoot <= seg + 1e-6) {
                    if (Math.abs(foot.x - projectedCenter.x) < 1e-3
                            && Math.abs(foot.y - projectedCenter.y) < 1e-3) {
                        return along + toFoot;
                    }
                }
                // 累计到包含投影的线段
                Vec2d ab = b.subtract(a);
                double len2 = ab.lengthSquared();
                if (len2 > 1e-12) {
                    double t = projectedCenter.subtract(a).dot(ab) / len2;
                    if (t >= -1e-6 && t <= 1.0 + 1e-6) {
                        return along + Math.clamp(t, 0.0, 1.0) * seg;
                    }
                }
                along += seg;
            }
            return along;
        }
        // 自 end 节点向外：从尾端反向累计
        along = 0.0;
        for (int i = centerline.size() - 1; i > 0; i--) {
            Vec2d a = centerline.get(i);
            Vec2d b = centerline.get(i - 1);
            double seg = a.distance(b);
            Vec2d ab = b.subtract(a);
            double len2 = ab.lengthSquared();
            if (len2 > 1e-12) {
                double t = projectedCenter.subtract(a).dot(ab) / len2;
                if (t >= -1e-6 && t <= 1.0 + 1e-6) {
                    return along + Math.clamp(t, 0.0, 1.0) * seg;
                }
            }
            along += seg;
        }
        return along;
    }

    private static void gradeColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int junctionY,
            String fillMaterialId,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            TerrainSampler terrain,
            int tunnelThreshold,
            int bridgeThreshold,
            Integer maxClearY) {
        if (columnResolver == null) {
            return;
        }
        int worldX = columnResolver.worldX(planPoint);
        int worldZ = columnResolver.worldZ(planPoint);
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        int groundY = terrain.sampleSurfaceY(planPoint);
        // fail-closed：禁止破坏性清障（不得退化为露天挖到地表）。
        if (maxClearY != null
                && maxClearY == JunctionConstructionContext.CLEAR_DENIED) {
            return;
        }
        if (maxClearY != null) {
            // 与 clearOverhead / shell 共用 resolveTunnelSolidPunchTop：
            // 浅覆土 cutTop==junctionY → 不切穿覆土；深覆土止于洞顶下。
            int cutTop = JunctionTerrainClearance.resolveTunnelSolidPunchTop(
                junctionY, groundY, maxClearY);
            if (cutTop > junctionY) {
                JunctionTerrainClearance.punchColumnBelowSurface(
                    solids, planPoint, junctionY, cutTop, terrain);
            }
            int gradingGroundY = Math.min(groundY, maxClearY);
            if (gradingGroundY < junctionY && junctionY - gradingGroundY <= bridgeThreshold) {
                for (int y = gradingGroundY + 1; y <= junctionY; y++) {
                    solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillMaterialId);
                }
            }
            return;
        }
        // 开敞平交：铺装列直接露天清到自然地表（CLEARANCE），不再走隧道阈值局部掏挖。
        // clearOverheadColumn 仍会再扫一遍装饰/柱顶；此处保证圆角铺装列即使漏进清障栅格也能去土。
        if (groundY > junctionY) {
            for (int y = junctionY + 1; y <= groundY; y++) {
                solids.add(
                    planPoint,
                    y,
                    RoadSolidLayer.TUNNEL,
                    "minecraft:air",
                    RoadSolidMaterialPriority.CLEARANCE);
            }
            int columnTop = terrain.sampleColumnTopY(planPoint);
            JunctionTerrainClearance.clearFloatingAndDecorationsAbove(
                solids,
                planPoint,
                terrain,
                worldX,
                worldZ,
                junctionY,
                groundY,
                Math.max(columnTop, groundY + 24),
                false);
        } else if (groundY < junctionY && junctionY - groundY <= bridgeThreshold) {
            if (fillMaterialId != null && !fillMaterialId.isBlank()) {
                for (int y = groundY + 1; y < junctionY; y++) {
                    solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillMaterialId);
                }
            }
        }
    }

    private static Vec2d approachLeftNormal(
            RoadEdge edge,
            String nodeId,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        List<Vec2d> centerline = centerlineResolver.apply(edge);
        Vec2d approach = RoadJunctionGeometry.computeApproachDirection(centerline, edge, nodeId);
        if (approach.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return approach.normalize().perpendicular();
    }

    private static EdgeProjection nearestEdgeProjection(
            Vec2d point,
            String nodeId,
            List<RoadEdge> edges,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        EdgeProjection best = null;
        for (RoadEdge edge : edges) {
            List<Vec2d> centerline = centerlineResolver.apply(edge);
            if (centerline == null || centerline.size() < 2) {
                continue;
            }
            for (int i = 0; i < centerline.size() - 1; i++) {
                Vec2d start = centerline.get(i);
                Vec2d end = centerline.get(i + 1);
                Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, point);
                double distance = projected.distance(point);
                if (best == null || distance < best.distance()) {
                    best = new EdgeProjection(edge, projected, distance);
                }
            }
        }
        return best;
    }

    private static Vec2d normalize(Vec2d vector) {
        return vector != null && vector.lengthSquared() > 1e-12
            ? vector.normalize()
            : new Vec2d(0, 1);
    }

    private record EdgeProjection(RoadEdge edge, Vec2d projectedCenter, double distance) {
    }
}
