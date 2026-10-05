package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.construction.WaterCrossingConstructionResolver;
import com.plot.plugin.road.pipeline.crosssection.RoadCrossSectionBuilder;
import com.plot.plugin.road.pipeline.furniture.RoadFurnitureGenerator;
import com.plot.plugin.road.pipeline.facility.RoadStationFacilityGenerator;
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
 * RoadEdgeBuildOrchestrator (profile solve + build request)
 * └─ RoadGenerationPipeline
 *    ├─ RoadGeometrySampler
 *    ├─ RoadConstructionClassifier
 *    ├─ RoadCrossSectionBuilder
 *    ├─ RoadMarkingGenerator
 *    ├─ RoadFurnitureGenerator
 *    ├─ RoadStationFacilityGenerator
 *    ├─ RoadTerrainGrader
 *    └─ RoadVoxelRasterizerPass
 * </pre>
 *
 * <p>Profile solving runs upstream via {@link com.plot.plugin.road.pipeline.profile.RoadProfileSolveCoordinator}.
 * {@link RoadGenerationPipelineHost} implements {@link RoadGenerationPipelineContext.Host}.
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

        ctx.setUnitsPerBlock(host.estimateCanvasUnitsPerBlock(request.pathPoints(), ctx.segments()));

        ConstructionDetection detection = RoadConstructionClassifier.classify(
            ctx.segments(),
            request.heightInfos(),
            request.terrain(),
            host.config(),
            request.terrainStyle(),
            host::canvasToBlockPos);
        detection = WaterCrossingConstructionResolver.apply(
            detection,
            request.profileWaterCrossings(),
            request.heightInfos(),
            ctx.unitsPerBlock(),
            host.config(),
            request.terrain(),
            host::canvasToBlockPos);
        ctx.setDetection(detection);

        host.setEndpointSnaps(request.endpointSnaps());
        try {
            ctx.initBuildState();
            RoadCrossSectionBuilder.build(ctx, host);
            RoadMarkingGenerator.generate(ctx, host);
            RoadFurnitureGenerator.generate(ctx, host);
            RoadStationFacilityGenerator.generate(ctx, host);
            RoadTerrainGrader.grade(ctx, host);
            return RoadVoxelRasterizerPass.rasterize(ctx, host);
        } finally {
            host.clearEndpointSnaps();
        }
    }
}
