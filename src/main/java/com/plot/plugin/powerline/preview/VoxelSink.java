package com.plot.plugin.powerline.preview;

/** 抽象体素放置目标：世界生成与 UI 预览共用同一套杆塔分层逻辑。 */
public interface VoxelSink {
    void put(int x, int y, int z, String blockId);
}
