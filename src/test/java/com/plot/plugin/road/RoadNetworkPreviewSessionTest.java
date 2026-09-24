package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkPreviewSessionTest {

    private RoadNetworkGenerator generator;
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        generator = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        RoadNode c = network.createNode(new Vec2d(20, 20));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(20, 0), new Vec2d(20, 20)), road.getId());
    }

    @Test
    void incrementalSessionMatchesSyncGenerateAll() {
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetworkGenerator.NetworkGenerationResult sync = generator.generateAll(network, terrain);

        RoadNetworkPreviewSession session = generator.beginPreviewSession(network, terrain);
        assertTrue(session.isValid());
        generator.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            generator.tickPreviewSessionEdges(session, 1);
        }
        while (session.hasMoreJunctions()) {
            generator.tickPreviewSessionJunctions(session, 1);
        }
        RoadNetworkGenerator.PreviewResult incremental = generator.completePreviewSession(session);

        assertEquals(sync.totalEdgeCount(), incremental.networkResult().totalEdgeCount());
        assertEquals(sync.successEdgeCount(), incremental.networkResult().successEdgeCount());
        assertFalse(incremental.aggregate().placementRecords.isEmpty());
    }
}
