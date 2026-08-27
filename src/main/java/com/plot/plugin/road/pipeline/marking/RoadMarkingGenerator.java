package com.plot.plugin.road.pipeline.marking;

import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;

/**
 * Lane dividers and centerline markings.
 */
public final class RoadMarkingGenerator {
    private RoadMarkingGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        host.generateMarkings(ctx);
    }
}
