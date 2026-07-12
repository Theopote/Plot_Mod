package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路生成落地结果：按层分类的 BlockPos 与放置记录（与几何生成解耦）。
 */
public class RoadGenerationResult {
    public final List<BlockPos> roadBlocks = new ArrayList<>();
    public final List<BlockPos> sidewalkBlocks = new ArrayList<>();
    public final List<BlockPos> bridgeBlocks = new ArrayList<>();
    public final List<BlockPos> tunnelBlocks = new ArrayList<>();
    public final List<BlockPos> streetlightBlocks = new ArrayList<>();
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
    public int cutVolume;
    public int fillVolume;
    public int bridgeCount;
    public int tunnelCount;
    public int streetlightCount;
    public double pathLength;

    public RoadGenerationResult(double pathLength) {
        this.pathLength = pathLength;
    }

    public void recordPlacementIfAbsent(BlockPos pos, String previousBlockId, String newBlockId) {
        RoadPlacementRecorder.recordIfAbsent(placementRecords, pos, previousBlockId, newBlockId);
    }

    public void recordPlacementOverride(BlockPos pos, String previousBlockId, String newBlockId) {
        RoadPlacementRecorder.recordOverride(placementRecords, pos, previousBlockId, newBlockId);
    }

    public void mergeFrom(RoadGenerationResult source) {
        if (source == null) {
            return;
        }
        roadBlocks.addAll(source.roadBlocks);
        sidewalkBlocks.addAll(source.sidewalkBlocks);
        bridgeBlocks.addAll(source.bridgeBlocks);
        tunnelBlocks.addAll(source.tunnelBlocks);
        streetlightBlocks.addAll(source.streetlightBlocks);
        placementRecords.putAll(source.placementRecords);
        cutVolume += source.cutVolume;
        fillVolume += source.fillVolume;
        bridgeCount += source.bridgeCount;
        tunnelCount += source.tunnelCount;
        streetlightCount += source.streetlightCount;
        pathLength += source.pathLength;
    }
}
