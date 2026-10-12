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
        203, 0, 0, 0, 0, 0, 0, 0, 0, 203, 0, 0, List.of());

    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        462, 0, 0, 36, 0, 0, 147, 0, 0, 349, 0, 0, List.of());

    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        583, 0, 0, 52, 0, 0, 163, 0, 0, 429, 0, 0, List.of());

    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        728, 0, 0, 58, 0, 0, 199, 0, 0, 508, 0, 0, List.of());

    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        209, 30, 0, 0, 0, 0, 0, 0, 30, 209, 0, 0, List.of());

    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        184, 15, 136, 0, 0, 136, 0, 136, 15, 320, 0, 0, List.of());

    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        396, 0, 6248, 0, 113, 2486, 0, 6248, 0, 2884, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        531, 0, 7809, 0, 0, 3494, 0, 7809, 0, 3801, 0, 2, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        28, 0, 0, 0, 380, 0, 0, 0, 0, 408, 1, 0, List.of());

    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        568, 35, 0, 0, 16, 0, 111, 0, 35, 515, 1, 0, List.of());

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
