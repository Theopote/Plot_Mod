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

    @Test
    void r08BridgeOpenSpanHasNoShoulderOrSubgradeFillWalls() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r08Bridge());
        assertZeroFillUnderDeck(edge, 10, 20);
        assertEquals(0, countOpenSpanEmbankmentWalls(edge, 10, 20, 51, 62),
            "bridge open span must not emit continuous shoulder/subgrade walls under deck");
    }

    @Test
    void r10WaterCrossingOpenSpanHasNoShoulderOrSubgradeFillWalls() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r10WaterCrossing());
        assertEquals(0, countOpenSpanEmbankmentWalls(edge, 8, 22, 51, 62),
            "water crossing bridge span must not emit shoulder/subgrade walls under deck");
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

    /**
     * Counts world columns in the open span that contain a tall stack of shoulder/subgrade fill.
     * Bridge piers ({@link RoadSolidLayer#BRIDGE}) are excluded.
     */
    private static int countOpenSpanEmbankmentWalls(
            RoadGenerationResult edge,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        int walls = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = -6; z <= 6; z++) {
                if (isEmbankmentWallColumn(edge, x, z, minY, maxY)) {
                    walls++;
                }
            }
        }
        return walls;
    }

    private static boolean isEmbankmentWallColumn(
            RoadGenerationResult edge,
            int x,
            int z,
            int minY,
            int maxY) {
        int consecutive = 0;
        boolean pierColumn = false;
        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            RoadSolidLayer layer = edge.placementLayers.get(pos);
            if (layer == RoadSolidLayer.BRIDGE) {
                pierColumn = true;
            }
            if (layer == RoadSolidLayer.SHOULDER || layer == RoadSolidLayer.SUBGRADE) {
                consecutive++;
            } else {
                consecutive = 0;
            }
            if (consecutive >= 4) {
                return !pierColumn;
            }
        }
        return false;
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
