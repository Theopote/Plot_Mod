package com.plot.core.command;

import net.minecraft.util.math.BlockPos;

/**
 * 方块放置记录（供道路/建筑等生成命令共用）
 */
public class BlockRecord {
    public final BlockPos pos;
    public final String previousBlockId;
    public final String newBlockId;

    public BlockRecord(BlockPos pos, String previousBlockId, String newBlockId) {
        this.pos = pos;
        this.previousBlockId = previousBlockId;
        this.newBlockId = newBlockId;
    }
}
