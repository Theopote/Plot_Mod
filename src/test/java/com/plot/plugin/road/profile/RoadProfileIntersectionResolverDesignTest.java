package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: design-time intersection markers must read stored VA even when generation
 * authority is still AUTO (editor working copy before confirm).
 */
class RoadProfileIntersectionResolverDesignTest {

    private final RoadSystemConfig config = new RoadSystemConfig("design-resolver");

    @Test
    void forRoadDesignUsesManualProfileAlignmentBeforeEditedAuthority() {
        RoadNetwork network = new RoadNetwork();
        Road upper = road(network, "upper");
        Road lower = road(network, "lower");
        upper.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 80.0))));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, upper));

        network.registerCrossing(new RoadCrossing(
            "auto",
            "upper",
            50.0,
            "lower",
            50.0,
            new Vec2d(50, 0),
            CrossingType.GRADE_SEPARATED,
            null,
            6.0,
            null));

        var upperMarker = RoadProfileIntersectionResolver.forRoadDesign(network, upper, config).getFirst();
        var lowerMarker = RoadProfileIntersectionResolver.forRoadDesign(network, lower, config).getFirst();

        assertTrue(upperMarker.currentRoadElevated());
        assertFalse(lowerMarker.currentRoadElevated());
        assertEquals(16.0, upperMarker.clearanceGap(), 1e-6);
        assertEquals(16.0, lowerMarker.clearanceGap(), 1e-6);
    }

    private static Road road(RoadNetwork network, String id) {
        Road road = network.createRoad(id);
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(start.getPosition(), end.getPosition()),
            id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));
        return road;
    }
}
