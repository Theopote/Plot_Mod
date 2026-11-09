package com.plot.plugin.road.golden;

/**
 * Golden Test 数值容差（绝对值）。
 */
public record RoadGoldenTolerance(
        int blockCounts,
        int volumes,
        int categoryBlocks) {

    public static RoadGoldenTolerance standard() {
        // blockCounts / categoryBlocks：多岔圆角与边坡在共享顶点处计数可能有 ±3 边界抖动
        // （五岔 R05 的 junctionBlocks 在不同 JVM 上可见 175↔177）
        return new RoadGoldenTolerance(3, 0, 3);
    }
}
