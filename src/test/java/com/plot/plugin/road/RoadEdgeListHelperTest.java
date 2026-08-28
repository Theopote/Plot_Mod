package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void formatRoadLabelPrefersCustomName() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        road.setName("Residential Loop");

        assertEquals("Residential Loop", RoadEdgeListHelper.formatRoadLabel(network, road));
    }

    @Test
    void setNameNormalizesBlankAndTrim() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();

        road.setName("  Main Street  ");
        assertEquals("Main Street", road.getName());
        assertEquals("Main Street", RoadEdgeListHelper.formatRoadLabel(network, road));

        road.setName("   ");
        assertNull(road.getName());
        assertEquals(
            RoadEdgeListHelper.formatAutoRoadLabel(network, road),
            RoadEdgeListHelper.formatRoadLabel(network, road));
    }

    @Test
    void buildDisplayRowsFlatMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadEdge e1 = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        List<RoadEdgeListHelper.DisplayRow> rows = RoadEdgeListHelper.buildDisplayRows(
            network,
            List.of(e1, e2),
            RoadEdgeListHelper.SortMode.LENGTH_ASC,
            Set.of(),
            false);

        assertEquals(2, rows.size());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.FLAT, rows.get(0).kind());
        assertEquals(e1.getId(), rows.get(0).edge().getId());
        assertEquals(0, rows.get(0).segmentIndex());
        assertEquals(e2.getId(), rows.get(1).edge().getId());
        assertEquals(1, rows.get(1).segmentIndex());
    }

    @Test
    void buildDisplayRowsGroupedSingleSegmentWithDelete() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        road.setName("Main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        List<RoadEdgeListHelper.DisplayRow> rows = RoadEdgeListHelper.buildDisplayRows(
            network,
            List.of(edge),
            RoadEdgeListHelper.SortMode.ROAD_GROUP,
            Set.of(),
            true);

        assertEquals(2, rows.size());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.SINGLE_ROAD, rows.get(0).kind());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.SINGLE_ROAD_DELETE, rows.get(1).kind());
        assertTrue(rows.get(0).hasRoadId());
    }

    @Test
    void buildDisplayRowsGroupedMultiSegmentRespectsExpansion() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadEdge e1 = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        List<RoadEdgeListHelper.DisplayRow> collapsed = RoadEdgeListHelper.buildDisplayRows(
            network,
            List.of(e1, e2),
            RoadEdgeListHelper.SortMode.ROAD_GROUP,
            Set.of(),
            false);
        assertEquals(1, collapsed.size());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.GROUP_HEADER, collapsed.getFirst().kind());

        List<RoadEdgeListHelper.DisplayRow> expanded = RoadEdgeListHelper.buildDisplayRows(
            network,
            List.of(e1, e2),
            RoadEdgeListHelper.SortMode.ROAD_GROUP,
            Set.of(road.getId()),
            false);
        assertEquals(3, expanded.size());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.GROUP_HEADER, expanded.get(0).kind());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.GROUP_SEGMENT, expanded.get(1).kind());
        assertEquals(0, expanded.get(1).segmentIndex());
        assertEquals(e1.getId(), expanded.get(1).edge().getId());
        assertEquals(RoadEdgeListHelper.DisplayRowKind.GROUP_SEGMENT, expanded.get(2).kind());
        assertEquals(1, expanded.get(2).segmentIndex());
        assertEquals(e2.getId(), expanded.get(2).edge().getId());
    }

    @Test
    void segmentIndexReturnsOrderedPosition() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadEdge e1 = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertEquals(0, RoadEdgeListHelper.segmentIndex(network, e1));
        assertEquals(1, RoadEdgeListHelper.segmentIndex(network, e2));
        assertEquals(-1, RoadEdgeListHelper.segmentIndex(network, network.createEdge(a.getId(), c.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)))));
    }
}
