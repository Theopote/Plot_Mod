package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTrendBuilderTest {

    @Test
    void trendIsSmootherThanRawOnStepTerrain() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        TerrainTrendResult result = TerrainTrendBuilder.build(chain, TerrainFollowPreset.STANDARD);

        assertEquals(raw.size(), result.trendElevations().size());
        double rawStepJump = maxAdjacentDelta(raw);
        double trendStepJump = maxAdjacentDelta(result.trendElevations());
        assertTrue(trendStepJump < rawStepJump,
            () -> "trend jump " + trendStepJump + " should be less than raw jump " + rawStepJump);
        assertNotEquals(raw, result.trendElevations());
    }

    @Test
    void manualEndpointsOverrideTrend() {
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(
            List.of(0.0, 50.0, 100.0),
            List.of(60.0, 70.0, 80.0));

        TerrainTrendResult result = TerrainTrendBuilder.build(
            chain, TerrainFollowPreset.STANDARD, 55, 85);

        assertEquals(55, result.toIntegerGuideLine().getFirst());
        assertEquals(85, result.toIntegerGuideLine().getLast());
    }

    @Test
    void gentlePresetSmoothsMoreThanTight() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0);
        List<Double> raw = List.of(60.0, 60.0, 60.0, 75.0, 75.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        TerrainTrendResult gentle = TerrainTrendBuilder.build(chain, TerrainFollowPreset.GENTLE);
        TerrainTrendResult tight = TerrainTrendBuilder.build(chain, TerrainFollowPreset.TIGHT);

        double gentleJump = maxAdjacentDelta(gentle.trendElevations());
        double tightJump = maxAdjacentDelta(tight.trendElevations());
        assertTrue(gentleJump <= tightJump + 1e-6);
        assertTrue(countTransitionSamples(gentle.trendElevations(), 60.0, 75.0)
            >= countTransitionSamples(tight.trendElevations(), 60.0, 75.0),
            "gentle should spread the step over more stations than tight");
    }

    @Test
    void stepTerrainTrendUsesGradualTransitionNotPlateauJump() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        TerrainTrendResult result = TerrainTrendBuilder.build(chain, TerrainFollowPreset.STANDARD);

        assertTrue(maxAdjacentDelta(result.trendElevations()) < maxAdjacentDelta(raw),
            () -> "standard trend should soften 15 m raw step, got jump "
                + maxAdjacentDelta(result.trendElevations()));
    }

    /** VA-A11: near a locked start, target height dominates; far away terrain returns. */
    @Test
    void startEndpointBlendFavorsTargetNearLock() {
        // Flat terrain Y60; locked start Y80; ramp should descend toward terrain.
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0);
        List<Double> raw = List.of(60.0, 60.0, 60.0, 60.0, 60.0, 60.0, 60.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        TerrainTrendResult result = TerrainTrendBuilder.build(
            chain, TerrainFollowPreset.TIGHT, 80, null);

        List<Double> trend = result.trendElevations();
        assertEquals(80.0, trend.getFirst(), 1e-6);
        assertTrue(trend.get(1) > 70.0,
            "station near start must stay close to locked Y80, got " + trend.get(1));
        assertTrue(trend.get(1) > trend.get(2),
            "blend must leave the locked end toward terrain");
        assertTrue(trend.get(trend.size() - 1) < 65.0,
            "far from start should recover near terrain Y60");
    }

    /** VA-A11: overlapping start/end ramps on a short road unify both locks. */
    @Test
    void overlappingEndpointRampsKeepExactEnds() {
        List<Double> stations = List.of(0.0, 5.0, 10.0, 15.0, 20.0);
        List<Double> raw = List.of(60.0, 60.0, 60.0, 60.0, 60.0);
        TerrainProfileSampleChain chain = new TerrainProfileSampleChain(stations, raw);

        TerrainTrendResult result = TerrainTrendBuilder.build(
            chain, TerrainFollowPreset.TIGHT, 80, 70);

        List<Double> trend = result.trendElevations();
        assertEquals(80.0, trend.getFirst(), 1e-6);
        assertEquals(70.0, trend.getLast(), 1e-6);
        assertTrue(trend.get(1) > trend.get(3),
            "interior should transition from start lock toward end lock");
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
