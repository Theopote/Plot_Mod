package com.plot.plugin.road.golden;

/**
 * Golden Test 数值容差（绝对值）。
 */
public record RoadGoldenTolerance(
        int blockCounts,
        int volumes,
        int categoryBlocks) {

    public static RoadGoldenTolerance standard() {
        // blockCounts：R11 等边坡扇区在共享顶点处表面计数可能有 ±3 边界抖动
        return new RoadGoldenTolerance(3, 0, 0);
    }
}
