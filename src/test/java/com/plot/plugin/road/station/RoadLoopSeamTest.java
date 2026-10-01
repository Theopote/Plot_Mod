package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingDetector;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLoopSeamTest {

    @Test
    void loopSeam_defaultDeterministic() {
        RoadNetwork network = buildTriangleLoopNetwork();
        Road road = network.getRoads().values().iterator().next();
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setLoopSeam(RoadLoopSeamService.computeDefault(network, road, List.of(new Vec2d(0, 0))));

        RoadNetwork network2 = buildTriangleLoopNetwork();
        Road road2 = network2.getRoads().values().iterator().next();
        road2.setTopologyMode(RoadTopologyMode.LOOP);
        road2.setLoopSeam(RoadLoopSeamService.computeDefault(network2, road2, List.of(new Vec2d(0, 0))));

        assertEquals(road.getLoopSeam().position().x, road2.getLoopSeam().position().x, 1e-6);
        assertEquals(road.getLoopSeam().position().y, road2.getLoopSeam().position().y, 1e-6);
    }

    private static RoadNetwork buildTriangleLoopNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(5, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(5, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(5, 10), new Vec2d(0, 0)), road.getId());
        return network;
    }

    @Test
    void interiorSeamBecomesActualStationZero() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("square");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        RoadEdge edge1 = network.createEdge(n0.getId(), n1.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(
            new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(n3.getId(), n0.getId(), List.of(
            new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());

        Vec2d seamPosition = new Vec2d(5, 0);
        road.setLoopSeam(RoadLoopSeam.onSegment(seamPosition, edge1.getId(), 0.5));

        Vec2d stationZero = RoadPlanGeometry.instancePointAtStation(network, road, 0.0).orElseThrow();
        assertEquals(seamPosition.x, stationZero.x, 1e-4);
        assertEquals(seamPosition.y, stationZero.y, 1e-4);
        assertEquals(40.0, RoadStationing.canonicalLength(network, road), 1e-4);
    }

    @Test
    void rotateLoopSeamRemapsCrossingStations() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("loop");
        roadA.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(40, 0));
        network.createEdge(n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        network.createEdge(n1.getId(), n0.getId(), List.of(new Vec2d(40, 0), new Vec2d(0, 0)), roadA.getId());
        roadA.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));

        Road roadB = network.createRoad("cross");
        network.createEdge(
            network.createNode(new Vec2d(20, -10)).getId(),
            network.createNode(new Vec2d(20, 10)).getId(),
            List.of(new Vec2d(20, -10), new Vec2d(20, 10)),
            roadB.getId());

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        String crossingId = crossing.id();
        network.setCrossingGradeSeparation(crossingId, CrossingType.GRADE_SEPARATED, roadB.getId(), 6.0);

        double stationOnA = crossing.stationOn(roadA.getId());
        RoadStationDataTransforms.rotateLoopStations(network, roadA, 30.0, 80.0);

        RoadCrossing rotated = network.getCrossing(crossingId);
        assertEquals(
            RoadLoopSeamService.rotateLoopStation(stationOnA, 30.0, 80.0),
            rotated.stationOn(roadA.getId()),
            1e-4);
        assertEquals(CrossingType.GRADE_SEPARATED, rotated.type());
        assertEquals(6.0, rotated.crossingClearance(), 1e-6);
    }

    @Test
    void loopStationing_startsAtSeam() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(n2.getId(), n3.getId(), List.of(
            new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(
            new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        road.setLoopSeam(RoadLoopSeam.at(new Vec2d(10, 0)));

        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        assertEquals(e2.getId(), segments.getFirst().edgeId());
        assertEquals(n2.getId(), segments.getFirst().entryNodeId());
        Vec2d stationZero = RoadPlanGeometry.instancePointAtStation(network, road, 0.0).orElseThrow();
        assertEquals(10.0, stationZero.x, 1e-4);
        assertEquals(0.0, stationZero.y, 1e-4);
    }

    @Test
    void loopProfile_endpointsSyncElevation() {
        Road road = new Road("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(100.0, 12.0)
        )));

        RoadVerticalAlignment updated = VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), 0, 15.0, road);

        assertEquals(15.0, updated.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(15.0, updated.getPvis().getLast().getElevation(), 1e-6);
    }

    @Test
    void rotateLoopStation_wrapsCorrectly() {
        assertEquals(90.0, RoadLoopSeamService.rotateLoopStation(10.0, 20.0, 100.0), 1e-6);
        assertEquals(0.0, RoadLoopSeamService.rotateLoopStation(25.0, 25.0, 100.0), 1e-6);
    }

    @Test
    void loopHa_interiorSeam_stationZeroAtSeam() {
        RoadNetwork network = buildSquareLoopHaNetwork();
        Road road = network.getRoad("square-ha");
        Vec2d seamPosition = new Vec2d(5, 0);

        Vec2d stationZero = RoadPlanGeometry.pointAtStation(network, road, 0.0).orElseThrow();
        assertEquals(seamPosition.x, stationZero.x, 0.1);
        assertEquals(seamPosition.y, stationZero.y, 0.1);
    }

    @Test
    void loopHa_interiorSeam_roadSamplesCoverFullLoop() {
        RoadNetwork network = buildSquareLoopHaNetwork();
        Road road = network.getRoad("square-ha");

        List<PlanCenterlineSample> samples = RoadPlanGeometry.resolveRoadCenterlineSamples(network, road);
        assertFalse(samples.isEmpty());
        assertEquals(RoadPlanGeometry.canonicalLength(network, road), samples.getLast().canonicalStation(), 0.5);

        Vec2d seamPosition = new Vec2d(5, 0);
        assertEquals(seamPosition.x, samples.getFirst().position().x, 0.1);
        assertEquals(seamPosition.y, samples.getFirst().position().y, 0.1);
    }

    @Test
    void loopHa_interiorSeam_crossingOnHeadSlice_detected() {
        RoadNetwork network = buildSquareLoopHaNetwork();
        Road loopRoad = network.getRoad("square-ha");

        Road crossRoad = network.createRoad("cross");
        network.createEdge(
            network.createNode(new Vec2d(2.5, -5)).getId(),
            network.createNode(new Vec2d(2.5, 5)).getId(),
            List.of(new Vec2d(2.5, -5), new Vec2d(2.5, 5)),
            crossRoad.getId());

        List<RoadCrossing> crossings = RoadCrossingDetector.detectAll(network);
        assertEquals(1, crossings.size());
        RoadCrossing crossing = crossings.getFirst();
        assertTrue(crossing.involvesRoad(loopRoad.getId()));
        assertTrue(crossing.involvesRoad(crossRoad.getId()));
        assertEquals(2.5, crossing.position().x, 0.2);
        assertEquals(0.0, crossing.position().y, 0.2);
    }

    @Test
    void loopHa_chainageRoundTrip_afterInteriorSeam() {
        RoadNetwork network = buildSquareLoopHaNetwork();
        Road road = network.getRoad("square-ha");
        double total = RoadPlanGeometry.canonicalLength(network, road);

        assertChainageRoundTrip(network, road, 0.0, 0.15);
        assertChainageRoundTrip(network, road, total * 0.25, 0.15);
        assertChainageRoundTrip(network, road, total * 0.75, 0.15);
    }

    private static void assertChainageRoundTrip(
            RoadNetwork network,
            Road road,
            double designChainage,
            double tolerance) {
        Vec2d position = RoadStationing.pointAtStation(network, road, designChainage).orElseThrow();
        double actual = RoadStationing.chainageAtPosition(network, road, position).orElseThrow();
        assertEquals(designChainage, actual, tolerance,
            () -> "design=" + designChainage + " position=" + position);
    }

    private static RoadNetwork buildSquareLoopHaNetwork() {
        double arcLen = Math.PI * 10.0 / 2.0;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT)));

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("square-ha");
        road.setHorizontalAlignment(alignment);
        road.setTopologyMode(RoadTopologyMode.LOOP);

        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        RoadEdge edge1 = network.createEdge(n0.getId(), n1.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(
            new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(n3.getId(), n0.getId(), List.of(
            new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());

        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), edge1.getId(), 0.5));
        return network;
    }

    @Test
    void loopSeam_survivesJsonRoundTrip() throws Exception {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), "edge", 0.5));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());
        assertNotNull(restoredRoad.getLoopSeam());
        assertEquals(5.0, restoredRoad.getLoopSeam().position().x, 1e-6);
        assertTrue(network.toJson().contains("\"loopSeam\""));
    }
}
