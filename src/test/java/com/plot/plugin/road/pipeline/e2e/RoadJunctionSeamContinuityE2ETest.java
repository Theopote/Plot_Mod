package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadPlacementSeamAssertions;
import com.plot.plugin.road.RoadPlacementSeamAssertions.StationPavement;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最终 aggregate.placementRecords 接缝验收：路口、曲线、变宽、桥头。
 */
class RoadJunctionSeamContinuityE2ETest {

    @Test
    void crossJunctionFinalAggregateSeamsMeetPlaza() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r04CrossJunction();
        RoadNetworkGenerator.PreviewResult preview = generate(scenario);
        RoadGenerationResult aggregate = preview.aggregate();
        assertTrue(aggregate.placementRecords.size() > 50, "aggregate must include merged placements");

        RoadNode plaza = scenario.network().getNodes().values().stream()
            .filter(n -> Math.hypot(n.getPosition().x, n.getPosition().y) < 1e-6)
            .findFirst()
            .orElseThrow();
        Integer plazaY = preview.nodeElevations().get(plaza.getId());
        assertTrue(plazaY != null);

        RoadPlacementSeamAssertions.assertJunctionApproachSeams(
            scenario.network(),
            aggregate,
            plaza.getPosition(),
            plazaY,
            2.0,
            10.0,
            1);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
    }

    @Test
    void curvedRoadStationSamplesStayContinuous() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r02Curved();
        RoadNetworkGenerator.PreviewResult preview = generate(scenario);
        RoadGenerationResult aggregate = preview.aggregate();
        RoadEdge edge = scenario.network().getEdges().values().iterator().next();

        List<StationPavement> samples = RoadPlacementSeamAssertions.sampleAlongEdgeCenterline(
            scenario.network(), edge, aggregate, 1.0);
        assertTrue(samples.size() >= 10, "curve must densify along centerline stations");
        RoadPlacementSeamAssertions.assertLongitudinalContinuity(samples, 1, "R02 curve");
        RoadPlacementSeamAssertions.assertNoClearedPavement(samples, "R02 curve");
        assertEquals(0, aggregate.roadShoulderSeamGapCount);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
    }

    @Test
    void variableWidthTransitionWidensAlongStations() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("var-width");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setIncludeShoulder(true);
        road.getCrossSection().getCarriageway().setWidth(5);
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(11);
        // STEP：桩号 ≥40 起切换到宽断面，便于前后半宽对比（不必依赖 SMOOTH 双桩号插值）。
        road.setVariableCrossSections(new RoadVariableCrossSections(
            List.of(StationCrossSection.at(40.0, wide)),
            VariableCrossSectionTransitionMode.STEP));

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(80, 0));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("var-width");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(false);

        RoadNetworkGenerator.PreviewResult preview = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generatePreview(network, new FlatTerrainSampler(64));
        RoadGenerationResult aggregate = preview.aggregate();

        List<StationPavement> samples = RoadPlacementSeamAssertions.sampleAlongEdgeCenterline(
            network, edge, aggregate, 2.0);
        RoadPlacementSeamAssertions.assertLongitudinalContinuity(samples, 1, "variable-width");
        RoadPlacementSeamAssertions.assertVariableWidthWidensAlongStations(
            aggregate, samples, 35.0, 50.0);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
    }

    @Test
    void bridgeHeadUsesRealOpenBridgeTransition() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r08Bridge();
        RoadNetworkGenerator.PreviewResult preview = generate(scenario);
        RoadGenerationResult aggregate = preview.aggregate();
        RoadEdge edge = scenario.network().getEdges().values().iterator().next();

        List<StationPavement> samples = RoadPlacementSeamAssertions.sampleAlongEdgeCenterline(
            scenario.network(), edge, aggregate, 1.0);
        assertTrue(samples.stream().anyMatch(StationPavement::hasBridgeMember),
            "R08 must emit BRIDGE members along centerline");
        RoadPlacementSeamAssertions.assertBridgeHeadSeams(samples, 2, 1);
        assertEquals(0, aggregate.unsupportedPavementCount);
        assertEquals(0, aggregate.structureInteriorSlopeCellCount);
    }

    private static RoadNetworkGenerator.PreviewResult generate(RoadGoldenScenario scenario) {
        return new RoadNetworkGenerator(new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()))
            .generatePreview(scenario.network(), scenario.terrain());
    }
}
