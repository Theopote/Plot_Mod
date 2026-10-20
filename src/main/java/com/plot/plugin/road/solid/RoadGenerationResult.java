package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.profile.ConstructionRunChartMarker;
import com.plot.plugin.road.profile.WaterCrossingChartMarker;
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
    /** 与 {@link #placementRecords} 同步：记录各 BlockPos 当前生效的图层优先级。 */
    public final Map<BlockPos, RoadSolidLayer> placementLayers = new LinkedHashMap<>();
    /** 与 {@link #placementRecords} 同步：记录各 BlockPos 当前生效的同层材质优先级。 */
    public final Map<BlockPos, Integer> placementMaterialPriorities = new LinkedHashMap<>();
    /** Sparse reserved headroom above open-road surfaces; no air records for already-empty terrain. */
    public final Map<BlockPos, Integer> openRoadClearanceTops = new LinkedHashMap<>();
    public int cutVolume;
    public int fillVolume;
    public int bridgeCount;
    public int tunnelCount;
    public int streetlightCount;
    /** 因实体图元硬顶上限而丢弃的方块数（>0 表示结果不完整） */
    public int droppedSolidCount;
    public double pathLength;
    public final List<RoadConstructionType> constructionTypes = new ArrayList<>();
    public double bridgeLength;
    public double tunnelLength;
    public double normalRoadLength;

    public String edgeId;
    public List<Double> profileDistances = new ArrayList<>();
    public List<Integer> profileGroundHeights = new ArrayList<>();
    public List<Integer> profileGuideLine = new ArrayList<>();
    public List<Double> profileDesignElevations = new ArrayList<>();
    public List<Integer> profileBuildHeights = new ArrayList<>();
    public BuildHeightProfile buildProfile = BuildHeightProfile.inactive();
    public List<BuildHeightSample> profileBuildSamples = new ArrayList<>();
    public List<Integer> profileWaterHeights = new ArrayList<>();
    public List<WaterCrossingChartMarker> profileWaterCrossingMarkers = new ArrayList<>();
    public List<ConstructionRunChartMarker> profileConstructionRuns = new ArrayList<>();
    /** False when a pinned start/end elevation cannot be reached within max slope. */
    public boolean manualEndpointConstraintFeasible = true;
    /** False when a fixed flat or manual elevation cannot clear water minimums. */
    public boolean waterConstraintFeasible = true;
    /** {@link com.plot.plugin.road.pipeline.corridor.RoadGenerationIntegrityValidator} 指标。 */
    public int missingRoadSurfaceCount;
    public int unclearedObstructionCount;
    public int orphanSlopePillarCount;
    /** Batter horizontal run capped at corridor max; deep cut/fill may be incomplete. */
    public int slopeBatterWidthCapCount;
    /** Slope mask columns that do not connect smoothly to adjacent batter columns. */
    public int brokenSlopeSurfaceCount;
    /** Batter profile stopped before meeting natural ground. */
    public int unresolvedSlopeConstructionCount;
    /** Width-capped batter that did not reach terrain; partial slope may still be placed. */
    public int needsRetainingWallCount;
    /** ROAD columns missing expected adjacent shoulder coverage. */
    public int roadShoulderSeamGapCount;
    /** Outward shoulder trace found a missing slope column while cut/fill still required. */
    public int shoulderTraceGapCount;
    /** Corridor slope cells under bridge/tunnel interior (must stay zero). */
    public int structureInteriorSlopeCellCount;

    public RoadGenerationResult(double pathLength) {
        this.pathLength = pathLength;
    }

    public void recordPlacementIfAbsent(BlockPos pos, String previousBlockId, String newBlockId) {
        RoadPlacementRecorder.recordIfAbsent(placementRecords, pos, previousBlockId, newBlockId);
    }

    public void recordPlacementOverride(BlockPos pos, String previousBlockId, String newBlockId) {
        RoadPlacementRecorder.recordOverride(placementRecords, pos, previousBlockId, newBlockId);
    }

    public void recordPlacementByLayerPriority(
            BlockPos pos,
            String previousBlockId,
            String newBlockId,
            RoadSolidLayer layer,
            int materialPriority) {
        RoadPlacementRecorder.recordByLayerPriority(
            placementRecords,
            placementLayers,
            placementMaterialPriorities,
            pos,
            previousBlockId,
            newBlockId,
            layer,
            materialPriority);
    }

    public void mergeFrom(RoadGenerationResult source) {
        if (source == null) {
            return;
        }
        source.openRoadClearanceTops.forEach((pos, top) -> openRoadClearanceTops.merge(pos, top, Math::max));
        roadBlocks.addAll(source.roadBlocks);
        sidewalkBlocks.addAll(source.sidewalkBlocks);
        bridgeBlocks.addAll(source.bridgeBlocks);
        tunnelBlocks.addAll(source.tunnelBlocks);
        streetlightBlocks.addAll(source.streetlightBlocks);
        // Keep each winning record, layer and material priority consistent so junction
        // rasterization can correctly resolve remaining structural/clearance conflicts.
        for (Map.Entry<BlockPos, BlockRecord> entry : source.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockRecord incoming = entry.getValue();
            BlockRecord existing = placementRecords.get(pos);
            boolean incomingAir = incoming != null && "minecraft:air".equals(incoming.newBlockId);
            boolean existingSurface = isProtectedSurface(placementLayers.get(pos));
            if (incomingAir && existing != null && "minecraft:air".equals(existing.newBlockId)) {
                recordPlacementByLayerPriority(pos, incoming.previousBlockId, incoming.newBlockId,
                    source.placementLayers.get(pos), source.placementMaterialPriorities.getOrDefault(
                        pos, RoadSolidMaterialPriority.DEFAULT));
                continue;
            }
            // Excavation, subgrade and tunnel lining from another edge must not
            // replace designed pavement. Copy the winning record and metadata together.
            boolean replaceWithSurface = existing != null
                    && ("minecraft:air".equals(existing.newBlockId)
                        || !existingSurface)
                    && incoming != null && !incomingAir
                    && isProtectedSurface(source.placementLayers.get(pos));
            // The union of approach clearance volumes opens tunnel junctions;
            // first-claim would retain another approach's wall across the lane.
            boolean replaceWithClearance = existing != null && incomingAir && !existingSurface;
            if (replaceWithSurface || replaceWithClearance) {
                recordPlacementOverride(pos, incoming.previousBlockId, incoming.newBlockId);
                placementLayers.put(pos, source.placementLayers.get(pos));
                placementMaterialPriorities.put(pos, source.placementMaterialPriorities.getOrDefault(
                    pos, RoadSolidMaterialPriority.DEFAULT));
            } else {
                placementRecords.putIfAbsent(pos, incoming);
            }
        }
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : source.placementLayers.entrySet()) {
            placementLayers.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<BlockPos, Integer> entry : source.placementMaterialPriorities.entrySet()) {
            placementMaterialPriorities.putIfAbsent(entry.getKey(), entry.getValue());
        }
        cutVolume += source.cutVolume;
        fillVolume += source.fillVolume;
        bridgeCount += source.bridgeCount;
        tunnelCount += source.tunnelCount;
        streetlightCount += source.streetlightCount;
        droppedSolidCount += source.droppedSolidCount;
        missingRoadSurfaceCount += source.missingRoadSurfaceCount;
        unclearedObstructionCount += source.unclearedObstructionCount;
        orphanSlopePillarCount += source.orphanSlopePillarCount;
        slopeBatterWidthCapCount += source.slopeBatterWidthCapCount;
        brokenSlopeSurfaceCount += source.brokenSlopeSurfaceCount;
        unresolvedSlopeConstructionCount += source.unresolvedSlopeConstructionCount;
        needsRetainingWallCount += source.needsRetainingWallCount;
        roadShoulderSeamGapCount += source.roadShoulderSeamGapCount;
        shoulderTraceGapCount += source.shoulderTraceGapCount;
        structureInteriorSlopeCellCount += source.structureInteriorSlopeCellCount;
        pathLength += source.pathLength;
        constructionTypes.addAll(source.constructionTypes);
        bridgeLength += source.bridgeLength;
        tunnelLength += source.tunnelLength;
        normalRoadLength += source.normalRoadLength;
    }

    private static boolean isProtectedSurface(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN || layer == RoadSolidLayer.MARKING
            || layer == RoadSolidLayer.BRIDGE;
    }

    public void copyProfileFrom(RoadGenerationResult source) {
        if (source == null) {
            return;
        }
        edgeId = source.edgeId;
        profileDistances = new ArrayList<>(source.profileDistances);
        profileGroundHeights = new ArrayList<>(source.profileGroundHeights);
        profileGuideLine = new ArrayList<>(source.profileGuideLine);
        profileDesignElevations = new ArrayList<>(source.profileDesignElevations);
        profileBuildHeights = new ArrayList<>(source.profileBuildHeights);
        buildProfile = source.buildProfile;
        profileBuildSamples = new ArrayList<>(source.profileBuildSamples);
        profileWaterHeights = new ArrayList<>(source.profileWaterHeights);
        profileWaterCrossingMarkers = new ArrayList<>(source.profileWaterCrossingMarkers);
        profileConstructionRuns = new ArrayList<>(source.profileConstructionRuns);
        manualEndpointConstraintFeasible = source.manualEndpointConstraintFeasible;
        waterConstraintFeasible = source.waterConstraintFeasible;
    }

    public boolean hasProfileData() {
        if (profileDistances.isEmpty()
                || profileDistances.size() != profileGroundHeights.size()
                || profileDistances.size() != profileGuideLine.size()) {
            return false;
        }
        int sampleCount = profileDistances.size();
        return profileBuildHeights.size() == sampleCount
            && profileDesignElevations.size() == sampleCount;
    }
}
