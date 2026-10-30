package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNodePlazaRadiusTest {

    @Test
    void plazaRadiusClampsAndDefaults() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));
        assertNull(node.getPlazaRadius());
        assertEquals(3.0, node.getEffectivePlazaRadius(3.0), 1e-9);

        node.setPlazaRadius(1.0);
        assertEquals(RoadNode.MIN_PLAZA_RADIUS, node.getPlazaRadius(), 1e-9);

        node.setPlazaRadius(99.0);
        assertEquals(RoadNode.MAX_PLAZA_RADIUS, node.getPlazaRadius(), 1e-9);

        node.setPlazaRadius(null);
        assertNull(node.getPlazaRadius());
    }

    @Test
    void fitPlazaRadiusToApproachesUsesWidestHalfWidthPlusOne() {
        RoadEdge narrow = edge("n");
        RoadEdge wide = edge("w");
        double fitted = RoadJunctionGeometry.fitPlazaRadiusToApproaches(
            List.of(narrow, wide),
            edge -> edge == narrow ? 2.5 : 4.0);
        assertEquals(5.0, fitted, 1e-9);

        assertEquals(RoadNode.MIN_PLAZA_RADIUS,
            RoadJunctionGeometry.fitPlazaRadiusToApproaches(List.of(), e -> 0.0), 1e-9);
        assertEquals(RoadNode.MAX_PLAZA_RADIUS,
            RoadJunctionGeometry.fitPlazaRadiusToApproaches(List.of(edge("x")), e -> 40.0), 1e-9);
    }

    @Test
    void resolveEffectiveJunctionRadiusHonorsPlazaOverride() {
        List<RoadEdge> edges = List.of(edge("e"));
        assertEquals(5.0,
            RoadJunctionGeometry.resolveEffectiveJunctionRadius(edges, e -> 4.0, 3.0), 1e-9);
        assertEquals(8.0,
            RoadJunctionGeometry.resolveEffectiveJunctionRadius(edges, e -> 4.0, 8.0), 1e-9);
    }

    private static RoadEdge edge(String id) {
        return new RoadEdge("a-" + id, "b-" + id, List.of(new Vec2d(0, 0), new Vec2d(1, 0)));
    }

    @Test
    void largerPlazaRadiusGrowsJunctionPolygon() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 20));
        RoadNode east = network.createNode(new Vec2d(20, 0));
        RoadNode south = network.createNode(new Vec2d(0, -20));
        Road road = network.createRoad();
        road.setWidth(5);

        List<RoadEdge> edges = new ArrayList<>();
        edges.add(network.createEdge(
            junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)), road.getId()));
        edges.add(network.createEdge(
            junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId()));
        edges.add(network.createEdge(
            south.getId(), junction.getId(),
            List.of(new Vec2d(0, -20), new Vec2d(0, 0)), road.getId()));

        var halfWidth = (java.util.function.ToDoubleFunction<RoadEdge>) e -> 2.5;
        var resolver = RoadJunctionCenterlineResolver.forNetwork(network);

        List<Vec2d> small = RoadJunctionGeometry.buildJunctionFillPolygon(
            junction.getId(),
            edges,
            halfWidth,
            junction.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS),
            0.0,
            resolver);
        junction.setPlazaRadius(10.0);
        List<Vec2d> large = RoadJunctionGeometry.buildJunctionFillPolygon(
            junction.getId(),
            edges,
            halfWidth,
            junction.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS),
            0.0,
            resolver);

        double smallSpan = maxRadialSpan(small, junction.getPosition());
        double largeSpan = maxRadialSpan(large, junction.getPosition());
        assertTrue(largeSpan > smallSpan + 1.0,
            "larger plaza radius should expand junction polygon (small=" + smallSpan + ", large=" + largeSpan + ")");
    }

    private static double maxRadialSpan(List<Vec2d> polygon, Vec2d center) {
        double max = 0.0;
        for (Vec2d p : polygon) {
            max = Math.max(max, p.distance(center));
        }
        return max;
    }
}
