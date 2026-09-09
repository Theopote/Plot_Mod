package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadSlopeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形标高与方块采样（与 Minecraft World 解耦，便于测试与未来非 MC 后端复用）。
 */
public interface TerrainSampler {

    int DEFAULT_SEA_LEVEL = EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;

    /**
     * 平面坐标处的地表高度（工程坐标系）。
     */
    int sampleSurfaceY(Vec2d planPoint);

    /**
     * 世界方块坐标处是否为实心方块（非空气）。
     * <p>
     * 道路/土方语义：仅 {@link com.plot.core.terrain.EngineeringTerrainService#isSolidEngineeringBlock} 等工程自然地体。
     */
    boolean isSolidBlock(int worldX, int y, int worldZ);

    /**
     * 导线净空检测：该位置是否为阻挡导线的实心体（含树木、人工构筑等，不含空气与流体）。
     */
    default boolean isWireObstruction(int worldX, int y, int worldZ) {
        return isSolidBlock(worldX, y, worldZ);
    }

    /** Highest block that may need inspection/clearing; defaults to the engineering surface. */
    default int sampleColumnTopY(Vec2d planPoint) {
        return sampleSurfaceY(planPoint);
    }

    /**
     * Whether a block is natural, non-load-bearing decoration that road construction may remove.
     * Implementations without block-type information remain conservative.
     */
    default boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
        return false;
    }

    /**
     * 沿道路横断面采样地表高度并取平均（覆盖 [-halfWidth, +halfWidth]）。
     */
    default int sampleCrossSectionGroundY(Vec2d center, Vec2d tangent, double halfWidth) {
        if (center == null) {
            return DEFAULT_SEA_LEVEL;
        }
        if (halfWidth <= 0) {
            return sampleSurfaceY(center);
        }

        Vec2d normal = RoadGeometryUtils.leftNormal(tangent);
        List<Integer> heights = new ArrayList<>();
        for (int offset : RoadGeometryUtils.crossSectionSampleOffsets(halfWidth)) {
            heights.add(sampleSurfaceY(center.add(normal.multiply(offset))));
        }
        return RoadSlopeUtils.averageGroundHeight(heights);
    }
}
