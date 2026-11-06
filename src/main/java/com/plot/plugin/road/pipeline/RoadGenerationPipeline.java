package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.construction.WaterCrossingConstructionResolver;
import com.plot.plugin.road.pipeline.corridor.RoadCorridorClearancePass;
import com.plot.plugin.road.pipeline.corridor.RoadCorridorPlanBuilder;
import com.plot.plugin.road.pipeline.corridor.RoadCorridorSolidEmitter;
import com.plot.plugin.road.pipeline.corridor.RoadGenerationIntegrityValidator;
import com.plot.plugin.road.pipeline.corridor.TunnelFinalVoxelValidator;
import com.plot.plugin.road.pipeline.corridor.TunnelInteriorClearancePass;
import com.plot.plugin.road.pipeline.corridor.TunnelPortalBackfillPass;
import com.plot.plugin.road.pipeline.crosssection.RoadCrossSectionBuilder;
import com.plot.plugin.road.pipeline.crosssection.TunnelStructureGenerator;
import com.plot.plugin.road.pipeline.furniture.RoadFurnitureGenerator;
import com.plot.plugin.road.pipeline.facility.RoadStationFacilityGenerator;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator;
import com.plot.plugin.road.pipeline.raster.RoadVoxelRasterizerPass;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates incremental road generation stages while {@link com.plot.plugin.road.RoadGenerator}
 * remains the public façade.
 *
 * <pre>
 * RoadEdgeBuildOrchestrator (profile solve + build request)
 * └─ RoadGenerationPipeline
 *    ├─ RoadGeometrySampler
 *    ├─ RoadConstructionClassifier + WaterCrossingConstructionResolver
 *    ├─ RoadCorridorPlanBuilder
 *    ├─ RoadCorridorClearancePass
 *    ├─ TunnelInteriorClearancePass
 *    ├─ RoadCorridorSolidEmitter
 *    ├─ RoadCrossSectionBuilder
 *    ├─ RoadMarkingGenerator
 *    ├─ RoadFurnitureGenerator
 *    ├─ RoadStationFacilityGenerator
 *    ├─ TunnelStructureGenerator
 *    ├─ TunnelPortalBackfillPass
 *    ├─ RoadVoxelRasterizerPass
 *    ├─ RoadSurfacePlacementPass
 *    ├─ RoadGenerationIntegrityValidator
 *    └─ TunnelFinalVoxelValidator
 * </pre>
 *
 * <p>Profile solving runs upstream via {@link com.plot.plugin.road.pipeline.profile.RoadProfileSolveCoordinator}.
 * {@link RoadGenerationPipelineHost} implements {@link RoadGenerationPipelineContext.Host}.
 */
