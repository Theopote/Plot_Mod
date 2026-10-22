package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditedProfileGeometryTest {

    @Test
    void unboundSignatureDoesNotMatchCurrentGeometry() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(40.0, 68.0))));
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        assertFalse(EditedProfileGeometry.matches(
            network, road, EditedProfileGeometry.UNBOUND_SIGNATURE));
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
    }

    @Test
    void confirmedSignatureMatchesCurrentGeometry() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(40.0, 68.0))));
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
    }
}
