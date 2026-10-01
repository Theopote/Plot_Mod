package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentCenterlineMaterializerTest {

    @Test
    void materializeUpdatesStraightCenterlineToMatchAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        assertTrue(result.isSuccess());
        List<Vec2d> points = network.getEdge(edge.getId()).getCenterlinePoints();
        assertTrue(points.size() >= 2);
        assertEquals(5.0, points.getFirst().y, 0.1);
        assertEquals(5.0, points.getLast().y, 0.1);
        assertEquals(5.0, network.getNode(n1.getId()).getPosition().y, 0.1);
        assertEquals(5.0, network.getNode(n2.getId()).getPosition().y, 0.1);
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(network, road).isConsistent());
    }

    @Test
    void materializeHandlesReversedSegmentGeometry() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n3.getId(), n2.getId(), List.of(new Vec2d(100, 0), new Vec2d(50, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        assertTrue(result.isSuccess());
        List<Vec2d> points = network.getEdge(tail.getId()).getCenterlinePoints();
        assertTrue(points.getFirst().distance(new Vec2d(100, 0)) < 0.5);
        assertTrue(points.getLast().distance(new Vec2d(50, 0)) < 0.5);
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(network, road).isConsistent());
    }

    @Test
    void materializeSkipsSharedJunctionNodes() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(100, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 100));
        network.createEdge(shared.getId(), endA.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(shared.getId(), endB.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 100)), roadB.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0.5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        RoadEdge edgeA = network.getEdge(roadA.getOrderedSegmentIds().getFirst());

        assertTrue(HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, roadA));

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertTrue(result.isSuccess());
        assertEquals("plugin.road.horizontal_alignment_materialize_partial", result.detailMessageKey());
        assertEquals(0.0, shared.getPosition().y, 1e-6);
        assertEquals(0.5, endA.getPosition().y, 0.1);
        assertTrue(edgeA.getCenterlinePoints().getFirst().distance(shared.getPosition()) < 1e-6);
    }

    @Test
    void materializeRejectsSharedJunctionWhenHaEndpointConflicts() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(100, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 100));
        RoadEdge edgeA = network.createEdge(
            shared.getId(), endA.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(shared.getId(), endB.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 100)), roadB.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        List<Vec2d> before = List.copyOf(edgeA.getCenterlinePoints());

        assertFalse(HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, roadA));
        assertFalse(HorizontalAlignmentJunctionConsistency.findConflicts(network, roadA, 2.0).isEmpty());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertEquals(CenterlineEditStatus.JUNCTION_ENDPOINT_CONFLICT, result.status());
        assertEquals(before, edgeA.getCenterlinePoints());
    }

    @Test
    void materializeRejectsLargeSharedJunctionDeviation() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode junction = network.createNode(new Vec2d(100, 100));
        RoadNode endA = network.createNode(new Vec2d(200, 100));
        RoadNode endB = network.createNode(new Vec2d(100, 200));
        RoadEdge edgeA = network.createEdge(
            junction.getId(), endA.getId(),
            List.of(new Vec2d(100, 100), new Vec2d(200, 100)),
            roadA.getId());
        network.createEdge(
            junction.getId(), endB.getId(),
            List.of(new Vec2d(100, 100), new Vec2d(100, 200)),
            roadB.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(101.2, 100.4), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        List<Vec2d> before = List.copyOf(edgeA.getCenterlinePoints());
        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertEquals(CenterlineEditStatus.JUNCTION_ENDPOINT_CONFLICT, result.status());
        assertEquals(before, edgeA.getCenterlinePoints());
    }

    @Test
    void materializeFailsWithoutAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        assertEquals(CenterlineEditStatus.HORIZONTAL_ALIGNMENT_NOT_DEFINED, result.status());
    }

    @Test
    void canMaterializeRejectsLengthMismatchBetweenDesignAndInstance() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mismatch");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(300, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(300, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(200.0));
        road.setHorizontalAlignment(alignment);

        assertFalse(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road));
        assertFalse(HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road));
    }

    @Test
    void materializeRejectsLengthMismatchWithoutModifyingEdges() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mismatch");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(300, 0));
        RoadEdge head = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(300, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(200.0));
        road.setHorizontalAlignment(alignment);

        List<Vec2d> headBefore = List.copyOf(head.getCenterlinePoints());
        List<Vec2d> tailBefore = List.copyOf(tail.getCenterlinePoints());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        assertEquals(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID, result.status());
        assertEquals(headBefore, head.getCenterlinePoints());
        assertEquals(tailBefore, tail.getCenterlinePoints());
    }

    @Test
    void preparePhaseDoesNotMutateNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadEdge head = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 4), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        List<Vec2d> headBefore = List.copyOf(head.getCenterlinePoints());
        List<Vec2d> tailBefore = List.copyOf(tail.getCenterlinePoints());
        Vec2d n1Before = network.getNode(n1.getId()).getPosition().copy();

        Optional<HorizontalAlignmentCenterlineMaterializer.MaterializationPlan> prepared =
            HorizontalAlignmentCenterlineMaterializer.prepareMaterialization(network, road, alignment, 2.0);

        assertTrue(prepared.isPresent());
        Map<String, List<Vec2d>> planned = prepared.get().centerlinesByEdgeId();
        assertEquals(2, planned.size());
        assertTrue(planned.containsKey(head.getId()));
        assertTrue(planned.containsKey(tail.getId()));
        assertEquals(headBefore, head.getCenterlinePoints());
        assertEquals(tailBefore, tail.getCenterlinePoints());
        assertEquals(n1Before, network.getNode(n1.getId()).getPosition());

        CenterlineEditResult committed =
            HorizontalAlignmentCenterlineMaterializer.commitMaterialization(network, road, prepared.get());
        assertTrue(committed.isSuccess());
        assertNotEquals(headBefore, head.getCenterlinePoints());
        assertNotEquals(tailBefore, tail.getCenterlinePoints());
    }

    @Test
    void prepareFailureLeavesAllEdgesUntouched() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("degenerate");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(50, 0));
        RoadEdge head = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge zeroLength = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(50, 0)), road.getId());
        RoadNode n4 = network.createNode(new Vec2d(100, 0));
        RoadEdge tail = network.createEdge(
            n3.getId(), n4.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        List<Vec2d> headBefore = List.copyOf(head.getCenterlinePoints());
        List<Vec2d> zeroBefore = List.copyOf(zeroLength.getCenterlinePoints());
        List<Vec2d> tailBefore = List.copyOf(tail.getCenterlinePoints());

        assertTrue(HorizontalAlignmentCenterlineMaterializer.prepareMaterialization(network, road, alignment, 2.0).isEmpty());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
        assertEquals(CenterlineEditStatus.TOO_FEW_POINTS, result.status());
        assertEquals(headBefore, head.getCenterlinePoints());
        assertEquals(zeroBefore, zeroLength.getCenterlinePoints());
        assertEquals(tailBefore, tail.getCenterlinePoints());
    }

    @Test
    void loopHa_materializeWithInteriorSeam_preservesContinuousPhysicalEdges() {
        RoadNetwork network = buildMaterializableSquareLoopHaNetwork();
        Road road = network.getRoad("square-ha");
        Vec2d seamPosition = new Vec2d(5, 0);

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
        assertTrue(result.isSuccess());

        for (String segmentId : road.getOrderedSegmentIds()) {
            RoadEdge edge = network.getEdge(segmentId);
            assertNotNull(edge);
            assertContinuousPhysicalEdge(edge, 5.0);
        }

        List<OrientedRoadSegment> physical =
            RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        assertFalse(physical.isEmpty());
        OrientedRoadSegment firstSegment = physical.getFirst();
        OrientedRoadSegment lastSegment = physical.getLast();
        assertEquals(firstSegment.entryNodeId(), lastSegment.exitNodeId());

        RoadEdge seamEdge = network.getEdge(firstSegment.edgeId());
        RoadNode startNode = network.getNode(seamEdge.getStartNodeId());
        RoadNode endNode = network.getNode(seamEdge.getEndNodeId());
        assertEdgeEndpointsMatchNodes(seamEdge, startNode, endNode, 0.5);

        Vec2d stationZero = RoadPlanGeometry.pointAtStation(network, road, 0.0).orElseThrow();
        assertEquals(seamPosition.x, stationZero.x, 0.15);
        assertEquals(seamPosition.y, stationZero.y, 0.15);

        RoadNode loopCloseNode = network.getNode(firstSegment.entryNodeId());
        RoadEdge closingEdge = network.getEdge(lastSegment.edgeId());
        assertTrue(closingEdge.getCenterlinePoints().getLast().distance(loopCloseNode.getPosition()) < 1.0);
    }

    @Test
    void loopHa_materializeRejectsClosingEndpointMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("open-loop-ha");
        road.setTopologyMode(RoadTopologyMode.LOOP);

        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(20, 0));
        RoadEdge forward = network.createEdge(
            n0.getId(),
            n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)),
            road.getId());
        RoadEdge back = network.createEdge(
            n1.getId(),
            n0.getId(),
            List.of(new Vec2d(20, 0), new Vec2d(0, 0)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(40.0));
        road.setHorizontalAlignment(alignment);
        road.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));

        List<Vec2d> forwardBefore = List.copyOf(forward.getCenterlinePoints());
        List<Vec2d> backBefore = List.copyOf(back.getCenterlinePoints());
        Vec2d n0Before = network.getNode(n0.getId()).getPosition().copy();
        Vec2d n1Before = network.getNode(n1.getId()).getPosition().copy();

        assertTrue(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road));
        assertTrue(HorizontalAlignmentCenterlineMaterializer.prepareMaterialization(
            network, road, alignment, 2.0).isEmpty());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
        assertEquals(CenterlineEditStatus.TOO_FEW_POINTS, result.status());
        assertEquals(forwardBefore, forward.getCenterlinePoints());
        assertEquals(backBefore, back.getCenterlinePoints());
        assertEquals(n0Before, network.getNode(n0.getId()).getPosition());
        assertEquals(n1Before, network.getNode(n1.getId()).getPosition());
    }

    private static void assertContinuousPhysicalEdge(RoadEdge edge, double maxSegmentLength) {
        List<Vec2d> points = edge.getCenterlinePoints();
        assertTrue(points.size() >= 2, () -> "edge " + edge.getId() + " has too few points");
        for (int i = 0; i < points.size() - 1; i++) {
            double span = points.get(i).distance(points.get(i + 1));
            assertTrue(span <= maxSegmentLength,
                "edge " + edge.getId() + " segment " + i + " span=" + span);
        }
    }

    private static void assertEdgeEndpointsMatchNodes(
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            double tolerance) {
        List<Vec2d> points = edge.getCenterlinePoints();
        assertTrue(points.getFirst().distance(startNode.getPosition()) <= tolerance);
        assertTrue(points.getLast().distance(endNode.getPosition()) <= tolerance);
    }

    private static RoadNetwork buildMaterializableSquareLoopHaNetwork() {
        double arcLen = Math.PI * 10.0 / 2.0;
        double sideChain = 10.0 + arcLen;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT)));

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("square-ha");
        road.setHorizontalAlignment(alignment);
        road.setTopologyMode(RoadTopologyMode.LOOP);

        List<RoadNode> corners = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            double chainage = i * sideChain;
            AlignmentPose pose = HorizontalAlignmentGeometry.poseAt(alignment, chainage).orElseThrow();
            corners.add(network.createNode(new Vec2d(pose.x(), pose.y())));
        }

        List<RoadEdge> edges = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            double start = i * sideChain;
            double end = (i + 1) * sideChain;
            List<Vec2d> points = sampleAlignmentRange(alignment, start, end, 0.5);
            edges.add(network.createEdge(
                corners.get(i).getId(),
                corners.get(next).getId(),
                points,
                road.getId()));
        }

        RoadEdge seamEdge = edges.getFirst();
        road.setLoopSeam(RoadLoopSeam.onSegment(
            new Vec2d(5, 0),
            seamEdge.getId(),
            5.0 / sideChain));
        return network;
    }

    private static List<Vec2d> sampleAlignmentRange(
            RoadHorizontalAlignment alignment,
            double startChainage,
            double endChainage,
            double spacing) {
        List<Vec2d> points = new ArrayList<>();
        for (double chainage = startChainage; chainage <= endChainage + 1e-6; chainage += spacing) {
            double clamped = Math.min(chainage, endChainage);
            HorizontalAlignmentGeometry.poseAt(alignment, clamped)
                .ifPresent(pose -> points.add(new Vec2d(pose.x(), pose.y())));
        }
        HorizontalAlignmentGeometry.poseAt(alignment, endChainage)
            .ifPresent(pose -> {
                Vec2d point = new Vec2d(pose.x(), pose.y());
                if (points.isEmpty() || points.getLast().distance(point) > 1e-3) {
                    points.add(point);
                }
            });
        return List.copyOf(points);
    }
}
