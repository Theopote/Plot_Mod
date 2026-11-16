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

    /**
     * 桥墩足迹的绝对横向偏移（相对道路中心线整数格），与路面条带同一套 min/max 语义，
     * 保证预览与世界生成不会出现半格错位。
     */
    public static int[] pierFootprintLaterals(int centerLateralOffset, int diameterBlocks) {
        int diameter = Math.max(1, diameterBlocks);
        int min = minLateralOffset(diameter);
        int max = maxLateralOffset(diameter);
        if (max < min) {
            return new int[0];
        }
        int[] laterals = new int[max - min + 1];
        for (int i = 0; i < laterals.length; i++) {
            laterals[i] = centerLateralOffset + min + i;
        }
        return laterals;
    }
}
