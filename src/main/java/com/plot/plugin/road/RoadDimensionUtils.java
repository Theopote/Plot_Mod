package com.plot.plugin.road;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 圆柱桥墩水平截面：直径范围内的近似圆盘。
     * 每项为 {@code [lateralLocal, alongRoadLocal]}，相对柱心；
     * 绝对横向 = {@code centerLateralOffset + lateralLocal}，沿路 = 桩号 + {@code alongRoadLocal}。
     */
    public static int[][] pierCylinderDiskOffsets(int diameterBlocks) {
        int diameter = Math.max(1, diameterBlocks);
        int localMin = minLateralOffset(diameter);
        int localMax = maxLateralOffset(diameter);
        if (localMax < localMin) {
            return new int[0][];
        }
        double mid = (localMin + localMax) * 0.5;
        double radiusSq = (diameter * 0.5) * (diameter * 0.5);
        List<int[]> cells = new ArrayList<>((localMax - localMin + 1) * (localMax - localMin + 1));
        for (int lx = localMin; lx <= localMax; lx++) {
            for (int lz = localMin; lz <= localMax; lz++) {
                double dx = lx - mid;
                double dz = lz - mid;
                if (dx * dx + dz * dz > radiusSq + 1e-6) {
                    continue;
                }
                cells.add(new int[] {lx, lz});
            }
        }
        return cells.toArray(new int[0][]);
    }

    /**
     * 双柱：{@code gapBlocks} 为两柱之间的空隙格数（与参数「双柱偏移」同义），
     * 空隙条带按 {@link #minLateralOffset}/{@link #maxLateralOffset} 居中，
     * 左右柱紧贴空隙外侧各占 {@code diameterBlocks} 格。
     *
     * @return {@code [leftCenter, rightCenter]}，可交给 {@link #pierFootprintLaterals}
     */
    public static int[] doublePierCenters(int gapBlocks, int diameterBlocks) {
        int gap = Math.max(1, gapBlocks);
        int diameter = Math.max(1, diameterBlocks);
        int gapMin = minLateralOffset(gap);
        int gapMax = maxLateralOffset(gap);
        int leftMax = gapMin - 1;
        int leftMin = leftMax - diameter + 1;
        int rightMin = gapMax + 1;
        int pierMin = minLateralOffset(diameter);
        return new int[] {leftMin - pierMin, rightMin - pierMin};
    }

    /** 双柱全部绝对横向格（左柱 + 右柱）。 */
    public static int[] doublePierFootprintLaterals(int gapBlocks, int diameterBlocks) {
        int[] centers = doublePierCenters(gapBlocks, diameterBlocks);
        int[] left = pierFootprintLaterals(centers[0], diameterBlocks);
        int[] right = pierFootprintLaterals(centers[1], diameterBlocks);
        int[] all = new int[left.length + right.length];
        System.arraycopy(left, 0, all, 0, left.length);
        System.arraycopy(right, 0, all, left.length, right.length);
        return all;
    }

    /** 双柱足迹是否完全落在行车道横向范围内。 */
    public static boolean doublePierFitsCarriageway(int carriagewayWidth, int gapBlocks, int diameterBlocks) {
        int roadMin = minLateralOffset(carriagewayWidth);
        int roadMax = maxLateralOffset(carriagewayWidth);
        for (int lateral : doublePierFootprintLaterals(gapBlocks, diameterBlocks)) {
            if (lateral < roadMin || lateral > roadMax) {
                return false;
            }
        }
        return true;
    }
}
