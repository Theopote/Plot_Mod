package com.plot.plugin.road.pipeline.terrain;

import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;

/**
 * Cut/fill grading of terrain around the road envelope.
 */
public final class RoadTerrainGrader {
    private RoadTerrainGrader() {
    }

    public static void grade(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        host.gradeTerrain(ctx);
    }
}
