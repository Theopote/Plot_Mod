package com.plot.plugin.road.pipeline.e2e;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTunnelQualityE2ETest {

    @Test
    void r09TunnelProducesStructureWithoutOpenCorridorFill() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertEquals(0, edge.fillVolume);
        assertTrue(edge.tunnelCount > 0 || edge.tunnelLength > 0 || !edge.tunnelBlocks.isEmpty(),
            "R09 must emit tunnel structure blocks");
    }

    @Test
    void r09TunnelHasNoOrphanSlopePillars() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.orphanSlopePillarCount);
        assertEquals(0, edge.structureInteriorSlopeCellCount);
    }

    private static RoadGenerationResult preview(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(), generator, scenario.terrain());
        return preview.edgeResults().values().iterator().next();
    }
}
