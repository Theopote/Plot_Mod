package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileChartCoordinatesTest {

    @Test
    void convertsBetweenGeometryLocalAndProfileDistance() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadGenerationResult result = new RoadGenerationResult(50);
        result.profileDistances = List.of(0.0, 25.0, 50.0);
        result.profileGroundHeights = List.of(64, 65, 66);
        result.profileGuideLine = List.of(64, 65, 66);
        result.profileDesignElevations = List.of(64.0, 65.0, 66.0);
        result.profileBuildHeights = List.of(64, 65, 66);
        result.profileTargetHeights = List.of(64, 65, 66);

        assertEquals(0.5, ProfileChartCoordinates.geometryToProfileScale(edge, result), 1e-6);
        assertEquals(25.0, ProfileChartCoordinates.geometryLocalToProfileDistance(edge, result, 50.0), 1e-6);
        assertEquals(50.0, ProfileChartCoordinates.profileDistanceToGeometryLocal(edge, result, 25.0), 1e-6);
    }
}
