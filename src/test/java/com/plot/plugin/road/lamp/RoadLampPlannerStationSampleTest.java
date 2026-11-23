package com.plot.plugin.road.lamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadLampPlannerStationSampleTest {

    @Test
    void halfSpacingTiesPreferTheEarlierIntegerSample() {
        assertEquals(4, RoadLampPlanner.nearestStationSample(4.5));
        assertEquals(-5, RoadLampPlanner.nearestStationSample(-4.5));
        assertEquals(4, RoadLampPlanner.nearestStationSample(4.0));
        assertEquals(5, RoadLampPlanner.nearestStationSample(4.6));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 11, 15})
    void oddSpacingStaggeredPreviewOffsetsMatchPlannerQuantization(int spacing) {
        double phase = spacing / 2.0;
        assertEquals(phase, new RoadLampPlacement(
            RoadLampPresets.ALL.getFirst().style(),
            RoadLampPlacement.Sides.STAGGERED,
            spacing,
            0,
            2,
            true,
            false).rightSidePhase());
        assertNotEquals((int) Math.round(phase), RoadLampPlanner.nearestStationSample(phase));

        for (int z = -spacing; z <= spacing; z += spacing) {
            int station = z;
            int previewRight = RoadLampPlanner.nearestStationSample(station + phase);
            assertEquals((int) Math.floor(station + phase), previewRight,
                "spacing=" + spacing + " z=" + station + " phase=" + phase);
            assertNotEquals((int) Math.round(station + phase), previewRight,
                "Math.round drifts for odd spacing at z=" + station);
        }
    }
}
