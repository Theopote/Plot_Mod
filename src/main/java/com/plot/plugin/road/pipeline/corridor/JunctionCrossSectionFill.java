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
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        RoadEdge widestEdge = selectWidestEdge(edges, network, config);
        ResolvedCrossSection widestSection = widestEdge != null
            ? RoadModelUtils.resolveCrossSection(network, widestEdge, config)
            : null;
        if (projection == null) {
            stampPlazaRoad(solids, cellPoint, stampY, widestSection, widestEdge, materials);
            gradeColumn(
                solids, cellPoint, stampY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold,
                maxClearY);
            return true;
        }

        ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, projection.edge(), config);
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
        ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, projection.edge(), config);
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
            Integer maxClearY) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold, maxClearY, List.of(), null);
    }

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
            List<JunctionGeometryResult.FilletCorner> fillets) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold, maxClearY, fillets, null);
    }

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
            JunctionFilletElevationLoft filletLoft) {
        if (solids == null || junctionCenter == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null
                || columnResolver == null) {
            return;
        }

        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int maxHorizontalRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        List<SlopeProfileSample> samples = new ArrayList<>();
        long stationSampleId = 0L;

        for (RoadEdge edge : edges) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, edge, config);
            if (!crossSection.includeSlopeBatter) {
                continue;
            }
            Vec2d leftNormal = approachLeftNormal(edge, nodeId, centerlineResolver);
            if (leftNormal.lengthSquared() < 1e-12) {
                continue;
            }
            String fillBlockId = materials.resolve(crossSection.fillSlopeMaterial);
            String cutBlockId = crossSection.cutSlopeMaterial != null && !crossSection.cutSlopeMaterial.isBlank()
                ? materials.resolve(crossSection.cutSlopeMaterial)
                : null;

            samples.addAll(collectShoulderBatterSamples(
                junctionCenter, leftNormal, junctionY, crossSection.maxSurfaceLateral(),
                terrain, scale, leftNormal, fillBlockId, cutBlockId,
                crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, maxHorizontalRun,
                columnResolver, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.LEFT)));
            samples.addAll(collectShoulderBatterSamples(
                junctionCenter, leftNormal, junctionY, crossSection.minSurfaceLateral(),
                terrain, scale, leftNormal.multiply(-1), fillBlockId, cutBlockId,
                crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, maxHorizontalRun,
                columnResolver, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.RIGHT)));
            stationSampleId++;
        }

        samples.addAll(collectFilletArcBatterSamples(
            fillets, junctionY, edges, network, config, materials, scale, maxHorizontalRun,
            terrain, columnResolver, stationSampleId, filletLoft));

        if (samples.isEmpty()) {
            return;
        }
        RoadCorridorPlan slopePlan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(slopePlan, samples, terrain);
        emitConsolidatedSlopeColumns(solids, slopePlan, terrain, columnResolver, bridgeThreshold, maxClearY);
    }

    /**
     * 沿路缘圆角短弧向外打边坡：圆心在草地侧，弧上点外法向指向草地（{@code center - arcPoint}）。
     * 材质/坡比跟随路口材质主道路；关闭 {@code includeSlopeBatter} 时跳过。
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
                // signedOuterLateral=0：弧点即路缘外缘
                samples.addAll(collectShoulderBatterSamples(
                    onArc, outward, arcY, 0,
                    terrain, scale, outward, fillBlockId, cutBlockId,
                    crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, maxHorizontalRun,
                    columnResolver,
                    new SlopeRayId(rayId + filletIndex * 64L + i, SlopeRayId.ShoulderSide.LEFT)));
            }
        }
        return samples;
    }

    private static List<SlopeProfileSample> collectShoulderBatterSamples(
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
            return samples;
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return samples;
        }
        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        if (slopeRatio <= 0f) {
            return samples;
        }
        int heightDifference = Math.abs(targetY - groundAtEdge);
        int unconstrainedRun = (int) Math.ceil(heightDifference * Math.max(0.5f, slopeRatio)) + 2;
        boolean widthCapped = unconstrainedRun > maxHorizontalRun;
        int usefulHorizontalRun = Math.clamp(unconstrainedRun, 2, maxHorizontalRun);
        String materialId = isFill ? fillBlockId : cutBlockId;
        if (materialId == null || materialId.isBlank()) {
            return samples;
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
            return samples;
        }

        boolean meetsTerrain = slopeProfileMeetsTerrain(profile, isFill, outerEdge, normal, scale, terrain);
        SlopeConstructionOutcome outcome = SlopeConstructionOutcome.classify(meetsTerrain, widthCapped);

        for (int step = 0; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
            if (!terrain.isChunkLoaded(samplePoint)) {
                continue;
            }
            ColumnKey key = new ColumnKey(columnResolver.worldX(samplePoint), columnResolver.worldZ(samplePoint));
            samples.add(new SlopeProfileSample(
                key, samplePoint, slopeHeight, isFill, materialId, rayId,
                horizontalOffset, slopeRatio, outcome));
        }
        return samples;
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
            int bridgeThreshold) {
        emitConsolidatedSlopeColumns(solids, plan, terrain, columnResolver, bridgeThreshold, null);
    }

    private static void emitConsolidatedSlopeColumns(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            int bridgeThreshold,
            Integer maxClearY) {
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            if (plan.isSlopeColumnIncomplete(key)) {
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
            if (slope.hasFill()) {
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
                int cutTop = maxClearY != null ? Math.min(groundY, maxClearY) : groundY;
                int cutFaceY = maxClearY != null
                    ? Math.min(slope.cutSurfaceY(), maxClearY)
                    : slope.cutSurfaceY();
                if (cutTop > cutFaceY) {
                    for (int y = cutFaceY + 1; y <= cutTop; y++) {
                        solids.add(
                            planPoint,
                            y,
                            RoadSolidLayer.TUNNEL,
                            "minecraft:air",
                            RoadSolidMaterialPriority.AIR);
                    }
                    String batterId = slope.cutMaterialId();
                    if (batterId != null && !batterId.isBlank() && cutFaceY < cutTop) {
                        solids.add(planPoint, cutFaceY, RoadSolidLayer.SUBGRADE, batterId);
                    }
                }
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
        return JunctionMaterialPolicy.selectMaterialOwner(edges, network, config);
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
            int columnTop = terrain.sampleColumnTopY(planPoint);
            // 立交下层：清到桥底（含柱顶/残留），不只 engineering surface
            int cutTop = Math.min(Math.max(groundY, columnTop), maxClearY);
            if (cutTop > junctionY) {
                JunctionTerrainClearance.punchColumn(solids, planPoint, junctionY, cutTop);
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
