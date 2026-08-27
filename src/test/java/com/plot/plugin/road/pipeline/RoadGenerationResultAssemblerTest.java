package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadGenerationResultAssemblerTest {

    @Test
    void applyConstructionStatsAccumulatesLengthsByType() {
        RoadGenerationResult result = new RoadGenerationResult(100.0);
        ConstructionDetection detection = new ConstructionDetection(
            List.of(),
            List.of(),
            List.of(RoadConstructionType.BRIDGE, RoadConstructionType.TUNNEL, RoadConstructionType.ROAD),
            List.of(10.0, 5.0, 20.0));

        RoadGenerationResultAssembler.applyConstructionStats(result, detection);

        assertEquals(10.0, result.bridgeLength, 1e-9);
        assertEquals(5.0, result.tunnelLength, 1e-9);
        assertEquals(20.0, result.normalRoadLength, 1e-9);
    }

    @Test
    void applyBuildMetricsCopiesVolumesAndCounts() {
        RoadGenerationResult result = new RoadGenerationResult(50.0);
        RoadEdgeBuildMetrics metrics = new RoadEdgeBuildMetrics();
        metrics.cutVolume = 3;
        metrics.fillVolume = 7;
        metrics.bridgeCount = 2;
        metrics.tunnelCount = 1;

        RoadGenerationResultAssembler.applyBuildMetrics(result, metrics);

        assertEquals(3, result.cutVolume);
        assertEquals(7, result.fillVolume);
        assertEquals(2, result.bridgeCount);
        assertEquals(1, result.tunnelCount);
    }
}
