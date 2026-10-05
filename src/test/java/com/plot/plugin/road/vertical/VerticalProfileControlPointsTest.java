package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.vertical.VerticalAlignmentJunctionSynchronizer;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileControlPointsTest {
    @Test void forRoadIncludesAllPvisWithCanonicalStations() {
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
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        List<ProfileControlPoint> points = VerticalProfileControlPoints.forRoad(network, road);
        assertEquals(3, points.size());
        assertEquals(ProfilePointRole.START_ENDPOINT, points.getFirst().role());
        assertEquals(ProfilePointRole.INTERIOR_PVI, points.get(1).role());
        assertEquals(ProfilePointRole.END_ENDPOINT, points.getLast().role());
        assertEquals(0.0, points.getFirst().roadStation(), 1e-6);
        assertEquals(50.0, points.get(1).roadStation(), 1e-6);
        assertEquals(100.0, points.getLast().roadStation(), 1e-6);
        assertEquals(-10.0, points.get(1).rightGradePercent(), 1e-6);
    }

    @Test void forRoadReportsTangentGradesAtInteriorPvi() {
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
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        ProfileControlPoint interior = VerticalProfileControlPoints.forRoad(network, road).stream()
            .filter(point -> point.pviIndex() == 1)
            .findFirst()
            .orElseThrow();
        assertEquals(50.0, interior.roadStation(), 1e-6);
        assertEquals(-10.0, interior.rightGradePercent(), 1e-6);
        assertEquals(10.0, interior.leftGradePercent(), 1e-6);
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
        RoadVerticalAlignment middle = VerticalProfileControlPoints.move(source, 1, 60, 78, 100);
        assertEquals(60, middle.getPvis().get(1).getStation(), 1e-6);
        assertEquals(78, middle.getPvis().get(1).getElevation(), 1e-6);
        assertEquals(12, middle.getPvis().get(1).getCurveLength(), 1e-6);

        RoadVerticalAlignment nearEnd = VerticalProfileControlPoints.move(source, 1, 95, 78, 100);
        assertEquals(88, nearEnd.getPvis().get(1).getStation(), 1e-6);
        assertFalse(nearEnd.getPvis().get(1).hasCurve());

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
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());

        assertTrue(VerticalProfileControlPoints.forRoad(network, road).isEmpty());
    }

    @Test void simpleEndpointPviIsEditable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(100, 72))));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        List<ProfileControlPoint> points = VerticalProfileControlPoints.forRoad(network, road);
        assertEquals(2, points.size());
        for (ProfileControlPoint point : points) {
            assertTrue(point.elevationEditable());
            assertTrue(VerticalProfileControlPoints.isEditablePvi(network, road, point));
        }
    }

    @Test void endpointAtSharedJunctionKeepsStartRoleAndAllowsElevationEdit() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 76.0),
            PointOfVerticalIntersection.of(20.0, 78.0))));
        var junction = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(20, 0));
        var north = network.createNode(new Vec2d(0, 20));
        var south = network.createNode(new Vec2d(0, -20));
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)));
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -20)));

        VerticalAlignmentJunctionSynchronizer.synchronize(network, road);

        ProfileControlPoint start = VerticalProfileControlPoints.forRoad(network, road).getFirst();
        assertEquals(ProfilePointRole.START_ENDPOINT, start.role());
        assertTrue(start.sharedJunction());
        assertTrue(start.elevationEditable());
        assertTrue(VerticalProfileControlPoints.isEditablePvi(network, road, start));
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
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        ProfileControlPoint middle = VerticalProfileControlPoints.forRoad(network, road).stream()
            .filter(point -> point.pviIndex() == 1)
            .findFirst()
            .orElseThrow();
        assertEquals(ProfilePointRole.JUNCTION_FIXED, middle.role());
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

    @Test void bootstrapOrInsertKeepsInteriorPointAtMinimumRunBoundary() {
        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        RoadVerticalAlignment created = VerticalProfileControlPoints.bootstrapOrInsert(
            null, 24, 70, 72, minRun, 75);
        assertEquals(3, created.pviCount());
        assertEquals(minRun, created.getPvis().get(1).getStation(), 1e-6);
        assertEquals(75, created.getPvis().get(1).getElevation(), 1e-6);
    }

    @Test void withCurveLengthRespectsAdjacentCurveOverlap() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(40, 75, 30),
            PointOfVerticalIntersection.withCurve(70, 78, 20),
            PointOfVerticalIntersection.of(100, 72)));
        double maxForSecond = VerticalProfileControlPoints.maxCurveLength(source.getPvis(), 2);
        assertTrue(maxForSecond < 20, "second curve should be limited by first curve extent");
        RoadVerticalAlignment edited = VerticalProfileControlPoints.withCurveLength(source, 2, 40);
        assertTrue(edited.getPvis().get(2).getCurveLength() <= maxForSecond + 1e-6);
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

    @Test void cannotDeleteJunctionFixedPvi() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            new PointOfVerticalIntersection(
                50, 75, null, VerticalControlPointConstraint.JUNCTION_FIXED),
            PointOfVerticalIntersection.of(100, 70))));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        ProfileControlPoint middle = VerticalProfileControlPoints.forRoad(network, road).stream()
            .filter(point -> point.pviIndex() == 1)
            .findFirst()
            .orElseThrow();
        assertFalse(VerticalProfileControlPoints.canDelete(network, road, middle));
        assertThrows(IllegalArgumentException.class, () ->
            VerticalProfileControlPoints.removeAt(road.getVerticalAlignment(), 1));
    }

    @Test void cannotDeleteEndpointAtDomainLayer() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.withCurve(50, 75, 12),
            PointOfVerticalIntersection.of(100, 70)));
        assertThrows(IllegalArgumentException.class, () ->
            VerticalProfileControlPoints.removeAt(source, 0));
        assertThrows(IllegalArgumentException.class, () ->
            VerticalProfileControlPoints.removeAt(source, 2));
    }

    @Test void insertNearEndpointReturnsTooClose() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(50, 75),
            PointOfVerticalIntersection.of(100, 70)));
        assertFalse(VerticalProfileControlPoints.canInsertAt(source, 5, 100));
        VerticalProfileControlPoints.InsertResult result = VerticalProfileControlPoints.tryInsertAt(
            source, 5, 73, 100);
        assertFalse(result.success());
        assertEquals(
            VerticalProfileControlPoints.InsertFailureReason.TOO_CLOSE_TO_NEIGHBOR,
            result.reason());
        assertEquals(30, VerticalProfileControlPoints.insertAt(source, 30, 73, 100)
            .getPvis().get(1).getStation(), 1e-6);
    }

    @Test void shortRoadCannotInsertPvi() {
        double shortLength = VerticalProfileDesignRules.MIN_ROAD_LENGTH_FOR_SLOPE - 1.0;
        assertFalse(VerticalProfileControlPoints.canInsertAt(null, 10, shortLength));
        VerticalProfileControlPoints.InsertResult result = VerticalProfileControlPoints.tryBootstrapOrInsert(
            null, shortLength, 70, 72, 10, 75);
        assertFalse(result.success());
        assertEquals(
            VerticalProfileControlPoints.InsertFailureReason.ROAD_TOO_SHORT,
            result.reason());
    }

    @Test void tryInsertAtInsufficientSpaceReturnsReason() {
        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        RoadVerticalAlignment tight = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(minRun * 1.25, 75),
            PointOfVerticalIntersection.of(100, 70)));
        VerticalProfileControlPoints.InsertResult result = VerticalProfileControlPoints.tryInsertAt(
            tight, minRun * 0.625, 73, 100);
        assertFalse(result.success());
        assertEquals(
            VerticalProfileControlPoints.InsertFailureReason.INSUFFICIENT_SPACE,
            result.reason());
    }

    @Test void movingEitherEndpointOfShortRoadKeepsWholeProfileFlat() {
        RoadVerticalAlignment source = VerticalProfileDesignRules.flatAlignment(18, 70);
        RoadVerticalAlignment moved = VerticalProfileControlPoints.move(source, 1, 18, 74, 18);
        assertEquals(74, moved.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(74, moved.getPvis().getLast().getElevation(), 1e-6);
    }
}
