package com.plot.core.terrain;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;

/**
 * 地形标高与方块采样（与 Minecraft World 解耦，供道路 / 电力线路等插件共用）。
 * <p>
 * 中立核心接口：插件不得再互相引用对方包内的采样类型。
 */
public interface TerrainSampler {

    int DEFAULT_SEA_LEVEL = EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;

    /**
     * 平面坐标处的地表高度（工程坐标系）。
     */
    int sampleSurfaceY(Vec2d planPoint);

    /**
     * 地表暴露水体顶面 Y；无水上覆盖时为空（不含洞穴/地下水）。
     */
    default OptionalInt findExposedWaterSurface(Vec2d planPoint) {
        return OptionalInt.empty();
    }

    /**
     * 世界方块坐标处是否为实心方块（非空气）。
     * <p>
     * 道路/土方语义：仅 {@link EngineeringTerrainService#isSolidEngineeringBlock} 等工程自然地体。
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
     * Whether a block is natural, non-load-bearing decoration that construction may remove.
     * Implementations without block-type information remain conservative.
     */
    default boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
        return false;
    }

    /**
     * 沿横断面采样地表高度并取平均（覆盖 [-halfWidth, +halfWidth]）。
     */
    default int sampleCrossSectionGroundY(Vec2d center, Vec2d tangent, double halfWidth) {
        if (center == null) {
            return DEFAULT_SEA_LEVEL;
        }
        if (halfWidth <= 0) {
            return sampleSurfaceY(center);
        }

        Vec2d normal = leftNormal(tangent);
        List<Integer> heights = new ArrayList<>();
        for (int offset : crossSectionSampleOffsets(halfWidth)) {
            heights.add(sampleSurfaceY(center.add(normal.multiply(offset))));
        }
        return averageHeight(heights);
    }

    private static Vec2d leftNormal(Vec2d direction) {
        if (direction == null || direction.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = direction.normalize();
        return new Vec2d(-unit.y, unit.x);
    }

    private static List<Integer> crossSectionSampleOffsets(double halfWidth) {
        if (halfWidth <= 0) {
            return List.of(0);
        }
        LinkedHashSet<Integer> offsets = new LinkedHashSet<>();
        offsets.add(0);
        offsets.add((int) Math.round(-halfWidth));
        offsets.add((int) Math.round(halfWidth));
        return List.copyOf(offsets);
    }

    private static int averageHeight(List<Integer> heights) {
        if (heights == null || heights.isEmpty()) {
            return DEFAULT_SEA_LEVEL;
        }
        long sum = 0;
        for (int height : heights) {
            sum += height;
        }
        return (int) Math.round(sum / (double) heights.size());
    }
}
