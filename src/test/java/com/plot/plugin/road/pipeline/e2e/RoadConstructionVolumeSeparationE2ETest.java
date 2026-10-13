package com.plot.plugin.road.pipeline.e2e;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E checks that road-body clearance, structure volumes, and decoration widths stay separated.
 */
class RoadConstructionVolumeSeparationE2ETest {

    @Test
    void r08BridgeHasNoWideSubgradeCorridorUnderDeck() {
        RoadGenerationResult edge = previewEdge(RoadGoldenScenarioFactory.r08Bridge());
        assertEquals(0, edge.fillVolume);
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertTrue(edge.constructionTypes.contains(RoadConstructionType.BRIDGE)
            || edge.bridgeLength > 0);
    }

    @Test
    void r09TunnelStructureInteriorAvoidsOpenCorridorFill() {
        RoadGenerationResult edge = previewEdge(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertTrue(edge.tunnelCount > 0 || edge.tunnelLength > 0 || !edge.tunnelBlocks.isEmpty());
    }

    @Test
    void r07DeepCutDoesNotLeaveWideFlatSubgradeOutsideRoadBody() {
        RoadGenerationResult edge = previewEdge(RoadGoldenScenarioFactory.r07TerrainFit());
        int subgradeFarFromCenter = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() != RoadSolidLayer.SUBGRADE) {
                continue;
            }
            if (Math.abs(pos.getZ()) >= 8) {
                subgradeFarFromCenter++;
            }
        }
        assertTrue(subgradeFarFromCenter <= 4,
            "SUBGRADE should not extend far beyond road body as flat corridor, count="
                + subgradeFarFromCenter);
    }

    private static RoadGenerationResult previewEdge(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            generator,
            scenario.terrain());
        assertEquals(1, preview.edgeResults().size());
        return preview.edgeResults().values().iterator().next();
    }
}