public final class RoadGenerationPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadGenerationPipeline.class);

    private static long stage(String name, long startedNanos, String edgeId) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (elapsedMs >= 25L) {
            LOGGER.debug("road-gen stage {} edge={} durationMs={}", name, edgeId, elapsedMs);
        }
        return System.nanoTime();
    }

    private static void generateTunnelStructures(
            RoadGenerationPipelineContext ctx,
            RoadGenerationPipelineContext.Host host) {
        TunnelStructureGenerator.generate(
            new TunnelStructureGenerator.Host() {
                @Override
                public String resolveBlockId(String material) {
                    return host.resolveBlockId(material);
                }

                @Override
                public int snapEndpointElevation(com.plot.api.geometry.Vec2d center, int targetY) {
                    return host.snapEndpointElevation(center, targetY);
                }
            },
            ctx.solids(),
            ctx.metrics(),
            ctx.request().tunnelStyle(),
            ctx.segments(),
            ctx.heightInfos(),
            ctx.request().crossSections(),
            ctx.terrain(),
            ctx.unitsPerBlock(),
            ctx.detection().constructionTypes(),
            ctx.request().designElevation(),
            ctx.buildProfile(),
            ctx.request().profileWaterCrossings(),
            ctx.detection(),
            new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(com.plot.api.geometry.Vec2d planPoint) {
                    return host.canvasToBlockPos(planPoint).getX();
                }

                @Override
                public int worldZ(com.plot.api.geometry.Vec2d planPoint) {
                    return host.canvasToBlockPos(planPoint).getZ();
                }
            });
    }

    public RoadGenerationResult execute(
            RoadGenerationBuildRequest request,
            RoadGenerationPipelineContext.Host host) {
        var ctx = new RoadGenerationPipelineContext(request);

        ctx.setSegments(RoadGeometrySampler.sample(
            request.pathPoints(),
            host.config().getPathSampleDistance(),
            host::estimateCanvasUnitsPerBlock));

        ctx.setUnitsPerBlock(host.estimateCanvasUnitsPerBlock(request.pathPoints(), ctx.segments()));

        int gradingEnvelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(request.crossSection());
        ConstructionDetection detection = RoadConstructionClassifier.classify(
            ctx.segments(),
            request.heightInfos(),
            request.terrain(),
            host.config(),
            request.terrainStyle(),
            host::canvasToBlockPos,
            gradingEnvelopeWidth,
            request.tunnelStyle(),
            new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(com.plot.api.geometry.Vec2d planPoint) {
                    return host.canvasToBlockPos(planPoint).getX();
                }

                @Override
                public int worldZ(com.plot.api.geometry.Vec2d planPoint) {
                    return host.canvasToBlockPos(planPoint).getZ();
                }
            }, ctx.unitsPerBlock());
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
        String edgeId = request.carriagewaySeedKey() != null ? request.carriagewaySeedKey() : "standalone";
        long pipelineStarted = System.nanoTime();
        try {
            ctx.initBuildState(host.maxDedupKeys());
            ctx.solids().setCoordinateService(host.coordinateTransformer());
            ctx.solids().setOverflowHandler(model -> {
                host.flushEdgeSolids(ctx.result(), model);
                model.clear();
            });
            var planHost = RoadCorridorPlanBuilder.PlanHost.from(
                host, ctx.request().terrainStyle(), ctx.request().tunnelStyle());
            long stageStarted = System.nanoTime();
            var plan = RoadCorridorPlanBuilder.build(ctx, host);
            ctx.setCorridorPlan(plan);
            if (host instanceof RoadGenerationPipelineHost pipelineHost) {
                pipelineHost.captureCorridorPlanForTesting(plan);
            }
            stageStarted = stage("corridor-plan", stageStarted, edgeId);
            RoadCorridorClearancePass.apply(
                ctx.solids(), plan, ctx.terrain(), planHost, ctx.metrics(), ctx.request().gradeSeparation());
            stageStarted = stage("open-clearance", stageStarted, edgeId);
            TunnelInteriorClearancePass.apply(
                ctx.solids(),
                ctx.segments(),
                ctx.heightInfos(),
                ctx.detection().constructionTypes(),
                ctx.request().crossSections(),
                ctx.request().designElevation(),
                ctx.buildProfile(),
                ctx.terrain(),
                ctx.unitsPerBlock(),
                ctx.request().tunnelStyle(),
                planHost,
                new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                    @Override
                    public int worldX(com.plot.api.geometry.Vec2d planPoint) {
                        return host.canvasToBlockPos(planPoint).getX();
                    }

                    @Override
                    public int worldZ(com.plot.api.geometry.Vec2d planPoint) {
                        return host.canvasToBlockPos(planPoint).getZ();
                    }
                },
                ctx.metrics());
            stageStarted = stage("tunnel-clearance", stageStarted, edgeId);
            RoadCorridorSolidEmitter.emit(
                ctx.solids(),
                plan,
                ctx.terrain(),
                planHost,
                ctx.metrics(),
                host.resolveBlockId(host.config().getFillSlopeMaterial()),
                ctx.request().gradeSeparation());
            stageStarted = stage("solid-emit", stageStarted, edgeId);
            RoadCrossSectionBuilder.build(ctx, host);
            stageStarted = stage("cross-section", stageStarted, edgeId);
            RoadMarkingGenerator.generate(ctx, host);
            RoadFurnitureGenerator.generate(ctx, host);
            RoadStationFacilityGenerator.generate(ctx, host);
            generateTunnelStructures(ctx, host);
            TunnelPortalBackfillPass.apply(ctx.solids(), plan, ctx.terrain(), planHost);
            stageStarted = stage("structures", stageStarted, edgeId);
            RoadGenerationResult result = RoadVoxelRasterizerPass.rasterize(ctx, host);
            stageStarted = stage("rasterize", stageStarted, edgeId);
            java.util.function.Function<net.minecraft.util.math.BlockPos, String> previousBlockId =
                host instanceof RoadGenerationPipelineHost concrete
                    ? concrete.projectionHandler()::getBlockIdAt
                    : null;
            com.plot.plugin.road.pipeline.corridor.RoadSurfacePlacementPass.apply(
                result,
                plan,
                request.tunnelStyle().clearHeight(),
                request.gradeSeparation(),
                previousBlockId);
            RoadGenerationIntegrityValidator.applyPlacementsToResult(result, plan, ctx.terrain(), planHost);
            TunnelFinalVoxelValidator.applyReportToResult(
                result, TunnelFinalVoxelValidator.validate(result, ctx.terrain()));
            stage("surface-validate", stageStarted, edgeId);
            stage("pipeline-total", pipelineStarted, edgeId);
            return result;
        } finally {
            host.clearEndpointSnaps();
        }
    }
}
