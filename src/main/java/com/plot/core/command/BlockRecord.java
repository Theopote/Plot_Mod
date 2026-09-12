package com.plot.core.command;

import com.plot.core.block.BlockSpec;
import net.minecraft.util.math.BlockPos;

/**
 * 方块放置记录（供道路/建筑等生成命令共用）
 */
public class BlockRecord {
    public final BlockPos pos;
    public final String previousBlockId;
    /** 完整 /setblock 参数（可含 BlockState，如 {@code minecraft:chain[axis=y]}）。 */
    public final String newBlockId;

    public BlockRecord(BlockPos pos, String previousBlockId, String newBlockId) {
        this.pos = pos;
        this.previousBlockId = previousBlockId;
        this.newBlockId = newBlockId;
    }

    public BlockRecord(BlockPos pos, String previousBlockId, BlockSpec newBlock) {
        this(pos, previousBlockId, newBlock != null ? newBlock.toSetBlockArgument() : "minecraft:air");
    }

    public BlockSpec newBlockSpec() {
        return BlockSpec.parse(newBlockId);
    }
}
