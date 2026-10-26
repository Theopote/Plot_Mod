package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCenterlineProfileStationingTest {

    private RoadNetworkManager manager;
    private RoadNetwork network;
    private Road road;
    private RoadEdge edge;
    private final AtomicReference<RoadChangeKind> lastChangeKind = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        manager.setOnNetworkChanged(lastChangeKind::set);
        network = manager.getNetwork();
        road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 66.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
    }

    @Test
    void insertPiPreservesPvisAndEditedSourceWhenLengthUnchanged() {
        CenterlineEditResult result = manager.insertPiAtLocalDistance(edge.getId(), 40.0);

        assertTrue(result.isSuccess());
        assertEquals(RoadChangeKind.GEOMETRY, lastChangeKind.get());
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        assertEquals(3, road.getVerticalAlignment().pviCount());
        assertEquals(0.0, road.getVerticalAlignment().getPvis().getFirst().getStation(), 1e-6);
        assertEquals(50.0, road.getVerticalAlignment().getPvis().get(1).getStation(), 1e-6);
        assertEquals(100.0, road.getVerticalAlignment().getPvis().get(2).getStation(), 1e-6);
        assertEquals(64.0, road.getVerticalAlignment().getPvis().getFirst().getElevation(), 1e-6);
    }

    @Test
    void splitEdgePreservesPvisButInvalidatesEditedSourceWhenTopologyChanges() {
        CenterlineEditResult result = manager.splitEdgeAtLocalDistance(edge.getId(), 40.0);

        assertTrue(result.isSuccess());
        assertEquals(RoadChangeKind.GEOMETRY, lastChangeKind.get());
        assertEquals(2, road.getOrderedSegmentIds().size());
        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
        assertEquals(3, road.getVerticalAlignment().pviCount());
        assertEquals(0.0, road.getVerticalAlignment().getPvis().getFirst().getStation(), 1e-6);
        assertEquals(50.0, road.getVerticalAlignment().getPvis().get(1).getStation(), 1e-6);
        assertEquals(100.0, road.getVerticalAlignment().getPvis().get(2).getStation(), 1e-6);
    }

    @Test
    void mergeSegmentsPreservesPvisAndEditedSourceWhenTotalLengthUnchanged() {
        Road mergeRoad = network.createRoad("merge");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)),
            mergeRoad.getId());
        network.createEdge(
            n2.getId(),
            n3.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)),
            mergeRoad.getId());
        mergeRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 66.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, mergeRoad);

        CenterlineEditResult result = manager.mergeSegmentsAtNode(n2.getId());

        assertTrue(result.isSuccess());
        assertEquals(1, mergeRoad.getOrderedSegmentIds().size());
        assertEquals(ProfileSource.EDITED, mergeRoad.getEffectiveProfileSource());
        assertEquals(3, mergeRoad.getVerticalAlignment().pviCount());
        assertEquals(50.0, mergeRoad.getVerticalAlignment().getPvis().get(1).getStation(), 1e-6);
    }

    @Test
    void filletInvalidatesEditedSourceButRetainsPvis() {
        RoadNode n1 = network.createNode(new Vec2d(0, 50));
        RoadNode n2 = network.createNode(new Vec2d(100, 100));
        Road cornerRoad = network.createRoad("corner");
        RoadEdge cornerEdge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 50), new Vec2d(50, 50), new Vec2d(50, 100)),
            cornerRoad.getId());
        cornerRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(100.0, 80.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, cornerRoad);
        int pviCount = cornerRoad.getVerticalAlignment().pviCount();

        CenterlineEditResult result = manager.filletCenterlineVertex(cornerEdge.getId(), 1, 5.0);

        assertTrue(result.isSuccess());
        assertEquals(RoadChangeKind.GEOMETRY, lastChangeKind.get());
        assertEquals(ProfileSource.AUTO, cornerRoad.getEffectiveProfileSource());
        assertEquals(pviCount, cornerRoad.getVerticalAlignment().pviCount());
    }
}
