package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

/**
 * 全网合并 / 接缝 / 封口之后的轻量终态验收：只对照已生成的设计期望与最终 placementRecords，
 * 不重跑走廊地形或边坡求解。用于覆盖边级 {@link RoadGenerationIntegrityValidator} 指标滞后。
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

        /** 建造门禁用的路面严重项合计（缺铺 + 错高程/错材质）。 */
        public int blockingSurfaceDefectCount() {
            return missingRoadSurfaceCount + wrongElevationOrMaterialCount;
        }
    }

    public static Report validate(RoadGenerationResult aggregate) {
        if (aggregate == null) {
            return Report.CLEAN;
        }
        int missingSurface = 0;
        int wrongOrMisplaced = 0;
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isCarriageway() || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                if (hasCarriagewayElsewhereInColumn(aggregate, pos)) {
                    wrongOrMisplaced++;
                } else {
                    missingSurface++;
                }
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.MARKING) {
                continue;
            }
            if (layer == RoadSolidLayer.ROAD
                    && expected.materialId() != null
                    && !expected.materialId().equals(actual.newBlockId)) {
                wrongOrMisplaced++;
            }
        }

        int unclearedCavity = 0;
        if (aggregate.expectedTunnelCavity != null) {
            for (BlockPos pos : aggregate.expectedTunnelCavity) {
                if (pos == null) {
                    continue;
                }
                BlockRecord record = aggregate.placementRecords.get(pos);
                if (record != null
                        && record.newBlockId != null
                        && !"minecraft:air".equals(record.newBlockId)) {
                    unclearedCavity++;
                }
            }
        }

        int missingLining = 0;
        if (aggregate.expectedTunnelLining != null) {
            for (BlockPos pos : aggregate.expectedTunnelLining) {
                if (pos == null) {
                    continue;
                }
                if (aggregate.expectedTunnelCavity != null
                        && aggregate.expectedTunnelCavity.contains(pos)) {
                    continue;
                }
                BlockRecord record = aggregate.placementRecords.get(pos);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    missingLining++;
                }
            }
        }

        return new Report(missingSurface, wrongOrMisplaced, unclearedCavity, missingLining);
    }

    /**
     * 用终态报告覆盖聚合结果上的严重计数（替换 mergeFrom 累加的边级中间值）。
     */
    public static Report applyToAggregate(RoadGenerationResult aggregate) {
        if (aggregate == null) {
            return Report.CLEAN;
        }
        Report report = validate(aggregate);
        aggregate.missingRoadSurfaceCount = report.blockingSurfaceDefectCount();
        aggregate.unclearedTunnelCavityCount = report.unclearedTunnelCavityCount();
        aggregate.missingTunnelLiningCount = report.missingTunnelLiningCount();
        return report;
    }

    public static boolean hasBlockingIssues(RoadGenerationResult aggregate) {
        if (aggregate == null) {
            return false;
        }
        return aggregate.missingRoadSurfaceCount > 0
            || aggregate.unclearedTunnelCavityCount > 0
            || aggregate.missingTunnelLiningCount > 0;
    }

    private static boolean hasCarriagewayElsewhereInColumn(RoadGenerationResult aggregate, BlockPos expected) {
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
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                return true;
            }
        }
        return false;
    }
}
