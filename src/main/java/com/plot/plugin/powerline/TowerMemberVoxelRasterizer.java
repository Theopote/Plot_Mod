package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/** 塔体构件体素光栅化（预览与建造共用厚度扩展规则）。 */
public final class TowerMemberVoxelRasterizer {
    private TowerMemberVoxelRasterizer() {
    }

    public static Set<BlockPos> rasterizeMember(
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            int thickness) {
        Set<BlockPos> blocks = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            startX, startY, startZ, endX, endY, endZ));
        if (thickness >= 2) {
            expandThickness(blocks, endX - startX, endY - startY, endZ - startZ);
        }
        return blocks;
    }

    private static void expandThickness(
            Set<BlockPos> blocks,
            double deltaX,
            double deltaY,
            double deltaZ) {
        int[] axes = crossSectionAxes(deltaX, deltaY, deltaZ);
        Set<BlockPos> expanded = new LinkedHashSet<>(blocks);
        for (BlockPos pos : blocks) {
            BlockPos first = offset(pos, axes[0]);
            BlockPos second = offset(pos, axes[1]);
            expanded.add(first);
            expanded.add(second);
            expanded.add(offset(first, axes[1]));
        }
        blocks.clear();
        blocks.addAll(expanded);
    }

    /** 选取与构件方向最垂直的两个体素轴。 */
    private static int[] crossSectionAxes(double deltaX, double deltaY, double deltaZ) {
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absX >= absY && absX >= absZ) {
            return new int[] {1, 2};
        }
        if (absY >= absZ) {
            return new int[] {0, 2};
        }
        return new int[] {0, 1};
    }

    private static BlockPos offset(BlockPos pos, int axis) {
        return switch (axis) {
            case 0 -> pos.up();
            case 1 -> pos.east();
            case 2 -> pos.south();
            default -> pos;
        };
    }
}
