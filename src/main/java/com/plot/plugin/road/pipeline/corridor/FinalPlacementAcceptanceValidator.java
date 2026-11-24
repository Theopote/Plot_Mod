package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 全网合并后的终态汇总：对照设计期望路面更新严重计数，并汇总已由专项校验写入的隧道指标。
 * <p>
 * 隧道洞腔/衬砌/灯具规则以 {@link TunnelFinalVoxelValidator} 为准，本类不得再实现简化版覆盖。
 */
public final class FinalPlacementAcceptanceValidator {
    private FinalPlacementAcceptanceValidator() {
    }

    public record Report(
            int missingRoadSurfaceCount,
            int wrongElevationOrMaterialCount,
            int unclearedTunnelCavityCount,
            int missingTunnelLiningCount) {
        public static final Report CLEAN = new Report(0, 0, 0, 0);

        public boolean hasBlockingIssues() {
            return missingRoadSurfaceCount > 0
                || wrongElevationOrMaterialCount > 0
                || unclearedTunnelCavityCount > 0
                || missingTunnelLiningCount > 0;
        }

        /** 建造门禁用的路面严重项合计（缺铺 + 错高程/错图层/错材质）。 */
        public int blockingSurfaceDefectCount() {
            return missingRoadSurfaceCount + wrongElevationOrMaterialCount;
        }
    }

    public static Report validate(RoadGenerationResult aggregate) {
        return validate(aggregate, FinalPlacementIndex.build(aggregate));
    }

