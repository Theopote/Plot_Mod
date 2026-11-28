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

    // T 字：终态验收门禁后接缝对账/封口收敛，表面与路口固体下调。
    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        331, 0, 0, 36, 0, 0, 61, 0, 0, 299, 0, 0, List.of());

    // 十字：同上。
    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        437, 0, 0, 52, 0, 0, 77, 0, 0, 389, 0, 0, List.of());

    // 五岔：同上；placement ±5 见 RoadGoldenTolerance。
    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        530, 0, 0, 58, 0, 0, 112, 0, 0, 474, 0, 0, List.of());

    // Acute shared-vertex pavement fan (≥90°) + longitudinal step cladding.
    public static final RoadGoldenMetrics R06 = new RoadGoldenMetrics(
        244, 84, 0, 0, 0, 0, 0, 0, 84, 244, 0, 0, List.of());

    // FIT_TERRAIN after Phase 2.4.5.1 V04/V05 terrain-trend sign/blend fixes.
    public static final RoadGoldenMetrics R07 = new RoadGoldenMetrics(
        188, 28, 40, 0, 0, 42, 0, 40, 28, 228, 0, 0, List.of());

    // Bridge/tunnel：行车道宽改为车道驱动打包后，桥面/支撑块数随派生半宽收敛。
    public static final RoadGoldenMetrics R08 = new RoadGoldenMetrics(
        217, 0, 7470, 0, 92, 1604, 0, 7470, 0, 1913, 1, 2, List.of());

    public static final RoadGoldenMetrics R09 = new RoadGoldenMetrics(
        406, 0, 4892, 0, 0, 2252, 0, 4892, 0, 2658, 0, 1, List.of());

    public static final RoadGoldenMetrics R10 = new RoadGoldenMetrics(
        217, 0, 0, 0, 248, 0, 0, 0, 0, 465, 1, 0, List.of());

    // 立交：保留隧道顶衬砌/承重层后，下层净空不再把洞顶当可挖土体灌空气。
    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        420, 0, 0, 0, 234, 0, 0, 0, 0, 699, 1, 0, List.of());

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
