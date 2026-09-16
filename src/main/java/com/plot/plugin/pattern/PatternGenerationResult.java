package com.plot.plugin.pattern;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 铺装图案生成结果。
 */
public class PatternGenerationResult {
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();

    public boolean hasPlacements() {
        return !placementRecords.isEmpty();
    }

    public int getBlockCount() {
        return placementRecords.size();
    }
}
