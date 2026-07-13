package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;

/**
 * 道路路基平整：按设计标高对每列地形做挖方（含隧道腔体）与填方。
 */
public final class RoadRoadbedGradingUtils {
    private RoadRoadbedGradingUtils() {
    }

    public record GradingVolumes(int cutVolume, int fillVolume) {
        public static final GradingVolumes ZERO = new GradingVolumes(0, 0);

        public GradingVolumes add(GradingVolumes other) {
            if (other == null) {
                return this;
            }
            return new GradingVolumes(cutVolume + other.cutVolume, fillVolume + other.fillVolume);
        }
    }

    public static GradingVolumes gradeColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId,
            int worldX,
            int worldZ,
            TerrainSampler terrain) {
        if (solids == null || planPoint == null || terrain == null) {
            return GradingVolumes.ZERO;
        }
        int groundY = terrain.sampleSurfaceY(planPoint);
        if (groundY > roadY) {
            return cutColumn(solids, planPoint, roadY, groundY, tunnelThreshold, worldX, worldZ, terrain);
        }
        if (groundY < roadY && roadY - groundY <= bridgeThreshold) {
            return fillColumn(solids, planPoint, roadY, groundY, fillMaterialId, worldX, worldZ, terrain);
        }
        return GradingVolumes.ZERO;
    }

    public static GradingVolumes gradeCrossSectionEnvelope(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int roadY,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver) {
        if (solids == null || center == null || leftNormal == null || widthBlocks <= 0
                || terrain == null || columnResolver == null) {
            return GradingVolumes.ZERO;
        }
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        GradingVolumes total = GradingVolumes.ZERO;
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            Vec2d planPoint = center.add(normal.multiply(lateral));
            int worldX = columnResolver.worldX(planPoint);
            int worldZ = columnResolver.worldZ(planPoint);
            total = total.add(gradeColumn(
                solids,
                planPoint,
                roadY,
                tunnelThreshold,
                bridgeThreshold,
                fillMaterialId,
                worldX,
                worldZ,
                terrain));
        }
        return total;
    }

    private static GradingVolumes cutColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int groundY,
            int tunnelThreshold,
            int worldX,
            int worldZ,
            TerrainSampler terrain) {
        RoadTerrainClearanceUtils.OverheadMode mode = RoadTerrainClearanceUtils.classify(
            roadY, groundY, worldX, worldZ, tunnelThreshold, terrain);
        if (mode == RoadTerrainClearanceUtils.OverheadMode.NONE) {
            return GradingVolumes.ZERO;
        }
        int cleared = 0;
        int topY = mode == RoadTerrainClearanceUtils.OverheadMode.TUNNEL
            ? roadY + tunnelThreshold
            : groundY;
        for (int y = roadY + 1; y <= topY; y++) {
            solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air");
            cleared++;
        }
        return new GradingVolumes(cleared, 0);
    }

    private static GradingVolumes fillColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int groundY,
            String fillMaterialId,
            int worldX,
            int worldZ,
            TerrainSampler terrain) {
        if (fillMaterialId == null || fillMaterialId.isBlank()) {
            return GradingVolumes.ZERO;
        }
        int filled = 0;
        for (int y = groundY + 1; y < roadY; y++) {
            solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillMaterialId);
            filled++;
        }
        return new GradingVolumes(0, filled);
    }
}
