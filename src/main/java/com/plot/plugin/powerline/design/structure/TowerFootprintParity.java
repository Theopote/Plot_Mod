package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.VoxelLineRasterizer;

/**
 * 塔体截面在方块网格上的奇偶对称分析。
 * <p>
 * 居中点状装饰（避雷针、信标等）需要截面在中心轴上有唯一一格；
 * 进格后的半宽 {@code N = |symmetricBlock(half)|} 为偶数时，外轮廓虽仍过原点，
 * 但与偶数厚度/封底组合时容易出现「中心两格」——故默认将 {@code N} 规范为奇数。
 */
public final class TowerFootprintParity {
    private static final double OFFSET_EPSILON = 1e-3;

    private TowerFootprintParity() {
    }

    /** 对称进格后的半宽（格），即角点落在 {@code ±N}。 */
    public static int blockHalfExtent(double halfDimension) {
        return Math.abs(VoxelLineRasterizer.symmetricBlock(halfDimension));
    }

    /** 沿单轴从 {@code -N} 到 {@code +N} 的截面列数（恒为奇数）。 */
    public static int symmetricSpanBlocks(double halfDimension) {
        int extent = blockHalfExtent(halfDimension);
        return 2 * extent + 1;
    }

    public static boolean isOddBlockHalfExtent(double halfDimension) {
        return (blockHalfExtent(halfDimension) & 1) == 1;
    }

    public static boolean isCentered(TowerDecoration decoration) {
        if (decoration == null) {
            return false;
        }
        return Math.abs(decoration.getLateralOffset()) < OFFSET_EPSILON
            && Math.abs(decoration.getLongitudinalOffset()) < OFFSET_EPSILON;
    }

    public static boolean requiresUniqueCenterColumn(TowerDecorationKind kind) {
        return kind == TowerDecorationKind.BEACON
            || kind == TowerDecorationKind.ANTENNA
            || kind == TowerDecorationKind.WARNING_LIGHT;
    }

    public static boolean requiresUniqueCenterColumn(TowerDecoration decoration) {
        return decoration != null
            && decoration.isEnabled()
            && isCentered(decoration)
            && requiresUniqueCenterColumn(decoration.getKind());
    }

    public static boolean structureNeedsCenterColumn(TowerStructureDesign structure) {
        if (structure == null) {
            return false;
        }
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (requiresUniqueCenterColumn(decoration)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将半宽进格为奇数块半径；若当前为偶数半径则最小增量抬升（{@code n + 0.5} → 进格为 {@code n + 1}）。
     */
    public static double snapHalfDimensionToOddBlockExtent(double halfDimension) {
        if (!Double.isFinite(halfDimension) || halfDimension <= 0.0) {
            return halfDimension;
        }
        if (isOddBlockHalfExtent(halfDimension)) {
            return halfDimension;
        }
        return blockHalfExtent(halfDimension) + 0.5;
    }

    public static double effectiveHalfWidth(TowerStation station) {
        if (station == null) {
            return 0.0;
        }
        return station.getHalfWidth();
    }

    public static double effectiveHalfDepth(TowerStation station) {
        if (station == null) {
            return 0.0;
        }
        return station.getHalfDepth();
    }

    public static TowerStation stationForDecorationHeight(TowerStructureDesign structure, double height) {
        if (structure == null) {
            return null;
        }
        TowerStation nearest = null;
        double bestDelta = Double.MAX_VALUE;
        for (TowerStation station : structure.sortedStations()) {
            double delta = Math.abs(station.getHeight() - height);
            if (delta < bestDelta) {
                bestDelta = delta;
                nearest = station;
            }
        }
        return nearest;
    }
}
