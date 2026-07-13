package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;

/**
 * 道路穿地形时的上方方块清除：按隧道阈值区分完全开挖与隧道腔体。
 */
public final class RoadTerrainClearanceUtils {
    private RoadTerrainClearanceUtils() {
    }

    public enum OverheadMode {
        NONE,
        CUT,
        TUNNEL
    }

    public static OverheadMode classify(
            int roadY,
            int surfaceY,
            int worldX,
            int worldZ,
            int tunnelThreshold,
            TerrainSampler terrain) {
        if (terrain == null || surfaceY <= roadY || tunnelThreshold <= 0) {
            return OverheadMode.NONE;
        }
        if (!hasSolidAboveRoad(roadY, surfaceY, worldX, worldZ, terrain)) {
            return OverheadMode.NONE;
        }
        if (terrain.isSolidBlock(worldX, roadY + tunnelThreshold, worldZ)) {
            return OverheadMode.TUNNEL;
        }
        return OverheadMode.CUT;
    }

    public static void applyClearance(
            RoadSolidModel solids,
            Vec2d planPoint,
            int roadY,
            int surfaceY,
            OverheadMode mode,
            int tunnelThreshold,
            int worldX,
            int worldZ,
            TerrainSampler terrain) {
        if (solids == null || planPoint == null || mode == null || mode == OverheadMode.NONE || terrain == null) {
            return;
        }
        switch (mode) {
            case CUT -> {
                for (int y = roadY + 1; y <= surfaceY; y++) {
                    if (terrain.isSolidBlock(worldX, y, worldZ)) {
                        solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air");
                    }
                }
            }
            case TUNNEL -> {
                int top = roadY + tunnelThreshold;
                for (int y = roadY + 1; y <= top; y++) {
                    if (terrain.isSolidBlock(worldX, y, worldZ)) {
                        solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air");
                    }
                }
            }
            default -> {
            }
        }
    }

    public static void clearCrossSectionOverhead(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int roadY,
            int tunnelThreshold,
            TerrainSampler terrain,
            BlockColumnResolver columnResolver) {
        if (solids == null || center == null || leftNormal == null || widthBlocks <= 0
                || terrain == null || columnResolver == null) {
            return;
        }
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            Vec2d planPoint = center.add(normal.multiply(lateral));
            int worldX = columnResolver.worldX(planPoint);
            int worldZ = columnResolver.worldZ(planPoint);
            int surfaceY = terrain.sampleSurfaceY(planPoint);
            OverheadMode mode = classify(roadY, surfaceY, worldX, worldZ, tunnelThreshold, terrain);
            applyClearance(solids, planPoint, roadY, surfaceY, mode, tunnelThreshold, worldX, worldZ, terrain);
        }
    }

    private static boolean hasSolidAboveRoad(
            int roadY,
            int surfaceY,
            int worldX,
            int worldZ,
            TerrainSampler terrain) {
        for (int y = roadY + 1; y <= surfaceY; y++) {
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                return true;
            }
        }
        return false;
    }

    public interface BlockColumnResolver {
        int worldX(Vec2d planPoint);

        int worldZ(Vec2d planPoint);
    }
}
