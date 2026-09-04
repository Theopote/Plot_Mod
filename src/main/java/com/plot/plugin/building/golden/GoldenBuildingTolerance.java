package com.plot.plugin.building.golden;

/**
 * Golden Test 数值容差（绝对值）。
 */
public record GoldenBuildingTolerance(
        int totalBlocks,
        int categoryBlocks,
        int volume,
        int bounds) {

    public static GoldenBuildingTolerance strict() {
        return new GoldenBuildingTolerance(0, 0, 0, 0);
    }

    public static GoldenBuildingTolerance standard() {
        return new GoldenBuildingTolerance(0, 0, 0, 0);
    }
}
