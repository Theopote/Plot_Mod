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

class RoadBridgeFillIsolationE2ETest {

    @Test
    void r08BridgeValleyHasNoSubgradeFillUnderDeck() {
        assertZeroFillUnderDeck(preview(RoadGoldenScenarioFactory.r08Bridge()), 10, 20);
    }

    @Test
    void r10WaterCrossingHasNoSubgradeFillUnderDeck() {
        assertZeroFillUnderDeck(preview(RoadGoldenScenarioFactory.r10WaterCrossing()), 8, 22);
    }

    @Test
    void shortHardThresholdBridgeStaysBridgeFamily() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r08Bridge());
        assertTrue(edge.constructionTypes.contains(RoadConstructionType.BRIDGE)
                || edge.bridgeLength > 0,
            "bridge scenario must classify bridge construction");
        assertEquals(0, edge.fillVolume,
            "bridge span must not accumulate open corridor fill volume");
    }

    private static void assertZeroFillUnderDeck(RoadGenerationResult edge, int minX, int maxX) {
        int subgradeInValley = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX) {
                continue;
            }
            if (entry.getValue() == RoadSolidLayer.SUBGRADE) {
                subgradeInValley++;
            }
        }
        assertEquals(0, subgradeInValley, "bridge deck must not have subgrade fill below");
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
