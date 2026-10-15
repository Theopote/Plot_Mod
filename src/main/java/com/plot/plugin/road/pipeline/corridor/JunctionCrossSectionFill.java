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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stamps full cross-section surface bands and shoulder batters inside junction fill polygons.
 */
public final class JunctionCrossSectionFill {
    private JunctionCrossSectionFill() {
    }

    public interface MaterialResolver {
        String resolve(String materialId);

        BlockPos canvasToBlockPos(Vec2d planPoint);
    }

    public static void fillCell(
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
        if (solids == null || cellPoint == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null) {
            return;
        }

        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        EdgeProjection projection = nearestEdgeProjection(cellPoint, nodeId, edges, centerlineResolver);
        if (projection == null) {
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
            gradeColumn(
                solids, cellPoint, junctionY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold);
            return;
        }

        ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, projection.edge(), config);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d leftNormal = approachLeftNormal(projection.edge(), nodeId, centerlineResolver);
        double lateralDistance = cellPoint.subtract(projection.projectedCenter()).dot(leftNormal);
        int lateral = (int) Math.round(lateralDistance / scale);

        Optional<ResolvedCrossSection.SurfaceBandSpec> band = crossSection.surfaceBandAtLateral(lateral);
        boolean stamped = false;
        if (band.isPresent()) {
            stampSurfaceBand(
                solids, cellPoint, junctionY, band.get(), projection.edge().getId(), materials);
            stamped = true;
        } else if (lateral >= crossSection.minSurfaceLateral()
                && lateral <= crossSection.maxSurfaceLateral()) {
            solids.add(cellPoint, junctionY, RoadSolidLayer.ROAD);
            stamped = true;
        }

        if (stamped) {
            gradeColumn(
                solids, cellPoint, junctionY, fillMaterialId, columnResolver, terrain, tunnelThreshold, bridgeThreshold);
        }
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
        if (solids == null || junctionCenter == null || nodeId == null || edges == null || edges.isEmpty()
                || network == null || config == null || materials == null || terrain == null) {
            return;
        }

        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int maxHorizontalRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;

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

            stampShoulderBatter(
                solids, junctionCenter, leftNormal, junctionY, crossSection.maxSurfaceLateral(),
                terrain, scale, leftNormal, fillBlockId, cutBlockId,
                crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, maxHorizontalRun,
                columnResolver, bridgeThreshold);
            stampShoulderBatter(
                solids, junctionCenter, leftNormal, junctionY, crossSection.minSurfaceLateral(),
                terrain, scale, leftNormal.multiply(-1), fillBlockId, cutBlockId,
                crossSection.fillSlopeRatio, crossSection.cutSlopeRatio, maxHorizontalRun,
                columnResolver, bridgeThreshold);
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

    private static void stampShoulderBatter(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
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
            int bridgeThreshold) {
        Vec2d corridorLeftNormal = normalize(leftNormal);
        Vec2d normal = normalize(outwardNormal);
        Vec2d outerEdge = center.add(corridorLeftNormal.multiply(signedOuterLateral * scale));
        if (!terrain.isChunkLoaded(outerEdge)) {
            return;
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return;
        }
        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        if (slopeRatio <= 0f) {
            return;
        }
        int heightDifference = Math.abs(targetY - groundAtEdge);
        int unconstrainedRun = (int) Math.ceil(heightDifference * Math.max(0.5f, slopeRatio)) + 2;
        int usefulHorizontalRun = Math.clamp(unconstrainedRun, 2, maxHorizontalRun);
        String materialId = isFill ? fillBlockId : cutBlockId;
        if (materialId == null || materialId.isBlank()) {
            return;
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

        for (int step = 1; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
            writeSlopeColumn(
                solids, samplePoint, slopeHeight, isFill, materialId, terrain, columnResolver, bridgeThreshold);
        }
    }

    private static void writeSlopeColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int slopeSurfaceY,
            boolean isFill,
            String materialId,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            int bridgeThreshold) {
        if (columnResolver == null) {
            return;
        }
        int worldX = columnResolver.worldX(planPoint);
        int worldZ = columnResolver.worldZ(planPoint);
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        int groundY = terrain.sampleSurfaceY(planPoint);
        if (isFill) {
            for (int y = groundY + 1; y <= slopeSurfaceY; y++) {
                solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, materialId);
            }
        } else if (groundY > slopeSurfaceY) {
            for (int y = slopeSurfaceY + 1; y <= groundY; y++) {
                solids.add(
                    planPoint,
                    y,
                    RoadSolidLayer.TUNNEL,
                    "minecraft:air",
                    RoadSolidMaterialPriority.AIR);
            }
            solids.add(planPoint, slopeSurfaceY, RoadSolidLayer.SUBGRADE, materialId);
        }
    }

    private static void gradeColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int junctionY,
            String fillMaterialId,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            TerrainSampler terrain,
            int tunnelThreshold,
            int bridgeThreshold) {
        if (columnResolver == null) {
            return;
        }
        int worldX = columnResolver.worldX(planPoint);
        int worldZ = columnResolver.worldZ(planPoint);
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
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
