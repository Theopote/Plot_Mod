package com.plot.plugin.road.golden;

import java.util.List;

/**
 * 单次道路场景生成的可比对统计量（Golden Test 断言载体）。
 */
public record RoadGoldenMetrics(
        int surfaceBlocks,
        int fillBlocks,
        int cutBlocks,
        int markingBlocks,
        int bridgeBlocks,
        int tunnelBlocks,
        int junctionBlocks,
        int cutVolume,
        int fillVolume,
        int placementRecords,
        int bridgeCount,
        int tunnelCount,
        List<String> warnings) {

    public RoadGoldenMetrics {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public static RoadGoldenMetrics empty() {
        return new RoadGoldenMetrics(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
