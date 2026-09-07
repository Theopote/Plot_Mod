package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 导线路径的离散体素栅格化（6-连通体素步进）。
 */
public final class PowerLineWireRasterizer {

    private PowerLineWireRasterizer() {
    }

    /**
     * 按跨距长度计算曲线采样点数：区间数满足密度，点数为区间数 + 1。
     */
    public static int computeWireSampleCount(double spanLength, int samplesPerBlock) {
        int segmentCount = Math.max(1, (int) Math.ceil(spanLength * samplesPerBlock));
        return segmentCount + 1;
    }

    /**
     * 将连续世界坐标线段栅格化为 6-连通方块序列（含两端）。
     */
    public static List<BlockPos> rasterizeLine3D(
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1) {
        return VoxelLineRasterizer.rasterizeLine3D(x0, y0, z0, x1, y1, z1);
    }

    /**
     * 在相邻体素之间沿曼哈顿路径步进，保证每步只改变一个轴。
     */
    static List<BlockPos> rasterizeBlockLine3D(BlockPos from, BlockPos to) {
        return VoxelLineRasterizer.rasterizeBlockLine3D(from, to);
    }

    private static BlockPos blockCell(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
