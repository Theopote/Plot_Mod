package com.plot.plugin.road.pipeline.raster;

import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;

/**
 * Final pass: flush {@link RoadSolidModel} voxels into placement records.
 */
public final class RoadVoxelRasterizerPass {
    private RoadVoxelRasterizerPass() {
    }

    public static RoadGenerationResult rasterize(
            RoadGenerationPipelineContext ctx,
            RoadGenerationPipelineContext.Host host) {
        RoadGenerationResult result = ctx.createResult();
        RoadGenerationResultAssembler.applyBuildMetrics(result, ctx.metrics());
        RoadGenerationResultAssembler.applyConstructionStats(result, ctx.detection());
        host.flushEdgeSolids(result, ctx.solids());
        return result;
    }
}
