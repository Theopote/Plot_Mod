package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainProfileFilterTest {

    @Test
    void distanceWindowMedianMatchesIndexRadiusOnUniformSpacing() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0);
        List<Double> raw = List.of(60.0, 62.0, 74.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        List<Double> filtered = TerrainProfileFilter.medianFilter(chain, 10.0);

        assertEquals(62.0, filtered.get(1), 1e-6,
            "10 m median window on 10 m spacing should include neighboring samples");
    }

    @Test
    void distanceWindowMedianUsesStationSpacingNotIndexOnly() {
        List<Double> stations = List.of(0.0, 5.0, 20.0, 25.0);
        List<Double> raw = List.of(60.0, 60.0, 75.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        List<Double> filtered = TerrainProfileFilter.medianFilter(chain, 10.0);

        assertEquals(60.0, filtered.get(1), 1e-6,
            "10 m window at 5 m should still see only low plateau samples");
        assertEquals(75.0, filtered.get(2), 1e-6,
            "10 m window at 20 m should see only high plateau samples");
    }

    @Test
    void stepTransitionSpreadsModeratePlateauSteps() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            63.0, 63.0, 63.0, 63.0, 63.0, 63.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);
        List<Double> sharpTrend = List.copyOf(raw);

        List<Double> spread = TerrainProfileFilter.spreadStepTransitions(
            chain, sharpTrend, 40.0);

        assertTrue(maxAdjacentDelta(spread) < maxAdjacentDelta(sharpTrend),
            () -> "spread trend jump " + maxAdjacentDelta(spread)
                + " should be less than sharp jump " + maxAdjacentDelta(sharpTrend));
        assertTrue(countTransitionSamples(spread, 60.0, 63.0) >= 2,
            "moderate step should produce intermediate trend samples");
    }

    @Test
    void gentlePresetSpreadsStepMoreThanTight() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            63.0, 63.0, 63.0, 63.0, 63.0, 63.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);
        List<Double> baseTrend = List.copyOf(raw);

        List<Double> gentle = TerrainProfileFilter.spreadStepTransitions(
            chain, baseTrend, TerrainFollowPreset.GENTLE.stepTransitionMeters());
        List<Double> tight = TerrainProfileFilter.spreadStepTransitions(
            chain, baseTrend, TerrainFollowPreset.TIGHT.stepTransitionMeters());

        assertTrue(maxAdjacentDelta(gentle) <= maxAdjacentDelta(tight) + 1e-6);
        assertTrue(countTransitionSamples(gentle, 60.0, 63.0)
            >= countTransitionSamples(tight, 60.0, 63.0));
    }

    @Test
    void largeCliffStepsAreLeftToDownstreamSolver() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);
        List<Double> filteredTrend = TerrainProfileFilter.movingAverage(
            chain.withElevations(TerrainProfileFilter.medianFilter(chain, 10.0)),
            30.0);

        List<Double> spread = TerrainProfileFilter.spreadStepTransitions(
            chain, filteredTrend, TerrainFollowPreset.STANDARD.stepTransitionMeters());

        assertEquals(filteredTrend, spread,
            "15-block cliff on 100 m road should defer to grade-limited solver");
    }

    private static double maxAdjacentDelta(List<Double> elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.size(); i++) {
            max = Math.max(max, Math.abs(elevations.get(i) - elevations.get(i - 1)));
        }
        return max;
    }

    private static int countTransitionSamples(List<Double> elevations, double low, double high) {
        int count = 0;
        for (double elevation : elevations) {
            if (elevation > low + 1e-6 && elevation < high - 1e-6) {
                count++;
            }
        }
        return count;
    }
}
