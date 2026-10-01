package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainProfileFilterTest {

    @Test
    void medianFilterRemovesIsolatedSpike() {
        TerrainProfileSampleChain chain = chain(
            List.of(0.0, 10.0, 20.0, 30.0, 40.0),
            List.of(64.0, 64.0, 80.0, 64.0, 64.0));

        List<Double> filtered = TerrainProfileFilter.medianFilter(chain, 15.0);

        assertEquals(64.0, filtered.get(2), 0.01);
        assertTrue(Math.abs(filtered.get(1) - 64.0) < 4.0);
        assertTrue(Math.abs(filtered.get(3) - 64.0) < 4.0);
    }

    @Test
    void movingAverageSmoothsStep() {
        TerrainProfileSampleChain chain = chain(
            List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0),
            List.of(60.0, 60.0, 60.0, 70.0, 70.0, 70.0));

        List<Double> smoothed = TerrainProfileFilter.movingAverage(chain, 30.0);

        double interiorJump = Math.abs(smoothed.get(3) - smoothed.get(2));
        assertTrue(interiorJump < 8.0, "moving average should soften the 10 m step");
    }

    private static TerrainProfileSampleChain chain(List<Double> stations, List<Double> elevations) {
        return new TerrainProfileSampleChain(stations, elevations);
    }
}
