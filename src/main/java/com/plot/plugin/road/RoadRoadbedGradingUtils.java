package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

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
        return gradeColumnForType(
            solids, planPoint, roadY, tunnelThreshold, bridgeThreshold, fillMaterialId,
            worldX, worldZ, terrain, null);
    }

    public static GradingVolumes gradeColumnForType(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int tunnelThreshold,
            int bridgeThreshold,
            String fillMaterialId,
            int worldX,
            int worldZ,
            TerrainSampler terrain,
            RoadConstructionType constructionType) {
        if (solids == null || planPoint == null || terrain == null) {
            return GradingVolumes.ZERO;
        }
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return GradingVolumes.ZERO;
        }
        int groundY = terrain.sampleSurfaceY(planPoint);
        if (constructionType == RoadConstructionType.BRIDGE
                || constructionType == RoadConstructionType.TUNNEL) {
            return GradingVolumes.ZERO;
        }
        if (constructionType == RoadConstructionType.FILL) {
            return groundY < roadY
                ? fillColumn(solids, planPoint, roadY, groundY, fillMaterialId, worldX, worldZ, terrain)
                : GradingVolumes.ZERO;
        }
        if (constructionType == RoadConstructionType.CUT) {
            return groundY > roadY
                ? cutOpenColumn(solids, planPoint, roadY, groundY)
                : GradingVolumes.ZERO;
        }
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
        return gradeCrossSectionEnvelope(
            solids, center, leftNormal, widthBlocks, roadY,
            tunnelThreshold, bridgeThreshold, fillMaterialId, terrain, columnResolver, 1.0);
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
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        return gradeCrossSectionEnvelope(
            solids, center, leftNormal, widthBlocks, roadY, tunnelThreshold, bridgeThreshold,
            fillMaterialId, terrain, columnResolver, canvasUnitsPerBlock, null);
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
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock,
            RoadConstructionType constructionType) {
        if (solids == null || center == null || leftNormal == null || widthBlocks <= 0
                || terrain == null || columnResolver == null) {
            return GradingVolumes.ZERO;
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        GradingVolumes total = GradingVolumes.ZERO;
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            Vec2d planPoint = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(planPoint);
            int worldZ = columnResolver.worldZ(planPoint);
            total = total.add(gradeColumnForType(
                solids,
                planPoint,
                roadY,
                tunnelThreshold,
                bridgeThreshold,
                fillMaterialId,
                worldX,
                worldZ,
                terrain,
                constructionType));
        }
        return total;
    }

    /** Removes trees, foliage, plants, snow and fluids without treating them as earthwork. */
    public static void clearRoadDecorations(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        if (solids == null || center == null || leftNormal == null || widthBlocks <= 0
                || terrain == null || columnResolver == null) return;
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12 ? leftNormal.normalize() : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            int groundY = terrain.sampleSurfaceY(point);
            int topY = terrain.sampleColumnTopY(point);
            for (int y = groundY + 1; y <= topY; y++) {
                if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                    solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air");
                }
            }
        }
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
        int cleared = 0;
        int topY = groundY;
        if (mode == RoadTerrainClearanceUtils.OverheadMode.TUNNEL) {
            topY = roadY + tunnelThreshold;
        } else if (mode == RoadTerrainClearanceUtils.OverheadMode.NONE && groundY > roadY) {
            topY = groundY;
        }
        for (int y = roadY + 1; y <= topY; y++) {
            solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air");
            cleared++;
        }
        return new GradingVolumes(cleared, 0);
    }

    private static GradingVolumes cutOpenColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int groundY) {
        int cleared = 0;
        for (int y = roadY + 1; y <= groundY; y++) {
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
