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
 * E2E: corridor earthwork must not place open batter under bridge/tunnel interiors.
 */
class RoadEarthworkIsolationE2ETest {

    @Test
    void r08BridgeValleyHasNoStructureInteriorSlopeCells() {
        RoadGenerationResult edge = previewEdge(RoadGoldenScenarioFactory.r08Bridge());
        assertEquals(0, edge.structureInteriorSlopeCellCount,
            "bridge span must not retain corridor slope cells, types=" + edge.constructionTypes);
        assertEquals(0, edge.orphanSlopePillarCount);
        assertEquals(0, edge.fillVolume,
            "bridge must not fill valley as ordinary roadbed");
        assertTrue(edge.constructionTypes.contains(RoadConstructionType.BRIDGE)
                || edge.bridgeLength > 0,
            "R08 must classify bridge, types=" + edge.constructionTypes);

        int roadInValley = 0;
        int subgradeInValley = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < 10 || pos.getX() > 20) {
                continue;
            }
            if (entry.getValue() == RoadSolidLayer.ROAD) {
                roadInValley++;
            } else if (entry.getValue() == RoadSolidLayer.SUBGRADE
                    && pos.getY() < 64
                    && pos.getY() > 50) {
                subgradeInValley++;
            }
        }
        assertTrue(roadInValley >= 5, "bridge deck must stay continuous, road columns=" + roadInValley);
        assertEquals(0, subgradeInValley,
            "ordinary subgrade fill under bridge valley must not be emitted");
    }

    @Test
    void r09TunnelHasNoStructureInteriorSlopeCells() {
        RoadGenerationResult edge = previewEdge(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.structureInteriorSlopeCellCount,
            "tunnel body must not retain corridor slope cells");
        assertTrue(edge.tunnelCount > 0 || edge.tunnelLength > 0 || !edge.tunnelBlocks.isEmpty(),
            "R09 must produce tunnel structure");
    }

    @Test
    void roadBridgeRoadCompositeIntegrity() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r08Bridge();
        RoadGenerationResult edge = previewEdge(scenario);
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertTrue(edge.bridgeCount > 0 || edge.bridgeLength > 0,
            "R08 scenario should classify bridge construction");
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
