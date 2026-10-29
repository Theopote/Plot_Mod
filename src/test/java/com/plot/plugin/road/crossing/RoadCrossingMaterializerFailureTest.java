package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossingMaterializerFailureTest {

    @Test
    void missingRoadDoesNotReportComplete() {
        RoadNetwork network = crossedRoads();
        network.registerCrossing(new RoadCrossing(
            "missing-road",
            "roadA",
            16.0,
            "ghost",
            16.0,
            new Vec2d(0, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            null));

        SnapshotMaterialization result = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(result.report().isComplete());
        assertEquals(IntersectionResult.INCOMPLETE, result.report().toIntersectionResult());
        assertEquals(1, result.report().failures().size());
        CrossingMaterializationFailure failure = result.report().failures().getFirst();
        assertEquals(CrossingMaterializeStatus.SUCCESS, failure.armA());
        assertEquals(CrossingMaterializeStatus.ROAD_NOT_FOUND, failure.armB());
        assertEquals(IntersectionResult.INCOMPLETE, RoadCrossingMaterializer.materializeInPlace(network));
    }

    @Test
    void unresolvedStationDoesNotApplyCrossingDesign() {
        RoadNetwork network = crossedRoads();
        network.registerCrossing(new RoadCrossing(
            "bad-station",
            "roadA",
            16.0,
            "roadB",
            9999.0,
            new Vec2d(0, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            70.0));

        SnapshotMaterialization result = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(result.report().isComplete());
        CrossingMaterializationFailure failure = result.report().failures().getFirst();
        assertEquals(CrossingMaterializeStatus.STATION_UNRESOLVED, failure.armB());

        boolean anyCrossingLock = result.network().getNodes().values().stream()
            .anyMatch(node -> node.getManualElevation() != null
                && node.getManualElevationOrigin()
                    == com.plot.plugin.road.model.ManualElevationOrigin.CROSSING);
        assertFalse(anyCrossingLock, "failed crossing must not apply shared elevation design");
    }

    @Test
    void successfulCrossingAttachesBothRoadsAtomically() {
        RoadNetwork network = crossedRoads();
        network.registerCrossing(new RoadCrossing(
            "ok",
            "roadA",
            16.0,
            "roadB",
            16.0,
            new Vec2d(0, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            null));

        SnapshotMaterialization result = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(result.report().isComplete());
        assertEquals(1, result.report().succeededCount());
        assertEquals(IntersectionResult.COMPLETE, result.report().toIntersectionResult());

        RoadNode junction = result.network().getNodes().values().stream()
            .filter(node -> node.getPosition().distance(new Vec2d(0, 0)) < 0.5)
            .findFirst()
            .orElseThrow();
        assertTrue(RoadCrossingMaterializer.isRoadConnectedToJunction(result.network(), junction, "roadA"));
        assertTrue(RoadCrossingMaterializer.isRoadConnectedToJunction(result.network(), junction, "roadB"));
        assertTrue(junction.getDegree() >= 3);
    }

    @Test
    void materializeInPlaceReturnsIncompleteOnArmFailure() {
        RoadNetwork network = crossedRoads();
        network.registerCrossing(new RoadCrossing(
            "inplace-fail",
            "roadA",
            16.0,
            "missing",
            16.0,
            new Vec2d(0, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            null));
        assertEquals(IntersectionResult.INCOMPLETE, RoadCrossingMaterializer.materializeInPlace(network));
    }

    private static RoadNetwork crossedRoads() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        RoadNode a0 = network.createNode(new Vec2d(0, -16));
        RoadNode a1 = network.createNode(new Vec2d(0, 16));
        RoadNode b0 = network.createNode(new Vec2d(-16, 0));
        RoadNode b1 = network.createNode(new Vec2d(16, 0));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), roadA.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), roadB.getId());
        return network;
    }
}
