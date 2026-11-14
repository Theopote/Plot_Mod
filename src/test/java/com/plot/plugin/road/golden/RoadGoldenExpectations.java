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

    // T 字：连续圆角平滑 + 开敞 stub 街角露天后，表面/路口固体下调。
    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        339, 0, 0, 36, 0, 0, 72, 0, 0, 305, 0, 0, List.of());

    // 十字：同上。
    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        459, 0, 0, 60, 0, 0, 109, 0, 0, 399, 0, 0, List.of());

    // 五岔：同上；placement ±5 见 RoadGoldenTolerance。
    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        597, 0, 0, 63, 0, 0, 179, 0, 0, 492, 0, 0, List.of());

    // Acute shared-vertex pavement fan (≥90°) + longitudinal step cladding.
    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        244, 84, 0, 0, 0, 0, 0, 0, 84, 244, 0, 0, List.of());

    // FIT_TERRAIN after acute corner gore stamps and clearance designY floor fix.
    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        209, 49, 35, 0, 0, 37, 0, 35, 49, 244, 0, 0, List.of());

    // Bridge/tunnel：禁地表及以上凭空衬砌与清障后，tunnelBlocks/placement 各减 36。
    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        217, 0, 5286, 0, 108, 1216, 0, 5286, 0, 1541, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        406, 0, 3851, 0, 0, 2112, 0, 3851, 0, 2518, 0, 1, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        217, 0, 0, 0, 380, 0, 0, 0, 0, 597, 1, 0, List.of());

    // 立交：保留隧道顶衬砌/承重层后，下层净空不再把洞顶当可挖土体灌空气。
    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        420, 0, 0, 0, 252, 0, 0, 0, 0, 717, 1, 0, List.of());

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
