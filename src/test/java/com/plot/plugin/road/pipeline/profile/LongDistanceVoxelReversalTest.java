package com.plot.plugin.road.pipeline.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongDistanceVoxelReversalTest {

    @Test
    void sparseSpikeOnFlatDesignIsRemovedWithinMaxReversalSpan() {
        // Simulates sparse profile samples every 20 blocks with a +1 voxel bump.
        List<BuildHeightSample> samples = new ArrayList<>();
        samples.add(new BuildHeightSample(0, 78.0, 78));
        samples.add(new BuildHeightSample(20, 78.0, 79));
        samples.add(new BuildHeightSample(40, 78.0, 78));
        samples.add(new BuildHeightSample(60, 78.0, 78));

        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);

        assertEquals(78, result.samples().get(1).buildY(),
            "single-block spike on flat design should be flattened at sparse station");
        assertTrue(result.diagnostics().shortReversalCountAfter()
            <= result.diagnostics().shortReversalCountBefore());
    }

    @Test
    void wideIntentionalCrestOnFlatDesignIsPreserved() {
        List<BuildHeightSample> samples = new ArrayList<>();
        double[] design = {78, 78.2, 80.0, 80.0, 78.2, 78};
        int[] build = {78, 78, 80, 80, 78, 78};
        for (int i = 0; i < build.length; i++) {
            samples.add(new BuildHeightSample(i * 15.0, design[i], build[i]));
        }
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        assertTrue(result.samples().get(2).buildY() >= 79);
        assertTrue(result.samples().get(3).buildY() >= 79);
    }

    @Test
    void gradualReversalOverFortyBlocksOnFlatDesignIsFlattened() {
        List<BuildHeightSample> samples = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            int build = 64;
            if (i > 10 && i < 30) {
                build = 65;
            }
            samples.add(new BuildHeightSample(i, 64.0, build));
        }
        BuildHeightSequenceSmoother.SmoothingResult result = BuildHeightSequenceSmoother.smooth(
            samples, ProfileCompileOptions.defaults(), null);
        for (BuildHeightSample sample : result.samples()) {
            assertEquals(64, sample.buildY(), "flat design should not keep mid-span +1 plateau");
        }
    }
}
