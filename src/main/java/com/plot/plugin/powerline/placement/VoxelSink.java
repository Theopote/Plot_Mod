package com.plot.plugin.powerline.placement;

import com.plot.core.block.BlockSpec;

/** 抽象体素放置目标：世界生成与 UI 预览共用同一套杆塔分层逻辑。 */
public interface VoxelSink {
    void put(int x, int y, int z, String blockId);

    default void put(int x, int y, int z, BlockSpec block) {
        if (block == null) {
            return;
        }
        put(x, y, z, block.toSetBlockArgument());
    }
}
