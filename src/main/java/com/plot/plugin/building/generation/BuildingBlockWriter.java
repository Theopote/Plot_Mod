package com.plot.plugin.building.generation;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;

/**
 * 统一写入建筑放置记录：保留首次 previousBlockId，允许后续 stage 覆盖 newBlockId。
 */
public final class BuildingBlockWriter {
    private BuildingBlockWriter() {
    }

    /**
     * 写入放置记录。若该位置已有记录，仅覆盖 newBlockId，保留首次 previousBlockId。
     */
    public static void recordBlock(
            BuildingGenerationResult result,
            BlockPos pos,
            String newBlockId,
            IBlockProjectionService projectionHandler) {
        if (result == null || pos == null || newBlockId == null) {
            return;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projectionHandler != null
            ? projectionHandler.getBlockIdAt(pos)
            : "minecraft:air";
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }

    /**
     * 测试用：直接指定 previousBlockId，不访问世界。
     */
    public static void recordBlockWithPrevious(
            BuildingGenerationResult result,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        if (result == null || pos == null || newBlockId == null) {
            return;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
        } else {
            result.placementRecords.put(pos, new BlockRecord(pos, previousBlockId, newBlockId));
        }
    }
}
