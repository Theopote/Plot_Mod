package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;

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
        return MemberVoxelRaster.rasterize(
            startX, startY, startZ, endX, endY, endZ, thickness).allBlocks();
    }

    public static MemberVoxelRaster rasterizeMemberDetailed(
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            int thickness) {
        return MemberVoxelRaster.rasterize(
            startX, startY, startZ, endX, endY, endZ, thickness);
    }
}
