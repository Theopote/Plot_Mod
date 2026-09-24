package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictPreviewJobTest {

    @Test
    void checkProgressGranularityUsesMultiplePipelineStages() {
        int defaultStages = BuildingGenerationPipeline.createDefault().stageCount();
        int frameStages = BuildingGenerationPipeline.createFrameOnly().stageCount();
        assertTrue(defaultStages >= 5, "default pipeline should expose enough stages for smooth check progress");
        assertTrue(frameStages >= 2, "frame pipeline should expose multiple stages");
    }
}
