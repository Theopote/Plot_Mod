package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.solid.RoadGenerationResult;

/**
 * Applies pipeline build metrics and construction statistics onto {@link RoadGenerationResult}.
 */
public final class RoadGenerationResultAssembler {
    private RoadGenerationResultAssembler() {
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
