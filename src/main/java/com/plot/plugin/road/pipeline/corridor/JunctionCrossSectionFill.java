package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
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
        if (solids == null || cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null) {
            return false;
        }
        int stampY = surfaceY;

        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
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

    /**
     * Resolves the surface layer that {@link #fillCell} would stamp at this plan cell.
     */
    public static RoadSolidLayer resolveSurfaceLayer(
            Vec2d cellPoint,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            double unitsPerBlock) {
        if (cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null) {
            return RoadSolidLayer.ROAD;
        }
        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
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
            JunctionFilletElevationLoft filletLoft,
            Set<String> tunnelApproachEdgeIds) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold, maxClearY, fillets,
            filletLoft, tunnelApproachEdgeIds, Set.of());
    }

    /**
     * 路口边坡：仅从路缘圆角真实外弧向外生成，不再从交叉点中心打独立路肩射线
     * （中心+半宽会侵入邻路/圆角补铺）。{@code pavedColumns} 内世界列禁止写入边坡。
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
            JunctionFilletElevationLoft filletLoft,
            Set<String> tunnelApproachEdgeIds,
            Set<ColumnKey> pavedColumns) {
        if (solids == null || junctionCenter == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null
                || columnResolver == null) {
            return;
        }
        // junctionCenter / tunnelApproachEdgeIds 保留于签名以兼容调用方；中心路肩已取消。
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int maxHorizontalRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        List<SlopeProfileSample> samples = new ArrayList<>(collectFilletArcBatterSamples(
            fillets, junctionY, edges, network, config, materials, scale, maxHorizontalRun,
            terrain, columnResolver, 0L, filletLoft));

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
            solids, slopePlan, terrain, columnResolver, bridgeThreshold, maxClearY, forbidden);
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
            JunctionFilletElevationLoft filletLoft) {
        if (fillets == null || fillets.isEmpty() || materials == null) {
            return List.of();
        }
        RoadEdge owner = selectWidestEdge(edges, network, config);
        if (owner == null) {
            return List.of();
        }
        ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, owner, config);
        if (!crossSection.includeSlopeBatter) {
            return List.of();
        }
        String fillBlockId = materials.resolve(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial != null && !crossSection.cutSlopeMaterial.isBlank()
            ? materials.resolve(crossSection.cutSlopeMaterial)
            : null;
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(config);
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
            if (!filletCornerAllowsOpenBatter(fillet, junctionY, terrain, tunnelThreshold, scale)) {
                continue;
            }
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
            // 外法向指向圆心：水平跑距不得超过半径，否则穿心与对侧射线交叉堆块。
            int cappedRun = maxFilletBatterRunBlocks(fillet.radius(), scale, maxHorizontalRun);
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
                int arcY = filletLoft != null ? filletLoft.surfaceY(onArc) : junctionY;
                ShoulderBatterBuild build = buildShoulderBatter(
                    onArc, outward, arcY, 0,
                    terrain, scale, outward, fillBlockId, cutBlockId,
                    crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, cappedRun,
                    columnResolver,
                    new SlopeRayId(rayId++, SlopeRayId.ShoulderSide.LEFT));
                samples.addAll(build.samples());
                if (build.ray() != null) {
                    arcRays.add(new IndexedShoulderRay(i, build.ray()));
                }
            }
            // 只连接原始采样序号相邻的射线，跳过缺射线造成的跨空环带。
            for (int i = 1; i < arcRays.size(); i++) {
                IndexedShoulderRay prev = arcRays.get(i - 1);
                IndexedShoulderRay curr = arcRays.get(i);
                if (curr.sampleIndex() != prev.sampleIndex() + 1) {
                    continue;
                }
                samples.addAll(SlopeBandRasterizer.fillBetweenRays(
                    prev.ray(), curr.ray(), terrain, columnResolver));
            }
        }
        return samples;
    }

    /**
     * 圆角外弧边坡最大水平跑距：不超过半径内侧一格，避免射线穿心。
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
     * 街角覆土达到隧道门槛时视为洞内圆角：不做开敞放坡。
     * 混合洞口路口仅对仍开敞的街角放坡。
     */
    static boolean filletCornerAllowsOpenBatter(
            JunctionGeometryResult.FilletCorner fillet,
            int junctionY,
            TerrainSampler terrain,
            int tunnelThreshold,
            double scale) {
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
        if (!terrain.isChunkLoaded(probe)) {
            return false;
        }
        int cover = terrain.sampleSurfaceY(probe) - junctionY;
        int threshold = Math.max(2, tunnelThreshold);
        return cover < threshold;
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
            Integer maxClearY,
            Set<ColumnKey> pavedColumns) {
        Set<ColumnKey> forbidden = pavedColumns != null ? pavedColumns : Set.of();
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
            RoadSystemConfig config) {
        return selectWidestEdge(edges, network, config, null);
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
        if (maxClearY != null) {
            int groundY = terrain.sampleSurfaceY(planPoint);
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
        RoadRoadbedGradingUtils.gradeColumn(
            solids,
            planPoint,
            junctionY,
            tunnelThreshold,
            bridgeThreshold,
            fillMaterialId,
            worldX,
            worldZ,
            terrain);
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
