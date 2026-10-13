package com.plot.plugin.road.pipeline.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildHeightSequenceSmootherTest {

    @Test
    void flatDesignProducesConstantBlockY() {
        List<BuildHeightSample> samples = samples(
            new int[] {64, 64, 64, 64, 64},
            64.0);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertConstant(result.samples(), 64);
    }

    @Test
    void monotonicAscentProducesNonDecreasingBlockY() {
        List<BuildHeightSample> samples = samples(
            new int[] {64, 64, 65, 65, 66, 66, 67},
            station -> 64.0 + station * 0.5);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertNonDecreasing(result.samples());
        assertEquals(67, result.samples().getLast().buildY());
    }

    @Test
    void monotonicDescentProducesNonIncreasingBlockY() {
        List<BuildHeightSample> samples = samples(
            new int[] {67, 66, 66, 65, 65, 64, 64},
            station -> 67.0 - station * 0.5);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertNonIncreasing(result.samples());
    }

    @Test
    void shortUnconstrainedCrestIsFlattened() {
        List<BuildHeightSample> samples = samples(
            new int[] {64, 64, 65, 65, 64, 64, 64},
            64.0);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertConstant(result.samples(), 64);
        assertTrue(result.diagnostics().shortReversalCountAfter()
            < result.diagnostics().shortReversalCountBefore());
    }

    @Test
    void shortUnconstrainedSagIsFlattened() {
        List<BuildHeightSample> samples = samples(
            new int[] {65, 65, 64, 64, 65, 65, 65},
            65.0);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertConstant(result.samples(), 65);
    }

    @Test
    void intentionalFixedCrestIsPreserved() {
        // Design itself has a clear crest; do not flatten.
        List<BuildHeightSample> samples = new ArrayList<>();
        double[] design = {64, 64.2, 65.5, 65.5, 64.2, 64, 64};
        int[] build = {64, 64, 66, 66, 64, 64, 64};
        for (int i = 0; i < build.length; i++) {
            samples.add(new BuildHeightSample(i, design[i], build[i]));
        }
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertTrue(result.samples().get(2).buildY() >= 65
            || result.samples().get(3).buildY() >= 65);
    }

    @Test
    void longAscentIsNotFlattened() {
        int[] build = new int[40];
        for (int i = 0; i < build.length; i++) {
            build[i] = 64 + i / 5;
        }
        List<BuildHeightSample> samples = samples(build, station -> 64.0 + station * 0.2);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertNonDecreasing(result.samples());
        assertTrue(result.samples().getLast().buildY() > result.samples().getFirst().buildY());
    }

    @Test
    void longDescentIsNotFlattened() {
        int[] build = new int[40];
        for (int i = 0; i < build.length; i++) {
            build[i] = 70 - i / 5;
        }
        List<BuildHeightSample> samples = samples(build, station -> 70.0 - station * 0.2);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertNonIncreasing(result.samples());
    }

    @Test
    void lockedEndpointsRemainUnchanged() {
        List<BuildHeightSample> samples = samples(
            new int[] {70, 64, 65, 64, 60},
            64.0);
        BitSet locked = new BitSet();
        locked.set(0);
        locked.set(4);
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), locked);
        assertEquals(70, result.samples().getFirst().buildY());
        assertEquals(60, result.samples().getLast().buildY());
    }

    private static List<BuildHeightSample> samples(int[] buildY, double designY) {
        return samples(buildY, station -> designY);
    }

    private static List<BuildHeightSample> samples(
            int[] buildY,
            java.util.function.DoubleUnaryOperator designAt) {
        List<BuildHeightSample> samples = new ArrayList<>(buildY.length);
        for (int i = 0; i < buildY.length; i++) {
            samples.add(new BuildHeightSample(i, designAt.applyAsDouble(i), buildY[i]));
        }
        return samples;
    }

    private static void assertConstant(List<BuildHeightSample> samples, int expected) {
        for (BuildHeightSample sample : samples) {
            assertEquals(expected, sample.buildY());
        }
    }

    private static void assertNonDecreasing(List<BuildHeightSample> samples) {
        for (int i = 1; i < samples.size(); i++) {
            assertTrue(samples.get(i).buildY() >= samples.get(i - 1).buildY(),
                "expected non-decreasing at " + i);
        }
    }

    private static void assertNonIncreasing(List<BuildHeightSample> samples) {
        for (int i = 1; i < samples.size(); i++) {
            assertTrue(samples.get(i).buildY() <= samples.get(i - 1).buildY(),
                "expected non-increasing at " + i);
        }
    }
}
