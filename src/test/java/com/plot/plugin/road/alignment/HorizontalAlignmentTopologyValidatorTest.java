package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentTopologyValidatorTest {

    @Test
    void alignedSingleSegmentRoadPasses() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        assertTrue(HorizontalAlignmentTopologyValidator.isAligned(network, road));
        assertTrue(HorizontalAlignmentTopologyValidator.validate(network, road).isEmpty());
    }

    @Test
    void detectsChainEntryMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        List<HorizontalAlignmentViolation> violations =
            HorizontalAlignmentTopologyValidator.validate(network, road);

        assertFalse(violations.isEmpty());
        assertEquals(HorizontalAlignmentViolationKind.ALIGNMENT_TOPOLOGY_MISMATCH, violations.getFirst().kind());
        assertEquals(start.getId(), violations.getFirst().nodeId());
        assertEquals(0.0, violations.getFirst().chainageMeters(), 1e-6);
        assertEquals(5.0, violations.getFirst().deviationMeters(), 1e-6);
    }

    @Test
    void detectsInternalJunctionMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        n2.setPosition(new Vec2d(50, 4));

        List<HorizontalAlignmentViolation> violations =
            HorizontalAlignmentTopologyValidator.validate(network, road);

        assertEquals(1, violations.size());
        assertEquals(n2.getId(), violations.getFirst().nodeId());
        assertEquals(50.0, violations.getFirst().chainageMeters(), 1e-6);
        assertTrue(violations.getFirst().deviationMeters() > 3.9);
    }

    @Test
    void detectsChainExitMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(90.0));
        road.setHorizontalAlignment(alignment);

        List<HorizontalAlignmentViolation> violations =
            HorizontalAlignmentTopologyValidator.validate(network, road);

        assertEquals(1, violations.size());
        assertEquals(end.getId(), violations.getFirst().nodeId());
        assertEquals(90.0, violations.getFirst().chainageMeters(), 1e-6);
    }
}
