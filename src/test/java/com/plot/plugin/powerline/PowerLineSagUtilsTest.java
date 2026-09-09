package com.plot.plugin.powerline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSagUtilsTest {

    @Test
    void zeroSagRatioIsLinearInterpolation() {
        List<Double> profile = PowerLineSagUtils.computeSagProfile(100.0, 80.0, 60.0, 0.0, 5);
        assertEquals(5, profile.size());
        assertEquals(80.0, profile.get(0), 1e-9);
        assertEquals(70.0, profile.get(2), 1e-9);
        assertEquals(60.0, profile.get(4), 1e-9);
    }

    @Test
    void positiveSagDropsAtMidspan() {
        double span = 40.0;
        double sagRatio = 0.15;
        List<Double> profile = PowerLineSagUtils.computeSagProfile(span, 100.0, 100.0, sagRatio, 11);

        assertEquals(100.0, profile.getFirst(), 1e-9);
        assertEquals(100.0, profile.getLast(), 1e-9);
        double mid = profile.get(profile.size() / 2);
        double linearMid = 100.0;
        assertTrue(mid < linearMid);
        assertEquals(span * sagRatio, linearMid - mid, 0.5);
    }

    @Test
    void maxSagDepthCapsMidspanDrop() {
        double span = 200.0;
        double sagRatio = 0.15;
        List<Double> uncapped = PowerLineSagUtils.computeSagProfile(
            span, 100.0, 100.0, sagRatio, 11, 0.0);
        List<Double> capped = PowerLineSagUtils.computeSagProfile(
            span, 100.0, 100.0, sagRatio, 11, PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH);

        double uncappedDrop = 100.0 - uncapped.get(uncapped.size() / 2);
        double cappedDrop = 100.0 - capped.get(capped.size() / 2);
        assertTrue(uncappedDrop > PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH);
        assertEquals(PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH, cappedDrop, 0.5);
    }
}
