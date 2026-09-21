package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.VoxelLineRasterizer;

/**
 * 塔体截面在关于 0 对称的方块网格上的占用分析。
 * <p>
 * 局部坐标 {@code -N … 0 … +N} 的中心轴恒为 {@code 0} 一格，与 {@code N} 奇偶无关；
 * 居中装饰应通过 {@code local(0, y, 0)} + {@link VoxelLineRasterizer#symmetricBlockCell} 落格，
 * 而不是修改塔体 halfWidth / halfDepth。
 */
public final class TowerFootprintParity {
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
}
