package com.plot.plugin.earthwork.volume;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1:1：挖填统计与分区偏移意图不经压实/可利用率换算。 */
class MinecraftOneToOneVolumeTest {

    @Test
    void fromMetricsIgnoresNonDefaultConversionRatios() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            1000L, 800L, MaterialConversionModel.LEARNING, 100L, 80L);

        assertEquals(1000L, report.geometricCutVolume());
        assertEquals(800L, report.geometricFillVolume());
        assertEquals(1000.0, report.reusableCutVolume(), 1e-9);
        assertEquals(1000.0, report.compactedFillSupply(), 1e-9);
        assertEquals(800.0, report.compactedFillDemand(), 1e-9);
        assertEquals(200.0, report.exportVolume(), 1e-9);
        assertEquals(0.0, report.importVolume(), 1e-9);
        assertEquals(180L, report.totalChangedBlocks());
    }

    @Test
    void geometricZoneOffsetsUseCutMinusFillIntent() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("surplus", EarthworkVolumeReport.fromMetrics(
            100L, 20L, MaterialConversionModel.DEFAULT, 0L, 0L));
        byZone.put("deficit", EarthworkVolumeReport.fromMetrics(
            10L, 90L, MaterialConversionModel.DEFAULT, 0L, 0L));

        Map<String, Integer> cells = Map.of("surplus", 10, "deficit", 10);
        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone, cells, null);

        assertTrue(offsets.get("surplus") > 0, "挖多应抬升");
        assertTrue(offsets.get("deficit") < 0, "填多应降低");
        assertEquals(8, offsets.get("surplus")); // (100-20)/10
        assertEquals(-8, offsets.get("deficit")); // (10-90)/10
    }
}
