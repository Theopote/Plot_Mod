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
 * 期望作废只由设计裁决路径执行（接缝修整、合并净空胜出等），不得依实际方块反向 scrub。
 */
public final class FinalPlacementAcceptanceValidator {
    private FinalPlacementAcceptanceValidator() {
    }

    public record Report(
            int missingRoadSurfaceCount,
            int wrongElevationCount,
            int wrongMaterialOrLayerCount,
            int unclearedTunnelCavityCount,
            int missingTunnelLiningCount,
            int floatingTunnelLightCount) {
        public static final Report CLEAN = new Report(0, 0, 0, 0, 0, 0);

        /**
         * 建造门禁：路面严重项 + 洞腔/衬砌。
         * 悬空灯具仅观感告警，不阻断落地（见 {@link #floatingTunnelLightCount()}）。
         */
        public boolean hasBlockingIssues() {
            return missingRoadSurfaceCount > 0
                || wrongElevationCount > 0
                || wrongMaterialOrLayerCount > 0
                || unclearedTunnelCavityCount > 0
                || missingTunnelLiningCount > 0;
        }

        public boolean hasFloatingLightAdvisory() {
            return floatingTunnelLightCount > 0;
        }

        /** 错高程 + 错图层/错材质合计（兼容旧诊断口径）。 */
        public int wrongElevationOrMaterialCount() {
            return wrongElevationCount + wrongMaterialOrLayerCount;
        }

        /** 建造门禁用的路面严重项合计（缺铺 + 错高程 + 错图层/错材质）。 */
        public int blockingSurfaceDefectCount() {
            return missingRoadSurfaceCount + wrongElevationOrMaterialCount();
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
        int wrongElevation = 0;
        int wrongMaterialOrLayer = 0;
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isStructuralSurface() || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                if (hasStructuralSurfaceElsewhereInColumn(aggregate, safeIndex, pos)) {
                    wrongElevation++;
                } else {
                    missingSurface++;
                }
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            // 合法附属覆盖：标线可盖车行道；护栏仅允许占用人行道/路肩/非机动车道/中央分隔带边缘。
            if (isAuthorizedSurfaceOverlay(layer, expected.layer())) {
                continue;
            }
            if (layer != expected.layer()) {
                // 车行道不得 silently 顶替人行道/路肩；其它结构层也必须对上设计层。
                wrongMaterialOrLayer++;
                continue;
            }
            if (expected.materialId() != null
                    && !expected.materialId().equals(actual.newBlockId)) {
                wrongMaterialOrLayer++;
            }
        }
        // 隧道计数只读专项校验已写入的聚合字段，不在此重算。
        return new Report(
            missingSurface,
            wrongElevation,
            wrongMaterialOrLayer,
            Math.max(0, aggregate.unclearedTunnelCavityCount),
            Math.max(0, aggregate.missingTunnelLiningCount),
            Math.max(0, aggregate.floatingTunnelLightCount));
    }

    /**
     * 仅覆盖路面终态计数；隧道指标保持 {@link TunnelFinalVoxelValidator} 已写入的值。
     * 不 scrub 设计期望——授权作废由接缝/合并路径完成。
     */
    public static Report applyToAggregate(RoadGenerationResult aggregate) {
        return applyToAggregate(aggregate, FinalPlacementIndex.build(aggregate));
    }

    public static Report applyToAggregate(RoadGenerationResult aggregate, FinalPlacementIndex index) {
        if (aggregate == null) {
            return Report.CLEAN;
        }
        FinalPlacementIndex safeIndex = index != null ? index : FinalPlacementIndex.build(aggregate);
        Report report = validate(aggregate, safeIndex);
        // 兼容门禁字段：合计路面严重项；分类以 Report 为准。
        aggregate.missingRoadSurfaceCount = report.blockingSurfaceDefectCount();
        return report;
    }

    /**
     * 与 {@link Report#hasBlockingIssues()} 同口径：路面严重项 + 隧道洞腔/衬砌。
     * 悬空灯具不进建造门禁。
     */
    public static boolean hasBlockingIssues(RoadGenerationResult aggregate) {
        if (aggregate == null) {
            return false;
        }
        return aggregate.missingRoadSurfaceCount > 0
            || aggregate.unclearedTunnelCavityCount > 0
            || aggregate.missingTunnelLiningCount > 0;
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
            if (isAuthorizedSurfaceOverlay(layer, expected.layer())) {
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

    /**
     * 标线可覆盖结构路面期望位；护栏仅允许覆盖路侧边缘层（人行道/路肩/非机动车道/中央分隔带），
     * 不得无条件豁免车行道内部护栏。
     */
    static boolean isAuthorizedSurfaceOverlay(RoadSolidLayer actualLayer, RoadSolidLayer expectedLayer) {
        if (actualLayer == RoadSolidLayer.MARKING) {
            return expectedLayer == RoadSolidLayer.ROAD
                || expectedLayer == RoadSolidLayer.SIDEWALK
                || expectedLayer == RoadSolidLayer.SHOULDER
                || expectedLayer == RoadSolidLayer.BIKE_LANE
                || expectedLayer == RoadSolidLayer.MEDIAN
                || expectedLayer == RoadSolidLayer.BRIDGE;
        }
        if (actualLayer != RoadSolidLayer.GUARDRAIL) {
            return false;
        }
        return expectedLayer == RoadSolidLayer.SIDEWALK
            || expectedLayer == RoadSolidLayer.SHOULDER
            || expectedLayer == RoadSolidLayer.BIKE_LANE
            || expectedLayer == RoadSolidLayer.MEDIAN;
    }
}
