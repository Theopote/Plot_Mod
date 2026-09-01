package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentCenterlineConsistencyTest {

    @Test
    void alignedTangentRoadIsConsistent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        HorizontalAlignmentCenterlineConsistency.Report report =
            HorizontalAlignmentCenterlineConsistency.evaluate(network, road);

        assertTrue(report.isConsistent());
        assertTrue(report.lengthMatches());
        assertTrue(report.maxDeviationMeters() < 0.1);
    }

    @Test
    void offsetAlignmentReportsDeviation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        HorizontalAlignmentCenterlineConsistency.Report report =
            HorizontalAlignmentCenterlineConsistency.evaluate(network, road);

        assertFalse(report.isConsistent());
        assertTrue(report.maxDeviationMeters() >= 4.9);
    }

    @Test
    void reversedSegmentStillSamplesCenterlineAlongChainage() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n3.getId(), n2.getId(), List.of(new Vec2d(100, 0), new Vec2d(50, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        HorizontalAlignmentCenterlineConsistency.Report report =
            HorizontalAlignmentCenterlineConsistency.evaluate(network, road);

        assertTrue(report.isConsistent());
    }

    @Test
    void lengthMismatchIsNotMaterializable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(300, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(300, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(200.0));
        road.setHorizontalAlignment(alignment);

        HorizontalAlignmentCenterlineConsistency.Report report =
            HorizontalAlignmentCenterlineConsistency.evaluate(network, road);

        assertTrue(report.evaluable());
        assertFalse(report.lengthMatches());
        assertFalse(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road));
    }

    @Test
    void pointAtStationFollowsOrientedChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n3.getId(), n2.getId(), List.of(new Vec2d(100, 0), new Vec2d(50, 0)), road.getId());

        assertTrue(RoadStationing.pointAtStation(network, road, 75.0).isPresent());
        Vec2d point = RoadStationing.pointAtStation(network, road, 75.0).orElseThrow();
        assertTrue(point.distance(new Vec2d(75, 0)) < 0.1);
        assertFalse(RoadStationing.segmentFlowsWithGeometry(network, road, tail.getId()));
    }
}
