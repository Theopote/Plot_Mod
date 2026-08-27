package com.plot.plugin.road.pipeline.raster;

import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.solid.RoadGenerationResult;

/**
 * Final pass: flush {@link com.plot.plugin.road.solid.RoadSolidModel} voxels into placement records.
 */
public final class RoadVoxelRasterizerPass {
    private RoadVoxelRasterizerPass() {
    }

    public static RoadGenerationResult rasterize(
            RoadGenerationPipelineContext ctx,
            RoadGenerationPipelineContext.Host host) {
        return host.rasterize(ctx);
    }
}
