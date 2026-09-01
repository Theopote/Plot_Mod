package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStationingCanonicalLengthTest {

    private static final double DESIGN_LENGTH = 300.0;
    private static final double INSTANCE_LENGTH = 299.3;

    @Test
    void canonicalLengthUsesDesignAlignmentWhenPresent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("canonical");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(INSTANCE_LENGTH, 0));
        network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(INSTANCE_LENGTH, 0)),
            road.getId());

        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(DESIGN_LENGTH))));

        assertEquals(DESIGN_LENGTH, RoadStationing.designLength(network, road), 1e-6);
        assertEquals(DESIGN_LENGTH, RoadStationing.canonicalLength(network, road), 1e-6);
        assertEquals(INSTANCE_LENGTH, RoadStationing.instanceLength(network, road), 1e-6);
    }

    @Test
    void isValidAcceptsCanonicalRangeWhenInstancePolylineIsShorter() {
        RoadNetwork network = buildMismatchedRoad();

        assertTrue(RoadStationing.isValid(network, new RoadStation("canonical", 0.0)));
        assertTrue(RoadStationing.isValid(network, new RoadStation("canonical", DESIGN_LENGTH)));
        assertTrue(RoadStationing.isValid(network, new RoadStation("canonical", 299.7)));

        assertFalse(RoadStationing.isValid(network, new RoadStation("canonical", 300.3)));
        assertFalse(RoadStationing.isValid(network, new RoadStation("canonical", -1.0)));
    }

    @Test
    void isValidRejectsBeyondDesignLengthWhenInstancePolylineIsLonger() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("long-instance");
        double instanceLength = 300.5;
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(instanceLength, 0));
        network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instanceLength, 0)),
            road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(DESIGN_LENGTH))));

        assertTrue(RoadStationing.isValid(network, new RoadStation("long-instance", 299.7)));
        assertFalse(RoadStationing.isValid(network, new RoadStation("long-instance", 300.3)));
        assertEquals(DESIGN_LENGTH, RoadStationing.designLength(network, road), 1e-6);
        assertEquals(DESIGN_LENGTH, RoadStationing.canonicalLength(network, road), 1e-6);
        assertEquals(instanceLength, RoadStationing.instanceLength(network, road), 1e-6);
    }

    @Test
    void resolveAndDesignPoseAgreeOnCanonicalRange() {
        RoadNetwork network = buildMismatchedRoad();
        Road road = network.getRoad("canonical");

        assertTrue(HorizontalAlignmentGeometry.poseAt(road.getHorizontalAlignment(), 299.7).isPresent());
        assertTrue(RoadStationing.resolve(network, road, 299.7).isPresent());
        assertTrue(RoadPlanGeometry.pointAtStation(network, road, 299.7).isPresent());

        assertFalse(HorizontalAlignmentGeometry.poseAt(road.getHorizontalAlignment(), 300.3).isPresent());
        assertFalse(RoadStationing.resolve(network, road, 300.3).isPresent());
    }

    @Test
    void exitNodeReportsCanonicalEndStation() {
        RoadNetwork network = buildMismatchedRoad();
        Road road = network.getRoad("canonical");
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        String exitNodeId = RoadStationing.chainExitNodeId(network, road).orElseThrow();

        double exitStation = RoadStationing.stationAtNode(network, road, edge, exitNodeId).orElseThrow();
        assertEquals(DESIGN_LENGTH, exitStation, 1e-6);
    }

    @Test
    void canonicalLengthFallsBackToInstanceWhenNoDesignAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("polyline");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(60, 0)),
            road.getId());

        assertEquals(0.0, RoadStationing.designLength(network, road), 1e-6);
        assertEquals(60.0, RoadStationing.instanceLength(network, road), 1e-6);
        assertEquals(60.0, RoadStationing.canonicalLength(network, road), 1e-6);
    }

    @Test
    void chainageConversionIsIdentityWhenLengthsMatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("aligned");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        assertEquals(0.0, RoadStationing.designLength(network, road), 1e-6);
        assertEquals(100.0, RoadStationing.canonicalLength(network, road), 1e-6);
        assertEquals(100.0, RoadStationing.instanceLength(network, road), 1e-6);
        assertEquals(50.0, RoadStationing.toCanonicalChainage(network, road, 50.0), 1e-6);
        assertEquals(50.0, RoadStationing.toInstanceChainage(network, road, 50.0), 1e-6);
    }

    private static RoadNetwork buildMismatchedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("canonical");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(INSTANCE_LENGTH, 0));
        network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(INSTANCE_LENGTH, 0)),
            road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(DESIGN_LENGTH))));
        return network;
    }
}
