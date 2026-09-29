package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileControlPointsTest {
    @Test void projectsOnlyPvisInsideSelectedEdgeAndReportsGrades() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70))));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(50, 0));
        var c = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        var tail = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        List<VerticalProfileControlPoints.ControlPoint> points =
            VerticalProfileControlPoints.forEdge(network, road, tail);
        assertEquals(2, points.size());
        assertEquals(1, points.getFirst().pviIndex());
        assertEquals(0.0, points.getFirst().localDistance(), 1e-6);
        assertEquals(-10.0, points.getFirst().rightGradePercent(), 1e-6);
        assertEquals(50.0, points.getLast().localDistance(), 1e-6);
    }

    @Test void elevationEditPreservesStationAndVerticalCurve() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70)));
        RoadVerticalAlignment edited = VerticalProfileControlPoints.withElevation(source, 1, 78);
        assertEquals(75, source.getPvis().get(1).getElevation(), 1e-6);
        assertEquals(78, edited.getPvis().get(1).getElevation(), 1e-6);
        assertEquals(50, edited.getPvis().get(1).getStation(), 1e-6);
        assertEquals(12, edited.getPvis().get(1).getCurveLength(), 1e-6);
    }

    @Test void moveClampsMiddlePviAndKeepsEndpointsFixed() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70)));
        RoadVerticalAlignment middle = VerticalProfileControlPoints.move(source, 1, 95, 78, 100);
        assertEquals(88, middle.getPvis().get(1).getStation(), 1e-6);
        assertEquals(78, middle.getPvis().get(1).getElevation(), 1e-6);
        assertEquals(12, middle.getPvis().get(1).getCurveLength(), 1e-6);

        RoadVerticalAlignment endpoint = VerticalProfileControlPoints.move(source, 0, 30, 72, 100);
        assertEquals(0, endpoint.getPvis().getFirst().getStation(), 1e-6);
        assertEquals(72, endpoint.getPvis().getFirst().getElevation(), 1e-6);
    }

    @Test void flatRoadProducesNoEditableControlPoints() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setVerticalAlignment(VerticalProfileDesignRules.flatAlignment(50, 70));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(50, 0));
        var edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());

        assertTrue(VerticalProfileControlPoints.forEdge(network, road, edge).isEmpty());
    }

    @Test void simpleEndpointPviIsEditable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(100, 72))));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        var edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        List<VerticalProfileControlPoints.ControlPoint> points =
            VerticalProfileControlPoints.forEdge(network, road, edge);
        assertEquals(2, points.size());
        for (VerticalProfileControlPoints.ControlPoint point : points) {
            assertTrue(point.elevationEditable());
            assertTrue(VerticalProfileControlPoints.isEditablePvi(network, road, point));
        }
    }

    @Test void junctionFixedMiddlePviIsNotEditable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            new PointOfVerticalIntersection(
                50, 75, null, VerticalControlPointConstraint.JUNCTION_FIXED),
            PointOfVerticalIntersection.of(100, 70))));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        var edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        VerticalProfileControlPoints.ControlPoint middle =
            VerticalProfileControlPoints.forEdge(network, road, edge).stream()
                .filter(point -> point.pviIndex() == 1)
                .findFirst()
                .orElseThrow();
        assertFalse(VerticalProfileControlPoints.isEditablePvi(network, road, middle));
        assertFalse(VerticalProfileControlPoints.canAutoSmooth(network, road, middle));
    }

    @Test void bootstrapOrInsertCreatesThreePointProfileWhenAlignmentMissing() {
        RoadVerticalAlignment created = VerticalProfileControlPoints.bootstrapOrInsert(
            null, 100, 70, 72, 40, 75);
        assertEquals(3, created.pviCount());
        assertEquals(40, created.getPvis().get(1).getStation(), 1e-6);
        assertEquals(75, created.getPvis().get(1).getElevation(), 1e-6);
    }

    @Test void insertAndRemoveMiddlePviPreservesEndpoints() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70)));
        RoadVerticalAlignment inserted = VerticalProfileControlPoints.insertAt(source, 30, 73, 100);
        assertEquals(4, inserted.pviCount());
        RoadVerticalAlignment removed = VerticalProfileControlPoints.removeAt(inserted, 2);
        assertEquals(3, removed.pviCount());
        assertEquals(30, removed.getPvis().get(1).getStation(), 1e-6);
    }

    @Test void withCurveLengthClampsToNeighborSpacing() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70)));
        RoadVerticalAlignment edited = VerticalProfileControlPoints.withCurveLength(source, 1, 40);
        assertEquals(40, edited.getPvis().get(1).getCurveLength(), 1e-6);
    }

    @Test void movingEitherEndpointOfShortRoadKeepsWholeProfileFlat() {
        RoadVerticalAlignment source = VerticalProfileDesignRules.flatAlignment(18, 70);
        RoadVerticalAlignment moved = VerticalProfileControlPoints.move(source, 1, 18, 74, 18);
        assertEquals(74, moved.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(74, moved.getPvis().getLast().getElevation(), 1e-6);
    }
}
