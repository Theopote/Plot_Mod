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

    // Interior-center curb fillets shrink plaza vs exterior-arc bug; seams treat MARKING as surface.
    public static final RoadGoldenMetrics R03 = new RoadGoldenMetrics(
        330, 0, 0, 36, 0, 0, 75, 0, 0, 299, 0, 0, List.of());

    public static final RoadGoldenMetrics R04 = new RoadGoldenMetrics(
        447, 0, 0, 60, 0, 0, 107, 0, 0, 389, 0, 0, List.of());

    // Dense rotated approaches retain complete pavement at the five-way plaza seam.
    public static final RoadGoldenMetrics R05 = new RoadGoldenMetrics(
        539, 0, 0, 63, 0, 0, 114, 0, 0, 474, 0, 0, List.of());

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

    // GS protection uses sharp plaza footprint; curb fillets no longer shrink deck-cap coverage.
    public static final RoadGoldenMetrics R11 = new RoadGoldenMetrics(
        451, 0, 0, 0, 240, 65, 96, 0, 0, 690, 1, 0, List.of());

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
