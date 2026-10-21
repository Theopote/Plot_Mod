package com.plot.plugin.earthwork.volume;

import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1:1：挖填统计与分区偏移意图按几何方块计量。 */
class MinecraftOneToOneVolumeTest {

    @Test
    void fromMetricsTracksGeometricAndChangedBlocks() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(1000L, 800L, 100L, 80L);

        assertEquals(1000L, report.geometricCutVolume());
        assertEquals(800L, report.geometricFillVolume());
        assertEquals(200L, report.netGeometricVolume());
        assertEquals(180L, report.totalChangedBlocks());
    }

    @Test
    void geometricZoneOffsetsUseCutMinusFillIntent() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("surplus", EarthworkVolumeReport.fromMetrics(100L, 20L, 0L, 0L));
        byZone.put("deficit", EarthworkVolumeReport.fromMetrics(10L, 90L, 0L, 0L));

        Map<String, Integer> cells = Map.of("surplus", 10, "deficit", 10);
        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone, cells, null);

        assertTrue(offsets.get("surplus") > 0, "挖多应抬升");
        assertTrue(offsets.get("deficit") < 0, "填多应降低");
        assertEquals(8, offsets.get("surplus"));
        assertEquals(-8, offsets.get("deficit"));
    }

    @Test
    void geometricZoneOffsetsClampExtremeIntent() {
        Map<String, EarthworkVolumeReport> byZone = Map.of(
            "deep", EarthworkVolumeReport.fromMetrics(1_000_000L, 0L, 0L, 0L));
        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone, Map.of("deep", 10), null);

        assertEquals(EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET, offsets.get("deep"));
    }
}