    public static Report validate(RoadGenerationResult aggregate, FinalPlacementIndex index) {
        if (aggregate == null) {
            return Report.CLEAN;
        }
        FinalPlacementIndex safeIndex = index != null ? index : FinalPlacementIndex.build(aggregate);
        int missingSurface = 0;
        int wrongOrMisplaced = 0;
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isStructuralSurface() || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                if (hasStructuralSurfaceElsewhereInColumn(aggregate, safeIndex, pos)) {
                    wrongOrMisplaced++;
                } else {
                    missingSurface++;
                }
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            // 合法附属覆盖（标线/护栏）：占用人行道、非机动车道等期望位，不要求底层材质重铺。
            if (isAuthorizedSurfaceOverlay(layer)) {
                continue;
            }
            if (layer != expected.layer()) {
                // 车行道不得 silently 顶替人行道/路肩；其它结构层也必须对上设计层。
                wrongOrMisplaced++;
                continue;
            }
            if (expected.materialId() != null
                    && !expected.materialId().equals(actual.newBlockId)) {
                wrongOrMisplaced++;
            }
        }
        // 隧道计数只读专项校验已写入的聚合字段，不在此重算。
        return new Report(
            missingSurface,
            wrongOrMisplaced,
            Math.max(0, aggregate.unclearedTunnelCavityCount),
            Math.max(0, aggregate.missingTunnelLiningCount));
    }

    /**
     * 仅覆盖路面终态计数；隧道指标保持 {@link TunnelFinalVoxelValidator} 已写入的值。
     */
    public static Report applyToAggregate(RoadGenerationResult aggregate) {
        return applyToAggregate(aggregate, FinalPlacementIndex.build(aggregate));
    }

    public static Report applyToAggregate(RoadGenerationResult aggregate, FinalPlacementIndex index) {
        if (aggregate == null) {
            return Report.CLEAN;
        }
        FinalPlacementIndex safeIndex = index != null ? index : FinalPlacementIndex.build(aggregate);
        // 立交/路口交接后，旧 Y 的边期望常残留在已清空气格上；同柱已有路面则作废，避免假拦建造。
        scrubSupersededSurfaceExpectations(aggregate, safeIndex);
        Report report = validate(aggregate, safeIndex);
        aggregate.missingRoadSurfaceCount = report.blockingSurfaceDefectCount();
        return report;
    }

    /**
     * 作废已被交接取代的路面期望：期望格为空/空气，且同柱另有结构路面，或该格已是隧道净空空气。
     */
    static int scrubSupersededSurfaceExpectations(
            RoadGenerationResult aggregate,
            FinalPlacementIndex index) {
        if (aggregate == null || aggregate.expectedRoadSurfaces == null
                || aggregate.expectedRoadSurfaces.isEmpty()) {
            return 0;
        }
        FinalPlacementIndex safeIndex = index != null ? index : FinalPlacementIndex.build(aggregate);
        List<BlockPos> stale = new ArrayList<>();
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isStructuralSurface() || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            BlockRecord actual = aggregate.placementRecords.get(pos);
            boolean emptyOrAir = actual == null || "minecraft:air".equals(actual.newBlockId);
            if (!emptyOrAir) {
                continue;
            }
            if (isTunnelClearanceAir(aggregate, pos, actual)
                    || hasStructuralSurfaceElsewhereInColumn(aggregate, safeIndex, pos)) {
                stale.add(pos);
            }
        }
        for (BlockPos pos : stale) {
            aggregate.revokeSurfaceExpectation(pos);
        }
        return stale.size();
    }

    private static boolean isTunnelClearanceAir(
            RoadGenerationResult aggregate,
            BlockPos pos,
            BlockRecord actual) {
        if (actual == null || !"minecraft:air".equals(actual.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = aggregate.placementLayers.get(pos);
        return layer == RoadSolidLayer.TUNNEL
            || (aggregate.tunnelBlocks != null && aggregate.tunnelBlocks.contains(pos));
    }

    public static boolean hasBlockingIssues(RoadGenerationResult aggregate) {
        if (aggregate == null) {
            return false;
        }
        return aggregate.missingRoadSurfaceCount > 0
            || aggregate.unclearedTunnelCavityCount > 0
            || aggregate.missingTunnelLiningCount > 0
            || aggregate.floatingTunnelLightCount > 0;
    }

    private static boolean hasStructuralSurfaceElsewhereInColumn(
            RoadGenerationResult aggregate,
            FinalPlacementIndex index,
            BlockPos expected) {
        if (aggregate == null || expected == null) {
            return false;
        }
        ColumnKey column = new ColumnKey(expected.getX(), expected.getZ());
        if (index != null && index.placements() != null) {
            for (BlockPos pos : index.placements().positionsInColumn(column.worldX(), column.worldZ())) {
                if (pos == null || pos.getY() == expected.getY()) {
                    continue;
                }
                if (!isStructuralLayer(aggregate.placementLayers.get(pos))) {
                    continue;
                }
                BlockRecord record = index.placements().recordAt(pos);
                if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                    return true;
                }
            }
            return false;
        }
        if (aggregate.placementLayers == null) {
            return false;
        }
        for (var entry : aggregate.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos == null
                    || pos.getX() != expected.getX()
                    || pos.getZ() != expected.getZ()
                    || pos.getY() == expected.getY()) {
                continue;
            }
            if (!isStructuralLayer(entry.getValue())) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStructuralLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.BRIDGE;
    }

    /** 诊断：前几个路面缺陷样本（日志用）。 */
    public static List<String> sampleSurfaceDefects(RoadGenerationResult aggregate, int limit) {
        List<String> samples = new ArrayList<>();
        if (aggregate == null || limit <= 0) {
            return samples;
        }
        FinalPlacementIndex index = FinalPlacementIndex.build(aggregate);
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (samples.size() >= limit) {
                break;
            }
            if (expected == null || !expected.isStructuralSurface() || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                String kind = hasStructuralSurfaceElsewhereInColumn(aggregate, index, pos)
                    ? "wrongY"
                    : "missing";
                samples.add(kind + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " expect=" + expected.layer());
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (isAuthorizedSurfaceOverlay(layer)) {
                continue;
            }
            if (layer != expected.layer()) {
                samples.add("layer@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " expect=" + expected.layer() + " actual=" + layer);
            } else if (expected.materialId() != null
                    && !expected.materialId().equals(actual.newBlockId)) {
                samples.add("material@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " expect=" + expected.materialId() + " actual=" + actual.newBlockId);
            }
        }
        return samples;
    }

    /** 标线、护栏等附属设施覆盖结构路面期望位时，不计入层/材质冲突。 */
    private static boolean isAuthorizedSurfaceOverlay(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.MARKING || layer == RoadSolidLayer.GUARDRAIL;
    }
}
