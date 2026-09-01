package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        RoadEdge edgeA = network.getEdge(roadA.getOrderedSegmentIds().getFirst());

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertTrue(result.isSuccess());
        assertEquals("plugin.road.horizontal_alignment_materialize_partial", result.detailMessageKey());
        assertEquals(0.0, shared.getPosition().y, 1e-6);
        assertEquals(5.0, endA.getPosition().y, 0.1);
        assertTrue(edgeA.getCenterlinePoints().getFirst().distance(shared.getPosition()) < 1e-6);
        assertTrue(roadA.getHorizontalAlignment().getOrigin().distance(shared.getPosition()) < 1e-6);
    }

    @Test
    void materializeSnapsSharedJunctionEndpointToNodePosition() {
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

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertTrue(result.isSuccess());
        Vec2d junctionPosition = junction.getPosition();
        assertEquals(100.0, junctionPosition.x, 1e-6);
        assertEquals(100.0, junctionPosition.y, 1e-6);
        assertTrue(edgeA.getCenterlinePoints().getFirst().distance(junctionPosition) < 1e-6);
        assertTrue(edgeA.getCenterlinePoints().getLast().distance(endA.getPosition()) < 0.1);
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
}
