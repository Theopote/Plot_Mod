package com.plot.plugin.road.pipeline.furniture;

import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;

/**
 * Street furniture such as streetlights along the road corridor.
 */
public final class RoadFurnitureGenerator {
    private RoadFurnitureGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        host.generateFurniture(ctx);
    }
}
