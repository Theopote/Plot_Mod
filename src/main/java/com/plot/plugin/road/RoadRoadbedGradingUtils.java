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

    /** Excavates and lines one tunnel cross-section while preserving the road surface itself. */
    public static GradingVolumes gradeTunnelCrossSection(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            int clearanceHeight,
            int sideClearance,
            int liningThickness,
            String liningMaterialId,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        if (solids == null || center == null || leftNormal == null || roadEnvelopeWidth <= 0
                || terrain == null || columnResolver == null
                || liningMaterialId == null || liningMaterialId.isBlank()) {
            return GradingVolumes.ZERO;
        }
        int clearance = Math.max(3, clearanceHeight);
        int side = Math.max(0, sideClearance);
        int lining = Math.max(1, liningThickness);
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int outerWidth = cavityWidth + lining * 2;
        int roadMin = RoadDimensionUtils.minLateralOffset(roadEnvelopeWidth);
        int roadMax = RoadDimensionUtils.maxLateralOffset(roadEnvelopeWidth);
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int cut = 0;
        int roofTop = roadY + clearance + lining;
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            boolean cavityColumn = lateral >= cavityMin && lateral <= cavityMax;
            boolean roadColumn = lateral >= roadMin && lateral <= roadMax;
            for (int y = roadY - 1; y <= roofTop; y++) {
                boolean cavityAir = cavityColumn && y >= roadY + 1 && y <= roadY + clearance;
                boolean cavityFloor = cavityColumn && !roadColumn && y == roadY;
                boolean structuralFloor = cavityColumn && y == roadY - 1;
                boolean wallOrRoof = !cavityColumn || y > roadY + clearance;
                if (terrain.isSolidBlock(worldX, y, worldZ)) cut++;
                if (cavityAir) {
                    solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air");
                } else if (cavityFloor || structuralFloor || wallOrRoof) {
                    solids.add(point, y, RoadSolidLayer.TUNNEL, liningMaterialId);
                }
            }
        }
        return new GradingVolumes(cut, 0);
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
