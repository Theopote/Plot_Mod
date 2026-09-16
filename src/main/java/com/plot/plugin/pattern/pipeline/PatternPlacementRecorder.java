package com.plot.plugin.pattern.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.pattern.PatternGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * 将解析后的材质写入生成结果（捕获 previousBlockId 供撤销）。
 */
public final class PatternPlacementRecorder {
    private final IBlockProjectionService projection;
    private final PatternGenerationResult result;

    public PatternPlacementRecorder(IBlockProjectionService projection, PatternGenerationResult result) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.result = Objects.requireNonNull(result, "result");
    }

    public void record(BlockPos pos, String newBlockId) {
        if (pos == null || newBlockId == null) {
            return;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projection.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
