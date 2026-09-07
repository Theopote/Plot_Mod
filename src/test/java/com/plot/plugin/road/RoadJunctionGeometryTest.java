package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
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
        RoadNetwork network = new RoadNetwork();
        List<RoadEdge> edges = tJunctionEdges(network);

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));

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
        RoadNetwork network = new RoadNetwork();

        RoadEdge north = RoadTestFixtures.geometryEdge(network, "e-n", "junction", "n-end",
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), 4);
        RoadEdge east = RoadTestFixtures.geometryEdge(network, "e-e", "junction", "e-end",
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), 8);
        RoadEdge south = RoadTestFixtures.geometryEdge(network, "e-s", "s-end", "junction",
            List.of(new Vec2d(0, -10), new Vec2d(0, 0)), 6);
        RoadEdge west = RoadTestFixtures.geometryEdge(network, "e-w", "w-end", "junction",
            List.of(new Vec2d(-10, 0), new Vec2d(0, 0)), 10);

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            List.of(north, east, south, west),
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));

        assertTrue(polygon.size() >= 8);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon));
        assertTrue(polygon.stream().anyMatch(point -> Math.abs(point.x) >= 5),
            "widest west road should push the polygon boundary outward");
    }

    @Test
    void collectPolygonVerticesCoversFiveWayJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        RoadNetwork network = new RoadNetwork();

        List<RoadEdge> edges = List.of(
            RoadTestFixtures.geometryEdge(network, "e-n", "hub", "n",
                List.of(new Vec2d(0, 0), new Vec2d(0, 12))),
            RoadTestFixtures.geometryEdge(network, "e-ne", "hub", "ne",
                List.of(new Vec2d(0, 0), new Vec2d(9, 9))),
            RoadTestFixtures.geometryEdge(network, "e-se", "hub", "se",
                List.of(new Vec2d(0, 0), new Vec2d(9, -9))),
            RoadTestFixtures.geometryEdge(network, "e-s", "s", "hub",
                List.of(new Vec2d(0, -12), new Vec2d(0, 0))),
            RoadTestFixtures.geometryEdge(network, "e-w", "w", "hub",
                List.of(new Vec2d(-12, 0), new Vec2d(0, 0)))
        );

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "hub",
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));

        assertTrue(polygon.size() >= 8);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon),
            "five-way junction center should remain inside the generated polygon");
    }

    @Test
    void collectPolygonVerticesHandlesAcuteAngleApproaches() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        RoadNetwork network = new RoadNetwork();

        double angle = Math.toRadians(25);
        Vec2d narrowEnd = new Vec2d(Math.cos(angle) * 20, Math.sin(angle) * 20);
        Vec2d wideEnd = new Vec2d(20, 0);

        List<RoadEdge> edges = List.of(
            RoadTestFixtures.geometryEdge(network, "main", "junction", "wide-end",
                List.of(new Vec2d(0, 0), wideEnd)),
            RoadTestFixtures.geometryEdge(network, "branch", "junction", "narrow-end",
                List.of(new Vec2d(0, 0), narrowEnd))
        );

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));

        assertTrue(polygon.size() >= 4);
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), polygon),
            "acute-angle junction should still cover the node center");
    }

    @Test
    void computeApproachDirectionPointsAwayFromJunctionAlongCenterline() {
        RoadNetwork network = new RoadNetwork();
        RoadEdge southbound = RoadTestFixtures.geometryEdge(network, "edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ));

        Vec2d direction = RoadJunctionGeometry.computeApproachDirection(
            southbound.getCenterlinePoints(), southbound, "junction");
        assertEquals(0, direction.x, 1e-6);
        assertEquals(4, direction.y, 1e-6);

        RoadEdge northApproach = RoadTestFixtures.geometryEdge(network, "edge2", "south-end", "junction", List.of(
            new Vec2d(0, -12),
            new Vec2d(0, 0)
        ));
        Vec2d intoSouth = RoadJunctionGeometry.computeApproachDirection(
            northApproach.getCenterlinePoints(), northApproach, "junction");
        assertEquals(0, intoSouth.x, 1e-6);
        assertEquals(-12, intoSouth.y, 1e-6);
    }

    @Test
    void extractApproachCenterlineWalksOutwardFromJunction() {
        RoadNetwork network = new RoadNetwork();
        RoadEdge edge = RoadTestFixtures.geometryEdge(network, "edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ));

        List<Vec2d> segment = RoadJunctionGeometry.extractApproachCenterline(
            edge.getCenterlinePoints(), edge, "junction", 3.0);

        assertEquals(2, segment.size());
        assertEquals(0, segment.getFirst().x, 1e-6);
        assertEquals(0, segment.getFirst().y, 1e-6);
        assertTrue(segment.get(1).y >= 2.9 && segment.get(1).y <= 3.1);
    }

    @Test
    void extractNearNodeSegmentWalksAwayFromJunctionAlongCenterline() {
        RoadNetwork network = new RoadNetwork();
        RoadEdge edge = RoadTestFixtures.geometryEdge(network, "edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ));

        List<Vec2d> segment = RoadJunctionGeometry.extractNearNodeSegment(
            edge.getCenterlinePoints(), edge, "junction", 3.0);

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
        RoadNetwork network = new RoadNetwork();
        List<RoadEdge> edges = tJunctionEdges(network);

        List<Vec2d> sharp = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));
        List<Vec2d> filleted = RoadJunctionGeometry.buildJunctionFillPolygon(
            "junction",
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            2.0,
            RoadJunctionCenterlineResolver.forNetwork(network));

        assertTrue(filleted.size() > sharp.size());
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 0), filleted));
    }

    @Test
    void junctionGenerationUsesDesignAlignmentNotStaleInstanceCenterline() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        RoadNetwork network = new RoadNetwork();

        Road road = network.createRoad("r1");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode eastEnd = network.createNode(new Vec2d(20, 0));
        RoadNode northEnd = network.createNode(new Vec2d(0, 20));

        RoadEdge east = network.createEdge(
            junction.getId(), eastEnd.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        RoadEdge north = network.createEdge(
            junction.getId(), northEnd.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)));

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 8), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(20.0));
        road.setHorizontalAlignment(alignment);

        List<RoadEdge> edges = List.of(east, north);
        var halfWidth = (java.util.function.ToDoubleFunction<RoadEdge>) edge ->
            RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0;

        List<Vec2d> staleEast = east.getCenterlinePoints();
        List<Vec2d> resolvedEast = RoadPlanGeometry.resolveEdgeCenterline(network, east);
        assertEquals(0.0, staleEast.getFirst().y, 1e-6);
        assertEquals(8.0, resolvedEast.getFirst().y, 0.2);

        Vec2d staleCenter = RoadJunctionGeometry.resolveNodeCenter(
            junction.getId(), List.of(east), RoadEdge::getCenterlinePoints);
        Vec2d resolvedCenter = RoadJunctionGeometry.resolveNodeCenter(
            junction.getId(), List.of(east), RoadJunctionCenterlineResolver.forNetwork(network));
        assertEquals(0.0, staleCenter.y, 1e-6);
        assertEquals(8.0, resolvedCenter.y, 0.2);

        List<Vec2d> stalePolygon = RoadJunctionGeometry.collectPolygonVertices(
            junction.getId(), edges, halfWidth, RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadEdge::getCenterlinePoints);
        List<Vec2d> resolvedPolygon = RoadJunctionGeometry.collectPolygonVertices(
            junction.getId(), edges, halfWidth, RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            RoadJunctionCenterlineResolver.forNetwork(network));

        assertFalse(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 8), stalePolygon),
            "stale instance centerline should anchor junction at y=0, missing design offset");
        assertTrue(RoadGeometryUtils.pointInPolygon(new Vec2d(0, 8), resolvedPolygon),
            "resolved centerline should anchor junction at design HA offset");
    }

    @Test
    void hitTestNodeFindsNearestJunction() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        network.createNode(new Vec2d(100, 100));

        String hit = RoadNetworkOverviewRenderer.hitTestNode(network, 0.2, 0.1, 1.0);
        assertEquals(junction.getId(), hit);
    }

    private static List<RoadEdge> tJunctionEdges(RoadNetwork network) {
        RoadEdge north = RoadTestFixtures.geometryEdge(network, "e-n", "junction", "n-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 10)
        ));
        RoadEdge east = RoadTestFixtures.geometryEdge(network, "e-e", "junction", "e-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0)
        ));
        RoadEdge south = RoadTestFixtures.geometryEdge(network, "e-s", "s-end", "junction", List.of(
            new Vec2d(0, -10),
            new Vec2d(0, 0)
        ));
        return List.of(north, east, south);
    }
}
