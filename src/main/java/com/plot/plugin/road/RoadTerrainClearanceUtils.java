package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

/**
 * 道路穿地形时的上方方块清除：按隧道阈值区分完全开挖与隧道腔体；
 * 洞穴空洞不打穿至柱顶。
 */
public final class RoadTerrainClearanceUtils {
    private RoadTerrainClearanceUtils() {
    }

    public enum OverheadMode {
        NONE,
        CUT,
        TUNNEL,
        /** 洞穴/不连续覆土：只清局部净空，不打穿山体。 */
        LOCAL
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
        if (terrain.isSolidBlock(worldX, roadY + tunnelThreshold, worldZ)) {
            return OverheadMode.TUNNEL;
        }
        if (!hasSolidAboveRoad(roadY, surfaceY, worldX, worldZ, terrain)) {
            // 高度图已报告路面以上存在地表，但逐格未检测到实心方块 → 局部净空即可
            return OverheadMode.LOCAL;
        }
        if (hasAirGapBetween(terrain, worldX, worldZ, roadY, surfaceY)) {
            return OverheadMode.LOCAL;
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
                        solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
                    }
                }
            }
            case TUNNEL -> {
                int top = roadY + tunnelThreshold;
                for (int y = roadY + 1; y <= top; y++) {
                    if (terrain.isSolidBlock(worldX, y, worldZ)) {
                        solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
                    }
                }
            }
            case LOCAL -> {
                // 与隧道设计净空同量级：清局部妨碍，保留洞顶上方山体
                int top = roadY + Math.max(1, tunnelThreshold);
                for (int y = roadY + 1; y <= top; y++) {
                    if (terrain.isSolidBlock(worldX, y, worldZ)) {
                        solids.add(planPoint, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
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

    static boolean hasAirGapBetween(
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            int roadY,
            int surfaceY) {
        for (int y = roadY + 1; y <= surfaceY; y++) {
            if (!terrain.isSolidBlock(worldX, y, worldZ)) {
                return true;
            }
        }
        return false;
    }

    public interface BlockColumnResolver {
        int worldX(Vec2d planPoint);

        int worldZ(Vec2d planPoint);

        /**
         * 世界方块列中心 → 画布平面点。
         * 默认恒等（无投影 / unitsPerBlock=1）；有投影时应覆盖。
         */
        default Vec2d planPoint(int worldX, int worldZ) {
            return new Vec2d(worldX + 0.5, worldZ + 0.5);
        }
    }
}
