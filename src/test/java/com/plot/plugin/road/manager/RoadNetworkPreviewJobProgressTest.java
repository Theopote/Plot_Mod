package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkPreviewSession;
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

class RoadNetworkPreviewJobProgressTest {

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
        RoadNode b = network.createNode(new Vec2d(24, 0));
        RoadNode c = network.createNode(new Vec2d(24, 24));
        RoadNode d = network.createNode(new Vec2d(0, 24));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(24, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(24, 0), new Vec2d(24, 24)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(24, 24), new Vec2d(0, 24)), road.getId());
        network.createEdge(d.getId(), a.getId(), List.of(new Vec2d(0, 24), new Vec2d(0, 0)), road.getId());
    }

    @Test
    void aggregationAdvancesOneStepPerTick() {
        RoadNetworkPreviewSession session =
            generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        generator.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            generator.tickPreviewSessionEdges(session, Integer.MAX_VALUE);
        }
        generator.applyPreviewSessionTunnelConnectivity(session);
        while (session.hasMoreJunctions()) {
            generator.tickPreviewSessionJunctions(session, Integer.MAX_VALUE);
        }

        assertEquals(RoadNetworkPreviewSession.AggregationStep.PENDING, session.aggregationStep());
        assertFalse(generator.tickPreviewSessionAggregation(session));
        assertEquals(RoadNetworkPreviewSession.AggregationStep.MERGE, session.aggregationStep());
        assertEquals(1, session.processedAggregationWorkUnits());

        assertFalse(generator.tickPreviewSessionAggregation(session));
        assertEquals(RoadNetworkPreviewSession.AggregationStep.VALIDATE, session.aggregationStep());
        assertEquals(2, session.processedAggregationWorkUnits());

        assertTrue(generator.tickPreviewSessionAggregation(session));
        assertEquals(RoadNetworkPreviewSession.AggregationStep.DONE, session.aggregationStep());
        assertEquals(3, session.processedAggregationWorkUnits());
        assertFalse(session.completedPreview().aggregate().placementRecords.isEmpty());
    }

    @Test
    void progressStaysBelowFullUntilAggregationFinishes() {
        RoadNetworkPreviewSession session =
            generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        generator.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            generator.tickPreviewSessionEdges(session, Integer.MAX_VALUE);
        }
        generator.applyPreviewSessionTunnelConnectivity(session);
        while (session.hasMoreJunctions()) {
            generator.tickPreviewSessionJunctions(session, Integer.MAX_VALUE);
        }

        int base = RoadNetworkPreviewJob.PREPARE_WORK_UNITS
            + session.totalWorkUnits()
            + RoadNetworkPreviewJob.CONNECTIVITY_WORK_UNITS
            + session.aggregationWorkUnits()
            + 1; // reserved projecting unit when auto ghosts
        int processedBeforeAggregate = RoadNetworkPreviewJob.PREPARE_WORK_UNITS
            + session.processedWorkUnits()
            + RoadNetworkPreviewJob.CONNECTIVITY_WORK_UNITS;
        assertTrue(processedBeforeAggregate < base);
        assertTrue(processedBeforeAggregate + session.aggregationWorkUnits() <= base);
    }
}
