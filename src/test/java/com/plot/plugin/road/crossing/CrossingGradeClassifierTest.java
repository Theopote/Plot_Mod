package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingGradeClassifierTest {

    @Test
    void missingProfilesStayAtGrade() {
        RoadNetwork network = twoRoads();
        RoadCrossing crossing = CrossingGradeClassifier.classifyTwoRoad(
            network, "a", 5.0, "b", 5.0, new Vec2d(5, 5));
        assertEquals(CrossingType.AT_GRADE, crossing.type());
        assertNull(crossing.elevatedRoadId());
    }

    @Test
    void smallElevationGapStaysAtGrade() {
        RoadNetwork network = twoRoads();
        setFlatProfile(network, "a", 64.0);
        setFlatProfile(network, "b", 65.0);
        RoadCrossing crossing = CrossingGradeClassifier.classifyTwoRoad(
            network, "a", 5.0, "b", 5.0, new Vec2d(5, 5));
        assertEquals(CrossingType.AT_GRADE, crossing.type());
    }

    @Test
    void profileSuggestsGradeSeparationMatchesClassifierThreshold() {
        RoadNetwork network = twoRoads();
        setFlatProfile(network, "a", 64.0);
        setFlatProfile(network, "b", 72.0);
        assertTrue(CrossingGradeClassifier.profileSuggestsGradeSeparation(network, "a", 5.0, "b", 5.0));
        setFlatProfile(network, "b", 65.0);
        assertFalse(CrossingGradeClassifier.profileSuggestsGradeSeparation(network, "a", 5.0, "b", 5.0));
    }

    @Test
    void clearanceGapMarksGradeSeparatedWithHigherRoadElevated() {
        RoadNetwork network = twoRoads();
        setFlatProfile(network, "a", 64.0);
        setFlatProfile(network, "b", 72.0);
        RoadCrossing crossing = CrossingGradeClassifier.classifyTwoRoad(
            network, "a", 5.0, "b", 5.0, new Vec2d(5, 5));
        assertEquals(CrossingType.GRADE_SEPARATED, crossing.type());
        assertEquals("b", crossing.elevatedRoadId());
        assertEquals(CrossingGradeClassifier.DEFAULT_CLEARANCE, crossing.crossingClearance(), 1e-9);
    }

    @Test
    void stackWithStackedProfilesMarksGradeSeparated() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(10.0, 64.0),
            PointOfVerticalIntersection.of(20.0, 72.0),
            PointOfVerticalIntersection.of(30.0, 72.0))));
        RoadStackCrossing stack = CrossingGradeClassifier.classifyStack(
            network, road.getId(), 5.0, 25.0, new Vec2d(5, 5));
        assertEquals(CrossingType.GRADE_SEPARATED, stack.type());
        assertEquals(5.0, stack.resolvedLowerStation(), 1e-9);
        assertEquals(25.0, stack.resolvedUpperStation(), 1e-9);
    }

    private static RoadNetwork twoRoads() {
        RoadNetwork network = new RoadNetwork();
        network.createRoad("a");
        network.createRoad("b");
        return network;
    }

    private static void setFlatProfile(RoadNetwork network, String roadId, double elevation) {
        network.getRoad(roadId).setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, elevation),
            PointOfVerticalIntersection.of(10.0, elevation))));
    }
}
