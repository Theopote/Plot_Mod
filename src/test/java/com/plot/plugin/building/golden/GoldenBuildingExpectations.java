package com.plot.plugin.building.golden;

import java.util.List;
import java.util.Map;

/**
 * Golden Building 回归期望值（由 {@link GoldenBuildingSnapshotGeneratorTest} 生成）。
 */
public final class GoldenBuildingExpectations {
    private GoldenBuildingExpectations() {
    }

    public static final GoldenBuildingMetrics B01 = new GoldenBuildingMetrics(
        46, 38, 4, 0, 0, 4, 0, 0, 0,
        0, 4, 64, 67, 0, 4,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B02 = new GoldenBuildingMetrics(
        270, 194, 64, 0, 0, 12, 0, 0, 0,
        0, 10, 64, 70, 0, 6,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B03 = new GoldenBuildingMetrics(
        140, 112, 24, 0, 0, 4, 0, 0, 0,
        5, 13, 64, 68, 5, 14,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B04 = new GoldenBuildingMetrics(
        309, 237, 56, 0, 0, 16, 0, 0, 0,
        0, 10, 64, 71, 0, 10,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B05 = new GoldenBuildingMetrics(
        323, 299, 2, 0, 0, 22, 0, 0, 0,
        0, 10, 64, 70, 0, 9,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B06 = new GoldenBuildingMetrics(
        601, 419, 154, 0, 0, 28, 0, 0, 0,
        0, 12, 64, 71, 0, 12,
        "GABLE",
        List.of());

    /** inner offset 降级 + 坡顶 downgrade；wallBlocks 必须 &gt; 0，见 {@link GoldenBuildingTest#b07InnerOffsetDegradationInvariants()} */
    public static final GoldenBuildingMetrics B07 = new GoldenBuildingMetrics(
        75, 69, 0, 0, 0, 6, 0, 0, 0,
        0, 11, 64, 66, 0, 2,
        "FLAT",
        List.of("plugin.building.warn.inner_offset_failed", "plugin.building.warn.roof_downgrade"));

    public static final GoldenBuildingMetrics B08 = new GoldenBuildingMetrics(
        732, 196, 72, 0, 448, 16, 0, 0, 512,
        0, 8, 65, 78, 0, 8,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B09 = new GoldenBuildingMetrics(
        656, 436, 180, 0, 0, 40, 0, 0, 0,
        0, 8, 64, 79, 0, 8,
        "FLAT",
        List.of());

    /** 厚墙（3）；bounds 可超出原点，见 {@link GoldenBuildingTest#b10ThickWallInvariants()} */
    public static final GoldenBuildingMetrics B10 = new GoldenBuildingMetrics(
        496, 432, 16, 0, 0, 48, 0, 0, 0,
        -2, 12, 64, 70, -2, 10,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B11 = new GoldenBuildingMetrics(
        347, 227, 96, 0, 0, 24, 0, 0, 0,
        0, 10, 64, 70, 0, 8,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B12 = new GoldenBuildingMetrics(
        171, 133, 32, 0, 0, 6, 0, 0, 0,
        0, 10, 64, 68, 0, 6,
        "GABLE",
        List.of());

    private static final Map<String, GoldenBuildingMetrics> BY_ID = Map.ofEntries(
        Map.entry("B01", B01),
        Map.entry("B02", B02),
        Map.entry("B03", B03),
        Map.entry("B04", B04),
        Map.entry("B05", B05),
        Map.entry("B06", B06),
        Map.entry("B07", B07),
        Map.entry("B08", B08),
        Map.entry("B09", B09),
        Map.entry("B10", B10),
        Map.entry("B11", B11),
        Map.entry("B12", B12));

    public static GoldenBuildingMetrics forCase(String caseId) {
        GoldenBuildingMetrics expected = BY_ID.get(caseId);
        if (expected == null) {
            throw new IllegalArgumentException("Unknown golden case: " + caseId);
        }
        return expected;
    }
}
