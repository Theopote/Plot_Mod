package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkInvariantValidatorTest {

    @Test
    void validNetworkPassesValidation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void detectsDuplicateRoadSegmentClaims() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());

        roadB.addSegment(edge.getId());

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.contains("claimed by roads")));
    }

    @Test
    void detectsMissingNodeOnEdge() {
        RoadNetwork network = new RoadNetwork();
        RoadEdge edge = new RoadEdge("edge-1", "missing-start", "missing-end", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), null, null);
        network.getEdges(); // force class load
        // Direct map mutation is only for testing broken states.
        injectEdge(network, edge);

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.contains("missing start node")));
    }

    @Test
    void detectsInvalidSlopeOverrideRange() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = new RoadEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)));
        edge.setSlopeOverrides(List.of(new RoadEdge.SlopeOverride(5.0, 3.0, 4.0f)));
        injectEdgeWithTopology(network, edge, start, end);

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.contains("endDistance < startDistance")));
    }

    @Test
    void detectsEdgeStartGeometryMismatch() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = new RoadEdge(start.getId(), end.getId(), List.of(
            new Vec2d(1.5, 0), new Vec2d(10, 0)));
        injectEdgeWithTopology(network, edge, start, end);

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.startsWith(RoadNetworkViolationKind.EDGE_START_GEOMETRY_MISMATCH.name())));
        assertTrue(RoadNetworkInvariantValidator.collectGeometryMismatchEdgeIds(network).contains(edge.getId()));
    }

    @Test
    void detectsEdgeEndGeometryMismatch() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = new RoadEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(8.5, 0.5)));
        injectEdgeWithTopology(network, edge, start, end);

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.startsWith(RoadNetworkViolationKind.EDGE_END_GEOMETRY_MISMATCH.name())));
        assertTrue(RoadNetworkInvariantValidator.collectGeometryMismatchEdgeIds(network).contains(edge.getId()));
    }

    @Test
    void materializedSharedJunctionSatisfiesGeometryEndpointInvariant() {
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

        var alignment = new com.plot.plugin.road.alignment.RoadHorizontalAlignment(
            new Vec2d(100, 100.5), 0.0, List.of());
        alignment.addElement(com.plot.plugin.road.alignment.HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer.materialize(network, roadA);

        RoadNetworkValidationResult result = RoadNetworkInvariantValidator.validate(network);
        assertTrue(result.isValid(), () -> String.join("; ", result.violations()));
        assertTrue(edgeA.getCenterlinePoints().getFirst().distance(junction.getPosition()) < 1e-6);
    }

    private static void injectEdge(RoadNetwork network, RoadEdge edge) {
        try {
            var field = RoadNetwork.class.getDeclaredField("edges");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var edges = (java.util.Map<String, RoadEdge>) field.get(network);
            edges.put(edge.getId(), edge);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void injectEdgeWithTopology(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode start,
            RoadNode end) {
        injectEdge(network, edge);
        start.addEdge(edge.getId());
        end.addEdge(edge.getId());
    }
}
