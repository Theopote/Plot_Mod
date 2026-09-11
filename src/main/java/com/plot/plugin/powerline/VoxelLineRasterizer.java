package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
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
        boolean reverse = compareLex(from, to) > 0;
        BlockPos start = reverse ? to : from;
        BlockPos end = reverse ? from : to;
        List<BlockPos> points = traceSixConnectedLine(start, end);
        if (reverse) {
            Collections.reverse(points);
        }
        return points;
    }

    /**
     * 三维 Bresenham（6-连通）：按各轴距离比例交替推进，每步只跨一个体素面。
     */
    private static List<BlockPos> traceSixConnectedLine(BlockPos from, BlockPos to) {
        int x = from.getX();
        int y = from.getY();
        int z = from.getZ();
        int dx = to.getX() - x;
        int dy = to.getY() - y;
        int dz = to.getZ() - z;
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        int sx = Integer.signum(dx);
        int sy = Integer.signum(dy);
        int sz = Integer.signum(dz);

        int steps = ax + ay + az;
        List<BlockPos> points = new ArrayList<>(steps + 1);
        points.add(new BlockPos(x, y, z));

        int errX = steps / 2;
        int errY = steps / 2;
        int errZ = steps / 2;
        for (int i = 0; i < steps; i++) {
            errX -= ax;
            errY -= ay;
            errZ -= az;
            if (errX <= errY && errX <= errZ) {
                x += sx;
                errX += steps;
            } else if (errY <= errZ) {
                y += sy;
                errY += steps;
            } else {
                z += sz;
                errZ += steps;
            }
            points.add(new BlockPos(x, y, z));
        }
        return points;
    }

    private static int compareLex(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static int floorCoord(double value) {
        return (int) Math.floor(value);
    }

    private static BlockPos blockCell(double x, double y, double z) {
        return new BlockPos(floorCoord(x), floorCoord(y), floorCoord(z));
    }
}
