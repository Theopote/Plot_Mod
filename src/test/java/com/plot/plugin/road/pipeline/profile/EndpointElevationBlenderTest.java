package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EndpointElevationBlenderTest {

    @Test
    void blendsTowardSnapAtCenter() {
        EndpointElevationSnap snap = new EndpointElevationSnap(new Vec2d(0, 0), 70, 10.0);

        assertEquals(70, EndpointElevationBlender.blend(new Vec2d(0, 0), snap, 64));
    }

    @Test
    void leavesElevationUnchangedOutsideBlendRadius() {
        EndpointElevationSnap snap = new EndpointElevationSnap(new Vec2d(0, 0), 70, 5.0);

        assertEquals(64, EndpointElevationBlender.blend(new Vec2d(10, 0), snap, 64));
    }
}

class EndpointElevationSnapResolverTest {

    @Test
    void returnsNullWhenNoNetworkElevations() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));

        assertNull(EndpointElevationSnapResolver.resolve(node, node, null, 5.0));
        assertNull(EndpointElevationSnapResolver.resolve(node, node, Map.of(), 5.0));
    }

    @Test
    void buildsSnapsForResolvedNodeElevations() {
        RoadNode start = new RoadNode(new Vec2d(0, 0));
        RoadNode end = new RoadNode(new Vec2d(10, 0));

        EndpointElevationSnaps snaps = EndpointElevationSnapResolver.resolve(
            start,
            end,
            Map.of(start.getId(), 68, end.getId(), 72),
            6.0);

        assertEquals(68, snaps.start().elevation());
        assertEquals(72, snaps.end().elevation());
        assertEquals(6.0, snaps.start().blendRadius(), 1e-9);
    }
}
