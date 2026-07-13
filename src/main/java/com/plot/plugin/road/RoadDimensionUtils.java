package com.plot.plugin.road;

/**
 * 道路尺寸与方块网格换算：UI 中的米制宽度按 1m = 1 方块边长四舍五入。
 */
public final class RoadDimensionUtils {
    private RoadDimensionUtils() {
    }

    public static int metersToBlocks(double meters) {
        return Math.max(1, (int) Math.round(meters));
    }

    public static int metersToBlocksNonNegative(double meters) {
        return Math.max(0, (int) Math.round(meters));
    }

    /** 中心线左侧（含）到右侧（含）的横向方块偏移下界。 */
    public static int minLateralOffset(int blockWidth) {
        if (blockWidth <= 0) {
            return 0;
        }
        return -((blockWidth - 1) / 2);
    }

    /** 中心线左侧（含）到右侧（含）的横向方块偏移上界。 */
    public static int maxLateralOffset(int blockWidth) {
        if (blockWidth <= 0) {
            return -1;
        }
        return blockWidth - ((blockWidth - 1) / 2) - 1;
    }

    /** 自中心线到最外侧方块中心的横向距离，用于地形采样与路口包络。 */
    public static double halfExtentFromCenter(int blockWidth) {
        if (blockWidth <= 0) {
            return 0.0;
        }
        return maxLateralOffset(blockWidth) + 0.5;
    }
}
