package com.plot.plugin.road.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;

/**
 * Applies pipeline build metrics and construction statistics onto {@link RoadGenerationResult}.
 */
public final class RoadGenerationResultAssembler {
    private RoadGenerationResultAssembler() {
    }

    public static void mergeResult(RoadGenerationResult target, RoadGenerationResult source) {
        if (target != null) {
            target.mergeFrom(source);
        }
    }

    public static void mergeJunction(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        if (target == null || junction == null) {
            return;
        }
        RoadVoxelRasterizer.flushJunctionSolids(
            target,
            junction.getSolids(),
            coordinateTransformer,
            projectionHandler,
            roadBlockId,
            sidewalkBlockId,
            markingBlockId);
    }

    public static void mergeJunctionBlocks(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId) {
        mergeJunction(target, junction, coordinateTransformer, projectionHandler, roadBlockId, sidewalkBlockId, null);
    }

    public static void applyBuildMetrics(RoadGenerationResult result, RoadEdgeBuildMetrics metrics) {
        result.cutVolume = metrics.cutVolume;
        result.fillVolume = metrics.fillVolume;
        result.bridgeCount = metrics.bridgeCount;
        result.tunnelCount = metrics.tunnelCount;
    }

    public static void applyConstructionStats(RoadGenerationResult result, ConstructionDetection detection) {
        result.constructionTypes.addAll(detection.constructionTypes());
        for (int i = 0; i < detection.constructionTypes().size(); i++) {
            double distance = detection.segmentDistances().get(i);
            switch (detection.constructionTypes().get(i)) {
                case BRIDGE -> result.bridgeLength += distance;
                case TUNNEL -> result.tunnelLength += distance;
                case ROAD, CUT, FILL -> result.normalRoadLength += distance;
            }
        }
    }
}
