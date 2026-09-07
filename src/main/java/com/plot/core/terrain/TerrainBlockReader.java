package com.plot.core.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 最小方块读取接口，供 {@link NaturalTreeClassifier} 与测试桩共用。
 */
public interface TerrainBlockReader {
    BlockState getBlockState(BlockPos pos);

    default boolean isChunkLoaded(int worldX, int worldZ) {
        return true;
    }
}
