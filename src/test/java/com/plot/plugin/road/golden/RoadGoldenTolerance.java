package com.plot.plugin.road.golden;

/**
 * Golden Test 数值容差（绝对值）。
 */
public record RoadGoldenTolerance(
        int blockCounts,
        int volumes,
        int categoryBlocks) {

    public static RoadGoldenTolerance standard() {
        return new RoadGoldenTolerance(0, 0, 0);
    }
}
