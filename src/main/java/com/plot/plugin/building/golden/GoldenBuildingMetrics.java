package com.plot.plugin.building.golden;

import java.util.List;

/**
 * 单次建筑生成的可比对统计量（Golden Test 断言载体）。
 */
public record GoldenBuildingMetrics(
        int totalBlocks,
        int wallBlocks,
        int floorBlocks,
        int roofBlocks,
        int foundationBlocks,
        int openingBlocks,
        int otherBlocks,
        int cutVolume,
        int fillVolume,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        String effectiveRoofType,
        List<String> warnings) {

    public GoldenBuildingMetrics {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public static GoldenBuildingMetrics empty() {
        return new GoldenBuildingMetrics(
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            "FLAT",
            List.of());
    }
}
