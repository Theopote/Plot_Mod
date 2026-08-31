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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        assertTrue(result.isSuccess());
        assertEquals("plugin.road.horizontal_alignment_materialize_partial", result.detailMessageKey());
        assertEquals(0.0, shared.getPosition().y, 1e-6);
        assertEquals(5.0, endA.getPosition().y, 0.1);
        assertEquals(5.0, roadA.getHorizontalAlignment().getOrigin().y, 1e-6);
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
}
