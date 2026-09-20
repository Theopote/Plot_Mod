package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 塔体成员光栅化结果：中心线路径 + 厚度扩展锚点。 */
public final class MemberVoxelRaster {
    private final List<BlockPos> centerline;
    private final Map<BlockPos, BlockPos> thicknessAnchors;

    public MemberVoxelRaster(List<BlockPos> centerline, Map<BlockPos, BlockPos> thicknessAnchors) {
        this.centerline = centerline == null ? List.of() : List.copyOf(centerline);
        this.thicknessAnchors = thicknessAnchors == null ? Map.of() : Map.copyOf(thicknessAnchors);
    }

    public List<BlockPos> centerline() {
        return centerline;
    }

    public Map<BlockPos, BlockPos> thicknessAnchors() {
        return thicknessAnchors;
    }

    public Set<BlockPos> allBlocks() {
        Set<BlockPos> blocks = new LinkedHashSet<>(centerline);
        blocks.addAll(thicknessAnchors.keySet());
        return blocks;
    }

    public static MemberVoxelRaster empty() {
        return new MemberVoxelRaster(List.of(), Map.of());
    }

    public static MemberVoxelRaster rasterize(
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            int thickness) {
        List<BlockPos> centerline = VoxelLineRasterizer.rasterizeLine3D(
            startX, startY, startZ, endX, endY, endZ);
        if (centerline.isEmpty()) {
            return empty();
        }
        if (thickness < 2) {
            return new MemberVoxelRaster(centerline, Map.of());
        }
        Map<BlockPos, BlockPos> thicknessAnchors = expandThicknessAnchors(
            centerline,
            endX - startX,
            endY - startY,
            endZ - startZ);
        return new MemberVoxelRaster(centerline, thicknessAnchors);
    }

    private static Map<BlockPos, BlockPos> expandThicknessAnchors(
            List<BlockPos> centerline,
            double deltaX,
            double deltaY,
            double deltaZ) {
        int[] axes = crossSectionAxes(deltaX, deltaY, deltaZ);
        Map<BlockPos, BlockPos> anchors = new LinkedHashMap<>();
        for (BlockPos core : centerline) {
            for (int sign0 : new int[] {-1, 0, 1}) {
                for (int sign1 : new int[] {-1, 0, 1}) {
                    if (sign0 == 0 && sign1 == 0) {
                        continue;
                    }
                    BlockPos expanded = core;
                    if (sign0 != 0) {
                        expanded = offsetSigned(expanded, axes[0], sign0);
                    }
                    if (sign1 != 0) {
                        expanded = offsetSigned(expanded, axes[1], sign1);
                    }
                    if (!expanded.equals(core)) {
                        anchors.putIfAbsent(expanded, core);
                    }
                }
            }
        }
        return anchors;
    }

    private static int[] crossSectionAxes(double deltaX, double deltaY, double deltaZ) {
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absY >= absX && absY >= absZ) {
            return new int[] {1, 2};
        }
        if (absX >= absZ) {
            return new int[] {0, 2};
        }
        return new int[] {0, 1};
    }

    private static BlockPos offsetSigned(BlockPos pos, int axis, int sign) {
        return switch (axis) {
            case 0 -> sign >= 0 ? pos.up() : pos.down();
            case 1 -> sign >= 0 ? pos.east() : pos.west();
            case 2 -> sign >= 0 ? pos.south() : pos.north();
            default -> pos;
        };
    }
}
