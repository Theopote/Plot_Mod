package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.crosssection.RoadCrossSectionBuilder;
import com.plot.plugin.road.pipeline.furniture.RoadFurnitureGenerator;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator;
import com.plot.plugin.road.pipeline.raster.RoadVoxelRasterizerPass;
import com.plot.plugin.road.pipeline.terrain.RoadTerrainGrader;
import com.plot.plugin.road.solid.RoadGenerationResult;

/**
 * Orchestrates incremental road generation stages while {@link com.plot.plugin.road.RoadGenerator}
 * remains the public façade.
 *
 * <pre>
 * RoadGenerationPipeline
 * ├─ RoadGeometrySampler
 * ├─ RoadProfileSolver (upstream, before build pipeline)
 * ├─ RoadConstructionClassifier
 * ├─ RoadCrossSectionBuilder
 * ├─ RoadMarkingGenerator
 * ├─ RoadFurnitureGenerator
 * ├─ RoadTerrainGrader
 * └─ RoadVoxelRasterizerPass
 * </pre>
 */
public final class RoadGenerationPipeline {
    public RoadGenerationResult execute(
            RoadGenerationBuildRequest request,
            RoadGenerationPipelineContext.Host host) {
        var ctx = new RoadGenerationPipelineContext(request);

        ctx.setSegments(RoadGeometrySampler.sample(
            request.pathPoints(),
            host.config().getPathSampleDistance(),
            host::estimateCanvasUnitsPerBlock));

        ctx.setDetection(RoadConstructionClassifier.classify(
            ctx.segments(),
            request.heightInfos(),
            request.terrain(),
            host.config(),
            canvas -> host.canvasToBlockPos(canvas)));

        ctx.setUnitsPerBlock(host.estimateCanvasUnitsPerBlock(request.pathPoints(), ctx.segments()));

        host.setEndpointSnaps(request.endpointSnaps());
        try {
            ctx.initBuildState();
            RoadCrossSectionBuilder.build(ctx, host);
            RoadMarkingGenerator.generate(ctx, host);
            RoadFurnitureGenerator.generate(ctx, host);
            RoadTerrainGrader.grade(ctx, host);
            return RoadVoxelRasterizerPass.rasterize(ctx, host);
        } finally {
            host.clearEndpointSnaps();
        }
    }
}
