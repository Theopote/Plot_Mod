package com.plot.plugin.road.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadValidationDrillDownTest {

    @Test
    void resolvesDisconnectedRoads() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("broken");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        List<String> roadIds = RoadValidationDrillDown.affectedRoadIds(
            "road_disconnected", network, null);

        assertEquals(List.of(road.getId()), roadIds);
        assertTrue(RoadValidationDrillDown.supports("road_disconnected"));
        assertFalse(RoadValidationDrillDown.supports("network_disconnected_components"));
    }

    @Test
    void resolvesTopologyIssuesAcrossKinds() {
        RoadNetwork network = new RoadNetwork();
        Road disconnected = network.createRoad("broken");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(100, 0));
        RoadNode d = network.createNode(new Vec2d(110, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), disconnected.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), disconnected.getId());

        Road ring = network.createRoad("ring");
        RoadNode r1 = network.createNode(new Vec2d(0, 50));
        RoadNode r2 = network.createNode(new Vec2d(10, 50));
        RoadNode r3 = network.createNode(new Vec2d(10, 60));
        network.createEdge(r1.getId(), r2.getId(), List.of(new Vec2d(0, 50), new Vec2d(10, 50)), ring.getId());
        network.createEdge(r2.getId(), r3.getId(), List.of(new Vec2d(10, 50), new Vec2d(10, 60)), ring.getId());
        network.createEdge(r3.getId(), r1.getId(), List.of(new Vec2d(10, 60), new Vec2d(0, 50)), ring.getId());

        List<String> roadIds = RoadValidationDrillDown.affectedRoadIds("topology_issues", network, null);

        assertEquals(2, roadIds.size());
        assertTrue(roadIds.contains(disconnected.getId()));
        assertTrue(roadIds.contains(ring.getId()));
    }

    @Test
    void messageIssueIdParsesFromTitleKey() {
        RoadValidationMessage message = RoadValidationMessage.of(
            com.plot.plugin.road.RoadNetworkValidationReport.Level.WARNING,
            "road_disconnected",
            2);

        assertEquals("road_disconnected", message.issueId());
    }
}
