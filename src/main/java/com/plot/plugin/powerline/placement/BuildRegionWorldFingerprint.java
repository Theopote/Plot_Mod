package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 预览目标区域内的世界方块快照指纹（用于检测 Build 前环境是否已变化）。 */
public final class BuildRegionWorldFingerprint {
    private BuildRegionWorldFingerprint() {
    }

    public static int capture(
            PowerLineGenerationResult result,
            IBlockProjectionService projection) {
        if (result == null || result.placementRecords.isEmpty() || projection == null) {
            return 0;
        }
        List<BlockPos> positions = new ArrayList<>(result.placementRecords.keySet());
        positions.sort(Comparator.comparingLong(BlockPos::asLong));
        int hash = 1;
        for (BlockPos pos : positions) {
            hash = 31 * hash + Long.hashCode(pos.asLong());
            hash = 31 * hash + Objects.hashCode(projection.getBlockIdAt(pos));
        }
        return hash;
    }

    public static boolean matches(
            PowerLineGenerationResult result,
            int expectedFingerprint,
            IBlockProjectionService projection) {
        if (expectedFingerprint == 0) {
            return true;
        }
        return expectedFingerprint == capture(result, projection);
    }
}
