package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentProfileSupportTest {

    @Test
    void shortNonFlatManualProfileIsNotSilentlyReplacedByLegacySolver() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("short-manual");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(18.0, 72.0))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(18, 0));
        network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(18, 0)), road.getId());

        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));
    }
}
