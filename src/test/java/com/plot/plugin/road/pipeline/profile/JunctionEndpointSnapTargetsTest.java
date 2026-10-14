package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JunctionEndpointSnapTargetsTest {

    @Test
    void atGradeJunctionUsesUnifiedNetworkElevation() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), "roadA");
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), "roadB");
        RoadEdge edge = network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), "roadC");

        List<SegmentHeightInfo> heightInfos = List.of(new SegmentHeightInfo(
            new com.plot.plugin.road.pipeline.geometry.PathSegment(new Vec2d(0, 0), new Vec2d(40, 0)),
            68, 72, 68, 72, 0.0));

        Integer snapY = JunctionEndpointSnapTargets.snapTargetY(
            junction,
            edge,
            network,
            Map.of(junction.getId(), 75),
            heightInfos,
            true,
            (node, net) -> null);

        assertEquals(75, snapY);
    }

    @Test
    void elevatedRoadKeepsProfileDeckHeightAtGradeSeparatedNode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("elevated");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setGradeSeparated(true);
        junction.setElevatedRoadId(road.getId());
        RoadNode far = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(junction.getId(), far.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        List<SegmentHeightInfo> heightInfos = List.of(new SegmentHeightInfo(
            new com.plot.plugin.road.pipeline.geometry.PathSegment(new Vec2d(0, 0), new Vec2d(40, 0)),
            83, 83, 83, 83, 0.0));

        Integer snapY = JunctionEndpointSnapTargets.snapTargetY(
            junction,
            edge,
            network,
            Map.of(junction.getId(), 70),
            heightInfos,
            true,
            (node, net) -> road.getId());

        assertEquals(83, snapY);
    }
}
