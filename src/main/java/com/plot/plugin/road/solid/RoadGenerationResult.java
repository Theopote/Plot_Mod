package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadConstructionType;
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
    public final List<RoadConstructionType> constructionTypes = new ArrayList<>();
    public double bridgeLength;
    public double tunnelLength;
    public double normalRoadLength;

    public String edgeId;
    public List<Double> profileDistances = new ArrayList<>();
    public List<Integer> profileGroundHeights = new ArrayList<>();
    public List<Integer> profileGuideLine = new ArrayList<>();
    public List<Integer> profileTargetHeights = new ArrayList<>();

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
        constructionTypes.addAll(source.constructionTypes);
        bridgeLength += source.bridgeLength;
        tunnelLength += source.tunnelLength;
        normalRoadLength += source.normalRoadLength;
    }

    public void copyProfileFrom(RoadGenerationResult source) {
        if (source == null) {
            return;
        }
        edgeId = source.edgeId;
        profileDistances = new ArrayList<>(source.profileDistances);
        profileGroundHeights = new ArrayList<>(source.profileGroundHeights);
        profileGuideLine = new ArrayList<>(source.profileGuideLine);
        profileTargetHeights = new ArrayList<>(source.profileTargetHeights);
    }

    public boolean hasProfileData() {
        return !profileDistances.isEmpty()
            && profileDistances.size() == profileGroundHeights.size()
            && profileDistances.size() == profileGuideLine.size()
            && profileDistances.size() == profileTargetHeights.size();
    }
}
