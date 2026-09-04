package com.plot.plugin.building.generation;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑生成结果（放置记录、土方量、警告与有效屋顶类型）。
 */
public class BuildingGenerationResult {
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
    public int cutVolume;
    public int fillVolume;
    public int blockCount;
    public final List<String> warnings = new ArrayList<>();
    public BuildingFootprint.RoofType effectiveRoofType = BuildingFootprint.RoofType.FLAT;
}
