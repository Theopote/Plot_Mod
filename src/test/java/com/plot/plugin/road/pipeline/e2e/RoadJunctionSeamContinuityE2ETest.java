package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路段↔路口高程接缝、转弯路肩连续、变宽横断面过渡的最终 placement 验收。
 */
class RoadJunctionSeamContinuityE2ETest {

    @Test
    void crossJunctionApproachesMeetPlazaWithoutOneBlockCliff() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r04CrossJunction();
        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview =
            generator.generatePreview(scenario.network(), scenario.terrain());

        RoadNode plaza = scenario.network().getNodes().values().stream()
            .filter(n -> Math.hypot(n.getPosition().x, n.getPosition().y) < 1e-6)
            .findFirst()
            .orElse(scenario.network().getNodes().values().iterator().next());
        Integer plazaY = preview.nodeElevations().get(plaza.getId());
        assertTrue(plazaY != null, "plaza elevation required");

        int minApproach = Integer.MAX_VALUE;
        int maxApproach = Integer.MIN_VALUE;
        int samples = 0;
        for (RoadGenerationResult edge : preview.edgeResults().values()) {
            for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                    continue;
                }
                if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                    continue;
                }
                double dist = Math.hypot(
                    pos.getX() - plaza.getPosition().x,
                    pos.getZ() - plaza.getPosition().y);
                if (dist < 6.0 || dist > 14.0) {
                    continue;
                }
                minApproach = Math.min(minApproach, pos.getY());
                maxApproach = Math.max(maxApproach, pos.getY());
                samples++;
            }
        }
        assertTrue(samples >= 8, "need approach ROAD samples near plaza, got " + samples);
        assertTrue(Math.abs(minApproach - plazaY) <= 1,
            "approach must meet plaza within 1 block, plaza=" + plazaY
                + " approach=" + minApproach + ".." + maxApproach);
        assertTrue(maxApproach - minApproach <= 2,
            "approach band must not cliff, " + minApproach + ".." + maxApproach);
    }

    @Test
    void curvedRoadKeepsShoulderBandAndCenterlineSteps() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r02Curved();
        RoadGenerationResult edge = preview(scenario);
        assertEquals(0, edge.roadShoulderSeamGapCount,
            "curved ROAD/SHOULDER band must not contain lateral holes");
        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(maxAdjacentCenterlineStep(edge) <= 1,
            "curved centerline ROAD must not jump more than 1 block between neighbors");
    }

    @Test
    void variableWidthTransitionKeepsShoulderSeamAndWidensCarriageway() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("var-width");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setIncludeShoulder(true);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(80, 64))));
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(10);
        road.setVariableCrossSections(new RoadVariableCrossSections(
            java.util.List.of(StationCrossSection.at(40.0, wide)),
            VariableCrossSectionTransitionMode.SMOOTH));

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(80, 0));
        network.createEdge(a.getId(), b.getId(),
            java.util.List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("var-width");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(false);

        RoadGenerationResult edge = RoadBenchmarkHarness.generatePreview(
            network,
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()),
            new FlatTerrainSampler(64))
            .edgeResults().values().iterator().next();

        assertEquals(0, edge.roadShoulderSeamGapCount,
            "variable-width transition must keep ROAD/SHOULDER band continuous");
        assertEquals(0, edge.missingRoadSurfaceCount);

        int narrowHalf = maxAbsZForRoad(edge, 10, 20);
        int wideHalf = maxAbsZForRoad(edge, 50, 70);
        assertTrue(wideHalf > narrowHalf,
            "carriageway must widen after variable section, narrowHalf=" + narrowHalf
                + " wideHalf=" + wideHalf);
    }

    @Test
    void bridgeApproachSeamStaysWithinOneBlockOfDeck() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r08Bridge());
        TreeMap<Integer, Integer> yByX = centerlineRoadY(edge);
        Integer approachY = null;
        Integer deckY = null;
        for (Map.Entry<Integer, Integer> e : yByX.entrySet()) {
            int x = e.getKey();
            if (x >= 4 && x <= 8) {
                approachY = e.getValue();
            }
            if (x >= 12 && x <= 16) {
                deckY = e.getValue();
            }
        }
        assertTrue(approachY != null && deckY != null,
            "need approach and deck samples, keys=" + yByX.keySet());
        assertTrue(Math.abs(approachY - deckY) <= 1,
            "bridge head seam must stay within 1 block, approach=" + approachY + " deck=" + deckY);
    }

    private static RoadGenerationResult preview(RoadGoldenScenario scenario) {
        return RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            new RoadGenerator(
                scenario.config(),
                IdentityCoordinateService.INSTANCE,
                BlockProjectionHandler.getInstance()),
            scenario.terrain())
            .edgeResults().values().iterator().next();
    }

    private static int maxAdjacentCenterlineStep(RoadGenerationResult edge) {
        TreeMap<Integer, Integer> yByX = centerlineRoadY(edge);
        int maxStep = 0;
        Integer prev = null;
        for (Integer y : yByX.values()) {
            if (prev != null) {
                maxStep = Math.max(maxStep, Math.abs(y - prev));
            }
            prev = y;
        }
        return maxStep;
    }

    private static TreeMap<Integer, Integer> centerlineRoadY(RoadGenerationResult edge) {
        TreeMap<Integer, Integer> yByX = new TreeMap<>();
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getZ()) > 1) {
                continue;
            }
            var record = edge.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            yByX.merge(pos.getX(), pos.getY(), Math::max);
        }
        return yByX;
    }

    private static int maxAbsZForRoad(RoadGenerationResult edge, int minX, int maxX) {
        int maxAbs = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX) {
                continue;
            }
            maxAbs = Math.max(maxAbs, Math.abs(pos.getZ()));
        }
        return maxAbs;
    }
}
