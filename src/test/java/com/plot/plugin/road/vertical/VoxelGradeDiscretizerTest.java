package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VoxelGradeDiscretizerTest {
    @Test void linearRiseIsEvenlyDistributedAndHitsBothEndpoints() {
        List<Integer> rises = new ArrayList<>();
        int previous = 70;
        for (int station = 0; station <= 73; station++) {
            int current = VoxelGradeDiscretizer.linearElevationAtRatio(70, 75, station / 73.0);
            if (current > previous) rises.add(station);
            previous = current;
        }
        assertEquals(List.of(15, 30, 44, 59, 73), rises);
        for (int i = 1; i < rises.size(); i++) {
            assertTrue(rises.get(i) - rises.get(i - 1) == 14
                || rises.get(i) - rises.get(i - 1) == 15);
        }
    }

    @Test void continuousProfileNeverJumpsMoreThanOneBlockPerSample() {
        List<Integer> values = VoxelGradeDiscretizer.discretizeContinuousProfile(
            5, station -> 70 + station * station);
        for (int i = 1; i < values.size(); i++) {
            assertTrue(Math.abs(values.get(i) - values.get(i - 1)) <= 1);
        }
    }

    @Test void verticalCurveProducesChangingStepFrequency() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 100),
            PointOfVerticalIntersection.of(100, 85)));
        List<Integer> values = VoxelGradeDiscretizer.discretizeContinuousProfile(
            100, station -> VerticalAlignmentGeometry.elevationAt(alignment, station).orElse(70));
        List<Integer> riseStations = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > values.get(i - 1)) riseStations.add(i);
        }
        assertTrue(riseStations.size() >= 8);
        int earlyGap = riseStations.get(1) - riseStations.get(0);
        int lateGap = riseStations.getLast() - riseStations.get(riseStations.size() - 2);
        assertTrue(lateGap <= earlyGap);
    }
}
