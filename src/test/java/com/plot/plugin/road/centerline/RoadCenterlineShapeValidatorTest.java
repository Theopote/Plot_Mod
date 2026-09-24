package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCenterlineShapeValidatorTest {

    @Test
    void openPolylineDetectsCrossing() {
        List<Vec2d> bowTie = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10),
            new Vec2d(10, 0));

        assertTrue(PolygonValidator.hasOpenPolylineSelfIntersection(bowTie));
        assertFalse(PolygonValidator.hasOpenPolylineSelfOverlap(bowTie));
    }

    @Test
    void openPolylineDetectsCollinearOverlap() {
        List<Vec2d> backtrack = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(5, 0),
            new Vec2d(15, 0));

        assertFalse(PolygonValidator.hasOpenPolylineSelfIntersection(backtrack));
        assertTrue(PolygonValidator.hasOpenPolylineSelfOverlap(backtrack));
    }

    @Test
    void linearRoadHasNoCenterlineViolations() {
        var network = new com.plot.plugin.road.model.RoadNetwork();
        var road = network.createRoad("road-a");
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(10, 0));
        var n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertTrue(RoadCenterlineShapeValidator.validateRoad(network, road).isEmpty());
    }

    @Test
    void singleEdgeFigureEightReportsSelfIntersection() {
        var network = new com.plot.plugin.road.model.RoadNetwork();
        var road = network.createRoad("road-x");
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());

        var violations = RoadCenterlineShapeValidator.validateRoad(network, road);
        assertTrue(violations.stream().anyMatch(v ->
            v.kind() == RoadCenterlineViolationKind.SELF_INTERSECTION));
    }

    @Test
    void branchingRoadReportsNonLinearTopology() {
        var network = new com.plot.plugin.road.model.RoadNetwork();
        var road = network.createRoad("road-fork");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(10, 0));
        var c = network.createNode(new Vec2d(20, 0));
        var d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        var violations = RoadCenterlineShapeValidator.validateRoad(network, road);
        assertTrue(violations.stream().anyMatch(v ->
            v.kind() == RoadCenterlineViolationKind.NON_LINEAR_ROAD_TOPOLOGY));
    }

    @Test
    void countByKindAggregatesAcrossRoads() {
        var network = new com.plot.plugin.road.model.RoadNetwork();

        var crossing = network.createRoad("crossing");
        var c1 = network.createNode(new Vec2d(0, 0));
        var c2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            c1.getId(),
            c2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 10), new Vec2d(0, 10), new Vec2d(10, 0)),
            crossing.getId());

        var fork = network.createRoad("fork");
        var a = network.createNode(new Vec2d(100, 0));
        var b = network.createNode(new Vec2d(110, 0));
        var c = network.createNode(new Vec2d(120, 0));
        var d = network.createNode(new Vec2d(110, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), fork.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(110, 0), new Vec2d(120, 0)), fork.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(110, 0), new Vec2d(110, 10)), fork.getId());

        var counts = RoadCenterlineShapeValidator.countByKind(network);
        assertTrue(counts.get(RoadCenterlineViolationKind.SELF_INTERSECTION) >= 1);
        assertTrue(counts.get(RoadCenterlineViolationKind.NON_LINEAR_ROAD_TOPOLOGY) >= 1);
        assertEquals(2, RoadCenterlineShapeValidator.countRoadsWithViolations(network));
    }
}
