package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.ExpectedRoadSurface;
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
    /** 与 {@link #placementRecords} 同步：AIR 清障用途（挡墙仅可覆盖边坡开挖）。 */
    public final Map<BlockPos, ClearancePurpose> placementClearancePurposes = new LinkedHashMap<>();
    /** Sparse reserved headroom above open-road surfaces; no air records for already-empty terrain. */
    public final Map<BlockPos, Integer> openRoadClearanceTops = new LinkedHashMap<>();
    /**
     * 设计路面期望（走廊 surfaceMask / 路口铺装）。最终封口按此逐格校验，
     * 不得从 placementRecords 反推。
     */
    public final Map<BlockPos, ExpectedRoadSurface> expectedRoadSurfaces = new LinkedHashMap<>();
    /**
     * 计划隧道洞腔世界格（与最终 AIR 用途表脱钩；实体覆盖后仍保留）。
     */
    public final java.util.Set<BlockPos> expectedTunnelCavity = new java.util.LinkedHashSet<>();
    /**
     * 计划隧道衬砌世界格（与最终放置胜出结果脱钩）。
     */
    public final java.util.Set<BlockPos> expectedTunnelLining = new java.util.LinkedHashSet<>();
    /**
     * Aggregate-network stage timings in nanoseconds (0 when not recorded).
     * Used for large-network profiling; safe to ignore in normal gameplay paths.
     */
    public long assembleMergeEdgesNanos;
    public long assembleMergeJunctionsNanos;
    public long assembleClearIntrudingFillNanos;
    public long assembleRailPassNanos;
    public int cutVolume;
    public int fillVolume;
    public int bridgeCount;
    public int tunnelCount;
    /** Feasible tunnel portal stations found during structure generation. */
    public int portalFoundCount;
    /** Tunnel-run boundaries where portal search exhausted without cover. */
    public int portalNotFoundCount;
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
    /** 开放走廊路面下方无地形/填方支撑（桥隧列不计）。 */
    public int unsupportedPavementCount;
    /** 隧道洞腔计划为 AIR 但仍残留实体障碍。 */
    public int unclearedTunnelCavityCount;
    /** 隧道灯具缺少相邻衬砌安装面。 */
    public int floatingTunnelLightCount;
    /** 期望衬砌世界格缺失 STRUCTURAL/ACCENT 实体（严格逐格）。 */
    public int missingTunnelLiningCount;
    /** 落地铁轨格数（含普通/动力/探测轨）。 */
    public int railBlockCount;
    /** 无四邻同层/±1 邻轨的孤立铁轨格。 */
    public int railOrphanCount;
    /** 邻格存在但 shape 未双向连接的边数。 */
    public int railShapeDiscontinuityCount;
    /** 生成记录中已确认轨下为空气的铁轨格。 */
    public int railUnsupportedCount;
    /** 生成记录未覆盖轨下格（世界可能已有支撑）的铁轨数。 */
    public int railSupportUnknownCount;
    /**
     * 可选：生成阶段写入的期望贯通轴线（世界 BlockPos → {@link com.plot.plugin.road.pipeline.marking.RoadRailShapeResolver.AxisHint}）。
     */
    public final Map<BlockPos, com.plot.plugin.road.pipeline.marking.RoadRailShapeResolver.AxisHint> railAxisHints =
        new LinkedHashMap<>();

    public RoadGenerationResult(double pathLength) {
        this.pathLength = pathLength;
    }

    public void recordExpectedRoadSurface(ExpectedRoadSurface expected) {
        if (expected == null) {
            return;
        }
        expectedRoadSurfaces.put(expected.position(), expected);
    }

    public void recordExpectedTunnelCavity(BlockPos pos) {
        if (pos != null) {
            expectedTunnelCavity.add(pos.toImmutable());
        }
    }

    public void recordExpectedTunnelLining(BlockPos pos) {
        if (pos != null) {
            expectedTunnelLining.add(pos.toImmutable());
        }
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
        recordPlacementByLayerPriority(
            pos, previousBlockId, newBlockId, layer, materialPriority, null);
    }

    public void recordPlacementByLayerPriority(
            BlockPos pos,
            String previousBlockId,
            String newBlockId,
            RoadSolidLayer layer,
            int materialPriority,
            ClearancePurpose clearancePurpose) {
        RoadPlacementRecorder.recordByLayerPriority(
            placementRecords,
            placementLayers,
            placementMaterialPriorities,
            placementClearancePurposes,
            pos,
            previousBlockId,
            newBlockId,
            layer,
            materialPriority,
            clearancePurpose);
    }

    public void mergeFrom(RoadGenerationResult source) {
        if (source == null) {
            return;
        }
        source.openRoadClearanceTops.forEach((pos, top) -> openRoadClearanceTops.merge(pos, top, Math::max));
        // 后写入的同柱期望覆盖先前（路口权威高于走廊边缘）。
        expectedRoadSurfaces.putAll(source.expectedRoadSurfaces);
        expectedTunnelCavity.addAll(source.expectedTunnelCavity);
        expectedTunnelLining.addAll(source.expectedTunnelLining);
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
            ClearancePurpose incomingPurpose = source.placementClearancePurposes.get(pos);
            if (incomingAir && existing != null && "minecraft:air".equals(existing.newBlockId)) {
                // AIR↔AIR：方块与清障用途一并裁决，避免用途丢失或残留。
                recordPlacementByLayerPriority(
                    pos,
                    incoming.previousBlockId,
                    incoming.newBlockId,
                    source.placementLayers.get(pos),
                    source.placementMaterialPriorities.getOrDefault(
                        pos, RoadSolidMaterialPriority.DEFAULT),
                    incomingPurpose);
                continue;
            }
            RoadSolidLayer incomingLayer = source.placementLayers.get(pos);
            RoadSolidLayer existingLayer = placementLayers.get(pos);
            boolean existingRetaining = isRetainingWallSolid(existingLayer, existing);
            boolean incomingRetaining = isRetainingWallSolid(incomingLayer, incoming);
            // Excavation, subgrade and tunnel lining from another edge must not
            // replace designed pavement. Copy the winning record and metadata together.
            boolean replaceWithSurface = existing != null
                    && ("minecraft:air".equals(existing.newBlockId)
                        || !existingSurface)
                    && incoming != null && !incomingAir
                    && isProtectedSurface(incomingLayer);
            // 下层先写入 AIR 后，上层底板（SUBGRADE/BRIDGE）仍须能覆盖同柱空气，保证合并顺序无关。
            boolean replaceWithDeckSupport = existing != null
                    && "minecraft:air".equals(existing.newBlockId)
                    && incoming != null && !incomingAir
                    && (isElevatedDeckSupport(incomingLayer, pos, source)
                        || isElevatedDeckSupport(incomingLayer, pos, this));
            ClearancePurpose existingPurpose = placementClearancePurposes.get(pos);
            // 挡墙仅可覆盖边坡开挖 AIR；隧道/行车/立交净空保持空气。
            boolean replaceWithRetaining = existing != null
                    && "minecraft:air".equals(existing.newBlockId)
                    && incomingRetaining
                    && isSlopeExcavationAir(existingPurpose);
            // The union of approach clearance volumes opens tunnel junctions;
            // first-claim would retain another approach's wall across the lane.
            // Do not let underpass AIR erase elevated deck underside (SUBGRADE/BRIDGE under ROAD).
            // 边坡开挖 AIR 不得抹掉挡墙；真正的行车/隧道净空 AIR 可以。
            boolean existingDeckSupport = existing != null
                    && !existingSurface
                    && isElevatedDeckSupport(existingLayer, pos, this);
            boolean existingAuthorizedLining = existing != null
                    && expectedTunnelLining != null
                    && expectedTunnelLining.contains(pos)
                    && !"minecraft:air".equals(existing.newBlockId);
            // 合法壳层顶衬/侧墙：禁止被边坡开挖空气抹掉。
            // 覆土保护主路径是清障 cap（Phase 1）；洞腔/立交/行车净空仍可打开误入行驶包络的衬砌。
            boolean authorizedLiningBlocksClearance = existingAuthorizedLining
                    && incomingPurpose == ClearancePurpose.SLOPE_EXCAVATION;
            boolean replaceWithClearance = existing != null && incomingAir && !existingSurface
                    && !existingDeckSupport
                    && !authorizedLiningBlocksClearance
                    && (!existingRetaining || !isSlopeExcavationAir(incomingPurpose));
            if (replaceWithSurface || replaceWithDeckSupport || replaceWithRetaining || replaceWithClearance) {
                if (incoming != null) {
                    recordPlacementOverride(pos, incoming.previousBlockId, incoming.newBlockId);
                }
                placementLayers.put(pos, incomingLayer);
                placementMaterialPriorities.put(pos, source.placementMaterialPriorities.getOrDefault(
                    pos, RoadSolidMaterialPriority.DEFAULT));
                // 用途只描述最终获胜的 AIR；实体方块覆盖后必须清除。
                syncClearancePurposeAfterMerge(pos, incomingAir, existingPurpose, incomingPurpose);
            } else if (!placementRecords.containsKey(pos)) {
                placementRecords.put(pos, incoming);
                if (incomingAir && incomingPurpose != null) {
                    placementClearancePurposes.put(pos, incomingPurpose);
                }
            }
        }
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : source.placementLayers.entrySet()) {
            placementLayers.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<BlockPos, Integer> entry : source.placementMaterialPriorities.entrySet()) {
            placementMaterialPriorities.putIfAbsent(entry.getKey(), entry.getValue());
        }
        // 失败的清障请求不得污染已获胜的实体方块；用途表只保留最终 AIR。
        scrubStaleClearancePurposes();
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
        unsupportedPavementCount += source.unsupportedPavementCount;
        unclearedTunnelCavityCount += source.unclearedTunnelCavityCount;
        floatingTunnelLightCount += source.floatingTunnelLightCount;
        missingTunnelLiningCount += source.missingTunnelLiningCount;
        railBlockCount += source.railBlockCount;
        railOrphanCount += source.railOrphanCount;
        railShapeDiscontinuityCount += source.railShapeDiscontinuityCount;
        railUnsupportedCount += source.railUnsupportedCount;
        railSupportUnknownCount += source.railSupportUnknownCount;
        railAxisHints.putAll(source.railAxisHints);
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

    /** 截断挡墙实体（非空气）：跨边合并时保护，不被普通清障 AIR 覆盖。 */
    private static boolean isRetainingWallSolid(RoadSolidLayer layer, BlockRecord record) {
        return layer == RoadSolidLayer.RETAINING_WALL
            && record != null
            && record.newBlockId != null
            && !"minecraft:air".equals(record.newBlockId);
    }

    /** 未标注用途的 AIR 按边坡开挖处理，保持同边生成兼容；显式净空用途则拒绝挡墙覆盖。 */
    private static boolean isSlopeExcavationAir(ClearancePurpose purpose) {
        return purpose == null || purpose.allowsRetainingWallOverride();
    }

    /**
     * 合并裁决后同步清障用途：获胜 AIR 写入/强化用途，实体方块清除用途。
     */
    private void syncClearancePurposeAfterMerge(
            BlockPos pos,
            boolean incomingAir,
            ClearancePurpose existingPurpose,
            ClearancePurpose incomingPurpose) {
        if (!incomingAir) {
            placementClearancePurposes.remove(pos);
            return;
        }
        ClearancePurpose merged = ClearancePurpose.preferProtected(existingPurpose, incomingPurpose);
        if (merged != null) {
            placementClearancePurposes.put(pos, merged);
        } else {
            placementClearancePurposes.remove(pos);
        }
    }

    /** 用途表不变量：仅最终仍为 AIR 的方块可保留 ClearancePurpose。 */
    private void scrubStaleClearancePurposes() {
        placementClearancePurposes.entrySet().removeIf(entry -> {
            BlockRecord record = placementRecords.get(entry.getKey());
            return record == null || !"minecraft:air".equals(record.newBlockId);
        });
    }

    /**
     * BRIDGE shell immediately under pavement in the same column.
     * 不含 SUBGRADE：立交净空里残留的泥土/草方路基不得当作上层底板保护。
     * Looks up pavement in {@code owner} so incoming deck support can replace prior AIR
     * even when this aggregate does not yet hold the elevated ROAD.
     */
    private static boolean isElevatedDeckSupport(
            RoadSolidLayer layer,
            BlockPos pos,
            RoadGenerationResult owner) {
        if (pos == null || owner == null || layer != RoadSolidLayer.BRIDGE) {
            return false;
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = pos.up(dy);
            if (!isProtectedSurface(owner.placementLayers.get(above))) {
                continue;
            }
            BlockRecord aboveRecord = owner.placementRecords.get(above);
            if (aboveRecord != null && !"minecraft:air".equals(aboveRecord.newBlockId)) {
                return true;
            }
        }
        return false;
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
