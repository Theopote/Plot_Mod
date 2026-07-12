package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadSlopeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形标高与方块采样（与 Minecraft World 解耦，便于测试与未来非 MC 后端复用）。
 */
public interface TerrainSampler {

    int DEFAULT_SEA_LEVEL = 64;

    /**
     * 平面坐标处的地表高度（工程坐标系）。
     */
    int sampleSurfaceY(Vec2d planPoint);

    /**
     * 世界方块坐标处是否为实心方块（非空气）。
     */
    boolean isSolidBlock(int worldX, int y, int worldZ);

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
