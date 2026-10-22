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

    // Sub-block sweep fills two previously missed curved pavement cells.
    public static final RoadGoldenMetrics R02 = new RoadGoldenMetrics(
        215, 0, 0, 0, 0, 0, 0, 0, 0, 215, 0, 0, List.of());

    // Exact plaza ownership now retains the approach cells at junction seams.
    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        384, 0, 0, 36, 0, 0, 147, 0, 0, 349, 0, 0, List.of());

    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        479, 0, 0, 52, 0, 0, 163, 0, 0, 429, 0, 0, List.of());

    // Dense rotated approaches retain complete pavement at the five-way plaza seam.
    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        574, 0, 0, 58, 0, 0, 199, 0, 0, 514, 0, 0, List.of());

    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        239, 36, 0, 0, 0, 0, 0, 0, 36, 239, 0, 0, List.of());

    // Oscillation reduction / FIT_TERRAIN auto profile changes surface and earthwork counts.
    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        197, 18, 51, 0, 0, 37, 0, 51, 18, 234, 0, 0, List.of());

    // Portal roof sealing and exposed-batter excavation are covered by semantic regressions.
    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        371, 0, 7036, 0, 108, 2572, 0, 7036, 0, 3005, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        349, 0, 7600, 0, 0, 2683, 0, 7600, 0, 2989, 0, 2, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        265, 0, 0, 0, 380, 0, 0, 0, 0, 635, 1, 0, List.of());

    // Grade-separated plaza seam retention after profile authority / clearance resolution.
    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        549, 24, 0, 0, 13, 60, 171, 0, 24, 598, 1, 0, List.of());

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
