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

    // T 字：街角后退预算随 R 增长后，核区∪补铺固体略增。
    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        334, 0, 0, 36, 0, 0, 64, 0, 0, 299, 0, 0, List.of());

    // 十字：局部中心线条带并集（直线退化为矩形 stub）+ 切线圆角。
    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        449, 0, 0, 60, 0, 0, 89, 0, 0, 389, 0, 0, List.of());

    // 五岔：锐角后退预算随 R 增长 + 倒圆外形补栅格，街角外扩铺装更完整。
    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        575, 0, 0, 63, 0, 0, 158, 0, 0, 480, 0, 0, List.of());

    // Acute shared-vertex pavement fan (≥90°) + longitudinal step cladding.
    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        244, 84, 0, 0, 0, 0, 0, 0, 84, 244, 0, 0, List.of());

    // FIT_TERRAIN after acute corner gore stamps and clearance designY floor fix.
    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        209, 49, 35, 0, 0, 37, 0, 35, 49, 244, 0, 0, List.of());

    // Bridge portal seal / tunnel voxel counts after roof stabilize + acute fans.
    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        217, 0, 5286, 0, 108, 1252, 0, 5286, 0, 1577, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        406, 0, 3851, 0, 0, 2148, 0, 3851, 0, 2554, 0, 1, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        217, 0, 0, 0, 380, 0, 0, 0, 0, 597, 1, 0, List.of());

    // 立交：下层不铺圆形广场；上跨边禁开敞切坡泥土贴底板边缘。±3 见 RoadGoldenTolerance。
    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        420, 0, 0, 0, 240, 150, 150, 0, 0, 741, 1, 0, List.of());

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
