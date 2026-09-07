package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** 通用 3D 直线体素栅格化（6-连通）。 */
public final class VoxelLineRasterizer {

    private VoxelLineRasterizer() {
    }

    public static List<BlockPos> rasterizeLine3D(
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1) {
        return rasterizeBlockLine3D(
            blockCell(x0, y0, z0),
            blockCell(x1, y1, z1));
    }

    static List<BlockPos> rasterizeBlockLine3D(BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            return List.of(from);
        }

        List<BlockPos> points = new ArrayList<>();
        points.add(from);
        int x = from.getX();
        int y = from.getY();
        int z = from.getZ();
        int targetX = to.getX();
        int targetY = to.getY();
        int targetZ = to.getZ();

        while (x != targetX || y != targetY || z != targetZ) {
            if (x != targetX) {
                x += Integer.compare(targetX, x);
            } else if (y != targetY) {
                y += Integer.compare(targetY, y);
            } else {
                z += Integer.compare(targetZ, z);
            }
            points.add(new BlockPos(x, y, z));
        }
        return points;
    }

    private static BlockPos blockCell(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
