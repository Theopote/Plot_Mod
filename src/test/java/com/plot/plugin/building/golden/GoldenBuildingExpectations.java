package com.plot.plugin.building.golden;

import java.util.List;
import java.util.Map;

/**
 * Regression Golden 的 Snapshot expected（A 类）。
 * <p>
 * 仅锁定「行为有没有变化」；正确性见 {@link SemanticAcceptanceAssertions}。
 * 刷新：{@link GoldenBuildingSnapshotGeneratorTest}。
 */
public final class GoldenBuildingExpectations {
    private GoldenBuildingExpectations() {
    }

    public static final GoldenBuildingMetrics B01 = new GoldenBuildingMetrics(
        44, 36, 4, 0, 0, 4, 0, 0, 0,
        0, 3, 64, 67, 0, 3,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B02 = new GoldenBuildingMetrics(
        264, 188, 64, 0, 0, 12, 0, 0, 0,
        0, 9, 64, 70, 0, 5,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B03 = new GoldenBuildingMetrics(
        140, 112, 24, 0, 0, 4, 0, 0, 0,
        5, 13, 64, 68, 5, 14,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B04 = new GoldenBuildingMetrics(
        301, 229, 56, 0, 0, 16, 0, 0, 0,
        0, 9, 64, 71, 0, 9,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B05 = new GoldenBuildingMetrics(
        309, 285, 2, 0, 0, 22, 0, 0, 0,
        0, 9, 64, 70, 0, 9,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B06 = new GoldenBuildingMetrics(
        585, 403, 154, 0, 0, 28, 0, 0, 0,
        0, 11, 64, 71, 0, 11,
        "GABLE",
        List.of());

    /**
     * B07 Snapshot：仅锁定数量回归。正确性（wallBlocks&gt;0 等）见
     * {@link BuildingSemanticAcceptanceTest#b07RequiresSemanticProtectionBeyondSnapshot()}——
     * 禁止只靠本字段证明降级行为正确。
     */
    public static final GoldenBuildingMetrics B07 = new GoldenBuildingMetrics(
        72, 66, 0, 0, 0, 6, 0, 0, 0,
        0, 11, 64, 66, 0, 1,
        "FLAT",
        List.of("plugin.building.warn.inner_offset_failed", "plugin.building.warn.roof_downgrade"));

    public static final GoldenBuildingMetrics B08 = new GoldenBuildingMetrics(
        724, 188, 72, 0, 448, 16, 0, 0, 512,
        0, 7, 65, 78, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B09 = new GoldenBuildingMetrics(
        636, 416, 180, 0, 0, 40, 0, 0, 0,
        0, 7, 64, 79, 0, 7,
        "FLAT",
        List.of());

    /**
     * 厚墙（3）：墙体/开洞均落在 footprint 内；负 bounds 曾是开洞内法向反向的 Bug，已修复。
     * 见 {@link GoldenBuildingTest#b10ThickWallInvariants()}。
     */
    public static final GoldenBuildingMetrics B10 = new GoldenBuildingMetrics(
        456, 396, 16, 0, 0, 44, 0, 0, 0,
        0, 9, 64, 70, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B11 = new GoldenBuildingMetrics(
        336, 216, 96, 0, 0, 24, 0, 0, 0,
        0, 9, 64, 70, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B12 = new GoldenBuildingMetrics(
        168, 130, 32, 0, 0, 6, 0, 0, 0,
        0, 9, 64, 68, 0, 5,
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
