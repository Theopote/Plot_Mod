package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEdgeListHelperPathRowsTest {

    @Test
    void pathDisplayRowsCollapseSegmentsIntoSingleRoadEntry() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("Main");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(50, 0));
        var c = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        List<RoadEdge> allEdges = List.copyOf(network.getEdges().values());
        List<RoadEdgeListHelper.DisplayRow> rows =
            RoadEdgeListHelper.buildPathDisplayRows(network, allEdges);

        assertEquals(1, rows.size());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.SINGLE_ROAD, rows.getFirst().kind());
        assertEquals(2, rows.getFirst().group().edges().size());
    }

    @Test
    void pathDisplayRowsListEachLogicalRoadOnceWhenFilterMatchesAnySegment() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("A");
        Road roadB = network.createRoad("B");
        var n0 = network.createNode(new Vec2d(0, 0));
        var n1 = network.createNode(new Vec2d(50, 0));
        var n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge a1 = network.createEdge(
            n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), roadA.getId());
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            n0.getId(), n2.getId(), List.of(new Vec2d(0, 10), new Vec2d(100, 10)), roadB.getId());

        List<RoadEdgeListHelper.DisplayRow> rows =
            RoadEdgeListHelper.buildPathDisplayRows(network, List.of(a1));

        assertEquals(1, rows.size());
        assertEquals(roadA.getId(), rows.getFirst().group().roadId());
        assertTrue(rows.getFirst().group().edges().size() >= 2);
    }
}
