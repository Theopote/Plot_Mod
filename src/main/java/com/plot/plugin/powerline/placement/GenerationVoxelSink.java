package com.plot.plugin.powerline.placement;

import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.api.world.IBlockProjectionService;

/**
 * 将 {@link VoxelSink} 写入 {@link PowerLineGenerationResult} 放置记录。
 * <p>
 * 此处采集的 {@code previousBlockId} 仅用于预览期去重合并；真正落地与 Undo baseline
 * 由 {@link BuildPlacementPreparer} 在执行前重新读取世界状态。
 */
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
        PlacementWriter.put(
            result,
            projectionHandler,
            new net.minecraft.util.math.BlockPos(x, y, z),
            blockId,
            PlacementCategory.STRUCTURE);
    }

    @Override
    public void put(int x, int y, int z, BlockSpec block) {
        if (block == null) {
            return;
        }
        PlacementWriter.put(
            result,
            projectionHandler,
            new net.minecraft.util.math.BlockPos(x, y, z),
            block,
            PlacementCategory.STRUCTURE);
    }
}
