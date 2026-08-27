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
