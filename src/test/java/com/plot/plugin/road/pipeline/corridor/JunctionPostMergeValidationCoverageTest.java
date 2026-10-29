package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionPostMergeValidationCoverageTest {

    @Test
    void successfulCrossJunctionHasValidationReportNotIntegrityGap() {
        RoadNetwork network = crossedRoads();
        network.registerCrossing(new RoadCrossing(
            "cross",
            "roadA",
            16.0,
            "roadB",
            16.0,
            new Vec2d(0, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            null));

        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            new RoadSystemConfig("junction-coverage"),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview =
            generator.generatePreview(network, new FlatTerrainSampler(64));

        assertFalse(preview.networkResult().getJunctionResults().isEmpty());
        assertTrue(preview.networkResult().getMissingJunctionValidationIds().isEmpty(),
            "generated junctions must not be treated as missing validation");
        assertTrue(preview.networkResult().getCrossingMaterializationFailures().isEmpty());
        assertFalse(preview.networkResult().getJunctionReports().isEmpty(),
            "post-merge validation must produce junction reports");
    }

    private static RoadNetwork crossedRoads() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        RoadNode a0 = network.createNode(new Vec2d(0, -16));
        RoadNode a1 = network.createNode(new Vec2d(0, 16));
        RoadNode b0 = network.createNode(new Vec2d(-16, 0));
        RoadNode b1 = network.createNode(new Vec2d(16, 0));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), roadA.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), roadB.getId());
        return network;
    }
}
