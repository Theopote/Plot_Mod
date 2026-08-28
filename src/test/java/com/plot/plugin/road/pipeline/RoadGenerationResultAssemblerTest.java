package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void mergeResultCopiesSourceIntoTarget() {
        RoadGenerationResult target = new RoadGenerationResult(10.0);
        RoadGenerationResult source = new RoadGenerationResult(20.0);
        source.bridgeCount = 2;

        RoadGenerationResultAssembler.mergeResult(target, source);

        assertEquals(2, target.bridgeCount);
    }

    @Test
    void mergeJunctionNoOpsOnNullInputs() {
        RoadGenerationResult target = new RoadGenerationResult(0);
        RoadGenerationResultAssembler.mergeJunction(
            target,
            null,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance(),
            "minecraft:stone",
            "minecraft:stone",
            null);
        assertTrue(target.placementRecords.isEmpty());
    }

    @Test
    void mergeJunctionFlushesSolids() {
        RoadGenerationResult target = new RoadGenerationResult(0);
        RoadJunctionGenerator.JunctionBlocks junction = new RoadJunctionGenerator.JunctionBlocks();
        junction.getSolids().add(new Vec2d(0, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone");

        RoadGenerationResultAssembler.mergeJunction(
            target,
            junction,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance(),
            "minecraft:stone",
            "minecraft:stone",
            null);

        assertTrue(target.placementRecords.size() > 0 || !target.roadBlocks.isEmpty());
    }
}
