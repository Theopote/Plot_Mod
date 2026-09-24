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
        56, 33, 4, 16, 0, 0, 3, 0, 0,
        0, 3, 64, 67, 0, 3,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B02 = new GoldenBuildingMetrics(
        292, 156, 64, 60, 0, 0, 12, 0, 0,
        0, 9, 64, 70, 0, 5,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B03 = new GoldenBuildingMetrics(
        193, 72, 24, 91, 0, 0, 6, 0, 0,
        5, 14, 64, 68, 5, 14,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B04 = new GoldenBuildingMetrics(
        400, 200, 56, 128, 0, 0, 16, 0, 0,
        0, 9, 64, 71, 0, 9,
        "HIP",
        List.of());

    public static final GoldenBuildingMetrics B05 = new GoldenBuildingMetrics(
        360, 282, 2, 52, 0, 0, 24, 0, 0,
        0, 9, 64, 70, 0, 9,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B06 = new GoldenBuildingMetrics(
        716, 280, 154, 256, 0, 0, 26, 0, 0,
        0, 11, 64, 71, 0, 11,
        "GABLE",
        List.of());

    /**
     * B07 Snapshot：仅锁定数量回归。正确性（wallBlocks&gt;0 等）见
     * {@link BuildingSemanticAcceptanceTest#b07RequiresSemanticProtectionBeyondSnapshot()}——
     * 禁止只靠本字段证明降级行为正确。
     */
    public static final GoldenBuildingMetrics B07 = new GoldenBuildingMetrics(
        96, 66, 0, 24, 0, 0, 6, 0, 0,
        0, 11, 64, 67, 0, 1,
        "FLAT",
        List.of("plugin.building.warn.inner_offset_failed", "plugin.building.warn.roof_downgrade"));

    public static final GoldenBuildingMetrics B08 = new GoldenBuildingMetrics(
        752, 154, 72, 64, 448, 0, 14, 0, 512,
        0, 7, 65, 78, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B09 = new GoldenBuildingMetrics(
        664, 385, 180, 64, 0, 0, 35, 0, 0,
        0, 7, 64, 79, 0, 7,
        "FLAT",
        List.of());

    /**
     * 厚墙（3）：墙体/开洞均落在 footprint 内；负 bounds 曾是开洞内法向反向的 Bug，已修复。
     * 见 {@link GoldenBuildingTest#b10ThickWallInvariants()}。
     */
    public static final GoldenBuildingMetrics B10 = new GoldenBuildingMetrics(
        540, 358, 16, 80, 0, 0, 86, 0, 0,
        0, 9, 64, 70, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B11 = new GoldenBuildingMetrics(
        368, 167, 96, 80, 0, 4, 21, 0, 0,
        0, 9, 64, 70, 0, 7,
        "FLAT",
        List.of());

    public static final GoldenBuildingMetrics B12 = new GoldenBuildingMetrics(
        236, 78, 32, 120, 0, 0, 6, 0, 0,
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
