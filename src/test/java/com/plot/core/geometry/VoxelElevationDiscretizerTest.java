package com.plot.core.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelElevationDiscretizerTest {

    @Test
    void linearRiseIsEvenlyDistributedAndHitsBothEndpoints() {
        List<Integer> rises = new ArrayList<>();
        int previous = 70;
        for (int station = 0; station <= 73; station++) {
            int current = VoxelElevationDiscretizer.linearElevationAtRatio(70, 75, station / 73.0);
            if (current > previous) {
                rises.add(station);
            }
            previous = current;
        }
        assertEquals(List.of(15, 30, 44, 59, 73), rises);
        for (int i = 1; i < rises.size(); i++) {
            assertTrue(rises.get(i) - rises.get(i - 1) == 14
                || rises.get(i) - rises.get(i - 1) == 15);
        }
    }

    @Test
    void continuousProfileNeverJumpsMoreThanOneBlockPerSample() {
        List<Integer> values = VoxelElevationDiscretizer.discretizeContinuousProfile(
            5, station -> 70 + station * station);
        for (int i = 1; i < values.size(); i++) {
            assertTrue(Math.abs(values.get(i) - values.get(i - 1)) <= 1);
        }
    }

    @Test
    void linearSlopeProfileNeverJumpsMoreThanOneBlock() {
        int[] values = VoxelElevationDiscretizer.discretizeLinearSlope(64.0, 0.25, 0.0, 40.0);
        for (int i = 1; i < values.length; i++) {
            assertTrue(Math.abs(values[i] - values[i - 1]) <= 1);
        }
        assertTrue(values[values.length - 1] >= 73);
    }
}
