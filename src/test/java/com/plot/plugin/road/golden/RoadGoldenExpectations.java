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
        305, 0, 0, 0, 0, 0, 0, 0, 0, 155, 0, 0, List.of());

    public static final RoadGoldenMetrics R02 = new RoadGoldenMetrics(
        627, 0, 0, 0, 0, 0, 0, 0, 0, 199, 0, 0, List.of());

    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        779, 0, 0, 53, 0, 0, 164, 0, 0, 349, 0, 0, List.of());

    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        1019, 0, 0, 88, 0, 0, 199, 0, 0, 429, 0, 0, List.of());

    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        1296, 0, 0, 112, 0, 0, 253, 0, 0, 504, 0, 0, List.of());

    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        501, 160, 0, 0, 210, 0, 0, 0, 160, 413, 1, 0, List.of());

    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        432, 115, 105, 0, 0, 70, 0, 105, 115, 282, 0, 0, List.of());

    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        462, 0, 3829, 0, 176, 2605, 0, 3829, 0, 1784, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        325, 0, 6480, 0, 0, 4087, 0, 6480, 0, 2259, 0, 1, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        447, 0, 0, 0, 516, 0, 0, 0, 0, 604, 1, 0, List.of());

    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        1017, 55, 0, 0, 18, 0, 111, 0, 55, 536, 1, 0, List.of());

    public static final RoadGoldenMetrics R12 = new RoadGoldenMetrics(
        720, 0, 0, 0, 0, 0, 0, 0, 0, 334, 0, 0, List.of());

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
