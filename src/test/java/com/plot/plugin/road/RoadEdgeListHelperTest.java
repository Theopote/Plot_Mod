package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadEdgeListHelperTest {

    @Test
    void sortByLengthDescending() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));

        RoadEdge shortEdge = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(5, 0)));
        RoadEdge longEdge = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(30, 0)));

        List<RoadEdge> sorted = RoadEdgeListHelper.filterAndSort(
            network,
            List.of(shortEdge, longEdge),
            "",
            RoadEdgeListHelper.SortMode.LENGTH_DESC,
            new RoadEdgeListHelper.CoordFilter(false, 0, 0, 0, 0));

        assertEquals(longEdge.getId(), sorted.getFirst().getId());
        assertEquals(shortEdge.getId(), sorted.get(1).getId());
    }

    @Test
    void filterBySearchText() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(100, 200));
        RoadNode b = network.createNode(new Vec2d(300, 400));
        RoadEdge edge = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(100, 200), new Vec2d(300, 400)));

        List<RoadEdge> filtered = RoadEdgeListHelper.filterAndSort(
            network,
            List.of(edge),
            "100,200",
            RoadEdgeListHelper.SortMode.INSERTION,
            new RoadEdgeListHelper.CoordFilter(false, 0, 0, 0, 0));

        assertEquals(1, filtered.size());
    }
}
