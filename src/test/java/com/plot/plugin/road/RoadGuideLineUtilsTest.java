package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGuideLineUtilsTest {

    @Test
    void guideLineTransitionsFromLowStartToHighEnd() {
        List<Integer> ground = List.of(60, 61, 62, 68, 72, 76, 80);
        List<Double> distances = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0);

        List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(ground, distances, 1.1f);

        assertEquals(7, guideLine.size());
        assertTrue(guideLine.getFirst() < guideLine.get(3));
        assertTrue(guideLine.get(3) < guideLine.getLast());

        for (int i = 1; i < guideLine.size(); i++) {
            assertTrue(guideLine.get(i) >= guideLine.get(i - 1) - 1);
        }
    }

    @Test
    void shortPathUsesConstantBalancedGuideLine() {
        List<Integer> ground = List.of(64, 70, 76);
        List<Double> distances = List.of(0.0, 5.0, 10.0);

        List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(ground, distances, 1.1f);

        assertEquals(3, guideLine.size());
        assertEquals(guideLine.getFirst(), guideLine.get(1));
        assertEquals(guideLine.getFirst(), guideLine.getLast());
    }

    @Test
    void manualAnchorsOverrideBalancedElevation() {
        List<Integer> ground = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            ground.add(64 + (i / 10));
            distances.add((double) i);
        }

        List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(
            ground, distances, 1.1f, 88, 72);

        assertEquals(88, guideLine.getFirst());
        assertEquals(72, guideLine.getLast());
    }

    @Test
    void shortPathRespectsSingleManualAnchor() {
        List<Integer> ground = List.of(64, 66, 68);
        List<Double> distances = List.of(0.0, 4.0, 8.0);

        List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(ground, distances, 1.1f, 90, null);

        assertTrue(guideLine.stream().allMatch(height -> height == 90));
    }
}
