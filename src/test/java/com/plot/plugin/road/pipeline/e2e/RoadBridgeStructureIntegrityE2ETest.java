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
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bridge spans must keep continuous deck surface and generated BRIDGE members under the pavement —
 * not only avoid open corridor fill.
 */
class RoadBridgeStructureIntegrityE2ETest {

    @Test
    void r08BridgeDeckHasContinuousRoadAndGeneratedSlab() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r08Bridge());
        assertTrue(edge.constructionTypes.contains(RoadConstructionType.BRIDGE) || edge.bridgeLength > 0);

        int roadInValley = countLayerInXBand(edge, RoadSolidLayer.ROAD, 10, 20);
        int bridgeInValley = countLayerInXBand(edge, RoadSolidLayer.BRIDGE, 10, 20);
        assertTrue(roadInValley >= 5, "continuous ROAD deck across valley, count=" + roadInValley);
        assertTrue(bridgeInValley >= 5,
            "bridge span must emit BRIDGE deck/pier members, count=" + bridgeInValley);

        assertEquals(0, edge.unsupportedPavementCount,
            "BRIDGE columns must keep generated slab under ROAD, not rely on valley terrain");
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertTrue(maxCenterlineRoadStep(edge, 8, 22) <= 1,
            "flat valley deck centerline must not cliff");
        assertTrue(maxCenterlineRoadStep(edge, 2, 28) <= 1,
            "road↔bridge approach seam must stay within 1 block");
    }

    @Test
    void r08BridgeDeckColumnsHaveSupportImmediatelyBelowPavement() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r08Bridge());
        int floatingDeck = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (pos.getX() < 10 || pos.getX() > 20 || Math.abs(pos.getZ()) > 3) {
                continue;
            }
            BlockPos below = pos.down();
            RoadSolidLayer belowLayer = edge.placementLayers.get(below);
            var belowRecord = edge.placementRecords.get(below);
            boolean structural = belowLayer == RoadSolidLayer.BRIDGE
                || belowLayer == RoadSolidLayer.SUBGRADE
                || (belowRecord != null && !"minecraft:air".equals(belowRecord.newBlockId));
            if (!structural) {
                floatingDeck++;
            }
        }
        assertEquals(0, floatingDeck,
            "every valley ROAD cell must sit on a generated non-air member");
    }

    private static int countLayerInXBand(
            RoadGenerationResult edge,
            RoadSolidLayer layer,
            int minX,
            int maxX) {
        int count = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() >= minX && pos.getX() <= maxX && entry.getValue() == layer) {
                count++;
            }
        }
        return count;
    }

    private static int maxCenterlineRoadStep(RoadGenerationResult edge, int minX, int maxX) {
        TreeMap<Integer, Integer> yByX = new TreeMap<>();
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX || Math.abs(pos.getZ()) > 1) {
                continue;
            }
            yByX.merge(pos.getX(), pos.getY(), Math::max);
        }
        int maxStep = 0;
        Integer prevY = null;
        for (Integer y : yByX.values()) {
            if (prevY != null) {
                maxStep = Math.max(maxStep, Math.abs(y - prevY));
            }
            prevY = y;
        }
        return maxStep;
    }

    private static RoadGenerationResult preview(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadBenchmarkHarness.generatePreview(
            scenario.network(), generator, scenario.terrain())
            .edgeResults().values().iterator().next();
    }
}
