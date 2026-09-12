package com.plot.plugin.powerline.placement;

import com.plot.core.block.BlockSpec;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.api.world.IBlockProjectionService;
import net.minecraft.util.math.BlockPos;

/** 将 {@link VoxelSink} 写入 {@link PowerLineGenerationResult} 放置记录。 */
public final class GenerationVoxelSink implements VoxelSink {
    private final PowerLineGenerationResult result;
    private final IBlockProjectionService projectionHandler;

    public GenerationVoxelSink(
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        this.result = result;
        this.projectionHandler = projectionHandler;
    }

    @Override
    public void put(int x, int y, int z, String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return;
        }
        putRecord(x, y, z, blockId);
    }

    @Override
    public void put(int x, int y, int z, BlockSpec block) {
        if (block == null) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, block));
            return;
        }
        String previous = projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, block));
    }

    private void putRecord(int x, int y, int z, String blockId) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, blockId));
            return;
        }
        String previous = projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, blockId));
    }
}
