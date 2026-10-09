package com.plot.plugin.road.golden;

import java.util.List;
import java.util.Map;

/**
 * Regression Golden 的 Snapshot expected（A 类）。
 * <p>
 * 刷新：{@link RoadGoldenSnapshotGeneratorTest}。
 */
public final class RoadGoldenExpectations {
    private RoadGoldenExpectations() {
    }

    public static final RoadGoldenMetrics R01 = new RoadGoldenMetrics(
        155, 0, 0, 0, 0, 0, 0, 0, 0, 155, 0, 0, List.of());

    public static final RoadGoldenMetrics R02 = new RoadGoldenMetrics(
        203, 0, 0, 0, 0, 0, 0, 0, 0, 201, 0, 0, List.of());

    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        462, 0, 0, 36, 0, 0, 147, 0, 0, 349, 0, 0, List.of());

    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        583, 0, 0, 52, 0, 0, 163, 0, 0, 429, 0, 0, List.of());

    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        729, 0, 0, 59, 0, 0, 200, 0, 0, 508, 0, 0, List.of());

    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        219, 80, 0, 0, 0, 0, 0, 0, 80, 214, 0, 0, List.of());

    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        219, 45, 130, 0, 0, 45, 0, 130, 45, 254, 0, 0, List.of());

    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        420, 0, 4635, 0, 113, 1815, 0, 4635, 0, 2306, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        362, 0, 5618, 0, 0, 2305, 0, 5618, 0, 2609, 0, 2, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        758, 0, 0, 0, 380, 0, 0, 0, 0, 1123, 1, 0, List.of());

    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        632, 115, 0, 0, 16, 0, 111, 0, 115, 574, 1, 0, List.of());

    public static final RoadGoldenMetrics R12 = new RoadGoldenMetrics(
        370, 0, 0, 0, 0, 0, 0, 0, 0, 334, 0, 0, List.of());

    private static final Map<String, RoadGoldenMetrics> BY_ID = Map.ofEntries(
        Map.entry("R01", R01),
        Map.entry("R02", R02),
        Map.entry("R03", R03),
        Map.entry("R04", R04),
        Map.entry("R05", R05),
        Map.entry("R06", R06),
        Map.entry("R07", R07),
        Map.entry("R08", R08),
        Map.entry("R09", R09),
        Map.entry("R10", R10),
        Map.entry("R11", R11),
        Map.entry("R12", R12));

    public static RoadGoldenMetrics forCase(String caseId) {
        RoadGoldenMetrics metrics = BY_ID.get(caseId);
        if (metrics == null) {
            throw new IllegalArgumentException("Unknown golden case: " + caseId);
        }
        return metrics;
    }
}
