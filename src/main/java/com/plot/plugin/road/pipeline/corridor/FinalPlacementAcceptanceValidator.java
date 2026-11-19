package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

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
            // 合法标线覆盖：保留，不要求重铺底层材质。
            if (layer == RoadSolidLayer.MARKING) {
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
        Report report = validate(aggregate, index);
        aggregate.missingRoadSurfaceCount = report.blockingSurfaceDefectCount();
        return report;
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
            || layer == RoadSolidLayer.MEDIAN;
    }
}
