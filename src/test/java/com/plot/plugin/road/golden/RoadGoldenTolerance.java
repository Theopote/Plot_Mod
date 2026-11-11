package com.plot.plugin.road.golden;

/**
 * Golden Test 数值容差（绝对值）。
 */
public record RoadGoldenTolerance(
        int blockCounts,
        int volumes,
        int categoryBlocks) {

    public static RoadGoldenTolerance standard() {
        // blockCounts / categoryBlocks：多岔圆角与边坡在共享顶点处计数可能有边界抖动
        // （五岔 R05 的 placementRecords 在不同跑次可见 505↔509）
        return new RoadGoldenTolerance(5, 0, 5);
    }
}
