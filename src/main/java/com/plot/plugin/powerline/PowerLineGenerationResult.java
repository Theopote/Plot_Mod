package com.plot.plugin.powerline;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力线路生成结果。
 */
public class PowerLineGenerationResult {
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();
    public int poleCount;
    public double wireLength;
    public int structureBlockCount;
    public int braceBlockCount;
    public int armBlockCount;
    public final PowerLineFootprint footprint;

    public PowerLineGenerationResult(PowerLineFootprint footprint) {
        this.footprint = footprint;
    }

    public int blockCount() {
        return placementRecords.size();
    }
}
