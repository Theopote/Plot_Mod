package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadJunctionGeometryTest {

    @Test
    void collectPolygonVerticesBuildsTJunctionOutline() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        List<RoadEdge> edges = tJunctionEdges();
        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            edges,
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        assertTrue(polygon.size() >= 6,
            "T-junction should contribute left/right corners for each connected edge");
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon),
            "junction center should be inside the filled polygon");
        assertTrue(polygon.stream().anyMatch(point -> point.y > 0),
            "north-bound edge should contribute positive-Y corners");
        assertTrue(polygon.stream().anyMatch(point -> point.x > 0),
            "east-bound edge should contribute positive-X corners");
    }

    @Test
    void collectPolygonVerticesHandlesFourDifferentWidths() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        RoadEdge north = new RoadEdge("e-n", "junction", "n-end", List.of(new Vec2d(0, 0), new Vec2d(0, 10)),
            4, null, null, null, null, null, null, null);
        RoadEdge east = new RoadEdge("e-e", "junction", "e-end", List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            8, null, null, null, null, null, null, null);
        RoadEdge south = new RoadEdge("e-s", "s-end", "junction", List.of(new Vec2d(0, -10), new Vec2d(0, 0)),
            6, null, null, null, null, null, null, null);
        RoadEdge west = new RoadEdge("e-w", "w-end", "junction", List.of(new Vec2d(-10, 0), new Vec2d(0, 0)),
            10, null, null, null, null, null, null, null);

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            List.of(north, east, south, west),
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        assertTrue(polygon.size() >= 8);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon));
        assertTrue(polygon.stream().anyMatch(point -> Math.abs(point.x) >= 5),
            "widest west road should push the polygon boundary outward");
    }

    @Test
    void collectPolygonVerticesCoversFiveWayJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        List<RoadEdge> edges = List.of(
            new RoadEdge("e-n", "hub", "n", List.of(new Vec2d(0, 0), new Vec2d(0, 12)),
                null, null, null, null, null, null, null, null),
            new RoadEdge("e-ne", "hub", "ne", List.of(new Vec2d(0, 0), new Vec2d(9, 9)),
                null, null, null, null, null, null, null, null),
            new RoadEdge("e-se", "hub", "se", List.of(new Vec2d(0, 0), new Vec2d(9, -9)),
                null, null, null, null, null, null, null, null),
            new RoadEdge("e-s", "s", "hub", List.of(new Vec2d(0, -12), new Vec2d(0, 0)),
                null, null, null, null, null, null, null, null),
            new RoadEdge("e-w", "w", "hub", List.of(new Vec2d(-12, 0), new Vec2d(0, 0)),
                null, null, null, null, null, null, null, null)
        );

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "hub",
            edges,
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        assertTrue(polygon.size() >= 8);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon),
            "five-way junction center should remain inside the generated polygon");
    }

    @Test
    void collectPolygonVerticesHandlesAcuteAngleApproaches() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        double angle = Math.toRadians(25);
        Vec2d narrowEnd = new Vec2d(Math.cos(angle) * 20, Math.sin(angle) * 20);
        Vec2d wideEnd = new Vec2d(20, 0);

        List<RoadEdge> edges = List.of(
            new RoadEdge("main", "junction", "wide-end", List.of(new Vec2d(0, 0), wideEnd),
                null, null, null, null, null, null, null, null),
            new RoadEdge("branch", "junction", "narrow-end", List.of(new Vec2d(0, 0), narrowEnd),
                null, null, null, null, null, null, null, null)
        );

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            edges,
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        assertTrue(polygon.size() >= 4);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon),
            "acute-angle junction should still cover the node center");
    }

    @Test
    void computeApproachDirectionPointsAwayFromJunctionAlongCenterline() {
        RoadEdge southbound = new RoadEdge("edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ), null, null, null, null, null, null, null, null);

        Vec2d direction = RoadJunctionGeometry.computeApproachDirection(southbound, "junction");
        assertEquals(0, direction.x, 1e-6);
        assertEquals(4, direction.y, 1e-6);

        RoadEdge northApproach = new RoadEdge("edge2", "south-end", "junction", List.of(
            new Vec2d(0, -12),
            new Vec2d(0, 0)
        ), null, null, null, null, null, null, null, null);
        Vec2d intoSouth = RoadJunctionGeometry.computeApproachDirection(northApproach, "junction");
        assertEquals(0, intoSouth.x, 1e-6);
        assertEquals(-12, intoSouth.y, 1e-6);
    }

    @Test
    void extractNearNodeSegmentWalksAwayFromJunctionAlongCenterline() {
        RoadEdge edge = new RoadEdge("edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ), null, null, null, null, null, null, null, null);

        List<Vec2d> segment = RoadJunctionGeometry.extractNearNodeSegment(
            edge, "junction", 3.0);

        assertEquals(3, segment.size());
        assertEquals(0, segment.getFirst().x, 1e-6);
        assertEquals(0, segment.getFirst().y, 1e-6);
        assertEquals(4, segment.get(1).y, 1e-6);
        assertTrue(segment.get(2).y >= 2.9 && segment.get(2).y <= 3.1);
    }

    @Test
    void collectSimpleEnvelopePointsFillsCircularArea() {
        List<Vec2d> points = RoadJunctionGeometry.collectSimpleEnvelopePoints(new Vec2d(0, 0), 2);

        assertEquals(13, points.size());
        assertTrue(points.stream().anyMatch(point -> point.x == 0 && point.y == 0));
        assertFalse(points.stream().anyMatch(point -> point.x == 3 && point.y == 0));
    }

    @Test
    void sortVerticesByPolarAngleWrapsCounterClockwise() {
        List<Vec2d> sorted = RoadJunctionGeometry.sortVerticesByPolarAngle(
            List.of(new Vec2d(1, 0), new Vec2d(0, 1), new Vec2d(-1, 0), new Vec2d(0, -1)),
            new Vec2d(0, 0)
        );

        assertEquals(4, sorted.size());
        assertTrue(sorted.getFirst().y <= -0.9, "polar sort starts at the lowest angle (-Y)");
        assertTrue(sorted.get(1).x >= 0.9, "then proceeds counter-clockwise through +X");
    }

    @Test
    void applyCornerFilletsIncreasesVertexCountForTJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        List<Vec2d> sharp = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            tJunctionEdges(),
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );
        List<Vec2d> filleted = RoadJunctionGeometry.buildJunctionFillPolygon(
            "junction",
            tJunctionEdges(),
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            2.0
        );

        assertTrue(filleted.size() > sharp.size());
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), filleted));
    }

    @Test
    void hitTestNodeFindsNearestJunction() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode far = network.createNode(new Vec2d(100, 100));

        String hit = RoadNetworkOverviewRenderer.hitTestNode(network, 0.2, 0.1, 1.0);
        assertEquals(junction.getId(), hit);
    }

    private static List<RoadEdge> tJunctionEdges() {
        RoadEdge north = new RoadEdge("e-n", "junction", "n-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 10)
        ), null, null, null, null, null, null, null, null);
        RoadEdge east = new RoadEdge("e-e", "junction", "e-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0)
        ), null, null, null, null, null, null, null, null);
        RoadEdge south = new RoadEdge("e-s", "s-end", "junction", List.of(
            new Vec2d(0, -10),
            new Vec2d(0, 0)
        ), null, null, null, null, null, null, null, null);
        return List.of(north, east, south);
    }
}
