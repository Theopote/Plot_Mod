package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
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
        if (solids == null || cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null) {
            return false;
        }

        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        EdgeProjection projection = nearestEdgeProjection(cellPoint, nodeId, edges, centerlineResolver);
        if (projection == null) {
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
            gradeColumn(
                solids, cellPoint, junctionY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold,
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
            stampSurfaceBand(
                solids, cellPoint, junctionY, band.get(), projection.edge().getId(), materials);
        } else if (lateral >= crossSection.minSurfaceLateral()
                && lateral <= crossSection.maxSurfaceLateral()) {
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
        } else {
            // Polygon cell outside nearest approach band: still pave the junction interior.
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
        }
        gradeColumn(
            solids, cellPoint, junctionY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold,
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
            int bridgeThreshold) {
        stampApproachSlopeBatters(
            solids, junctionCenter, junctionY, nodeId, edges, network, config, materials,
            columnResolver, unitsPerBlock, terrain, bridgeThreshold, null);
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

        if (samples.isEmpty()) {
            return;
        }
        RoadCorridorPlan slopePlan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(slopePlan, samples, terrain);
        emitConsolidatedSlopeColumns(solids, slopePlan, terrain, columnResolver, bridgeThreshold, maxClearY);
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
            int cutTop = Math.min(groundY, maxClearY);
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
