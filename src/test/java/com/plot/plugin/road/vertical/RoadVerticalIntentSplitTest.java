package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadVerticalIntentSplitTest {

    private final RoadSystemConfig config = new RoadSystemConfig("test");

    @Test
    void splitFitTerrainRoadPreservesVerticalMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);

        assertNotNull(tailId);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, network.getRoad(road.getId()).getVerticalMode());
        assertEquals(RoadVerticalMode.FIT_TERRAIN, network.getRoad(tailId).getVerticalMode());
    }

    @Test
    void splitFlatRoadPreservesFlatIntent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-road");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 70.0);
        road.getFlatVerticalIntent().setIntersectionOverride(n3.getId(), 72.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        Road tail = network.getRoad(tailId);

        assertNotNull(tail);
        assertEquals(RoadVerticalMode.FLAT, tail.getVerticalMode());
        assertNotNull(tail.getFlatVerticalIntent());
        assertEquals(70.0, tail.getFlatVerticalIntent().getBaseElevation(), 1e-6);
        assertEquals(72.0, tail.getFlatVerticalIntent().getIntersectionOverride(n3.getId()), 1e-6);
        assertNull(tail.getFlatVerticalIntent().getIntersectionOverride(n1.getId()));
    }

    @Test
    void topologyRepairPreservesTerrainAdaptiveMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        for (Road repaired : network.getRoads().values()) {
            assertEquals(RoadVerticalMode.FIT_TERRAIN, repaired.getVerticalMode());
        }
    }

    @Test
    void topologyRepairDoesNotConvertFlatToManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork-flat");
        road.setName("Flat Fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(3, network.getRoads().size());
        for (Road repaired : network.getRoads().values()) {
            assertEquals(RoadVerticalMode.FLAT, repaired.getVerticalMode());
            assertNotNull(repaired.getFlatVerticalIntent());
            assertEquals(68.0, repaired.getFlatVerticalIntent().getBaseElevation(), 1e-6);
        }
    }
}
