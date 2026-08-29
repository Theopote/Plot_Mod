package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadSegmentOrderingTest {

    @Test
    void ordersLinearChainRegardlessOfInsertionOrder() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        road.reorderSegments(List.of(edge2.getId(), edge1.getId()));

        assertEquals(
            List.of(edge1.getId(), edge2.getId()),
            RoadSegmentOrdering.orderedSegmentIds(network, road));
    }

    @Test
    void getOrderedSegmentIdsReflectsStorageOrder() {
        Road road = new Road("road-a");
        road.addSegment("e3");
        road.addSegment("e1");
        road.addSegment("e2");
        assertEquals(List.of("e3", "e1", "e2"), road.getOrderedSegmentIds());
        road.reorderSegments(List.of("e1", "e2", "e3"));
        assertEquals(List.of("e1", "e2", "e3"), road.getOrderedSegmentIds());
    }

    @Test
    void applyTopologicalOrderSyncsRoadStorage() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        road.reorderSegments(List.of(edge2.getId(), edge1.getId()));

        RoadSegmentOrdering.applyTopologicalOrder(network, road);

        assertEquals(List.of(edge1.getId(), edge2.getId()), road.getOrderedSegmentIds());
    }
}
