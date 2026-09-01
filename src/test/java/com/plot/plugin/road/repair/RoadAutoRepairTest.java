package com.plot.plugin.road.repair;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadAutoRepairTest {

    @Test
    void diagnosesDisconnectedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("broken");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        List<RoadRepairIssue> issues = RoadAutoRepair.diagnose(
            network, road, new RoadSystemConfig("test"), IntersectionProbeResult.resolved(), false);

        assertTrue(issues.contains(RoadRepairIssue.TOPOLOGY_DISCONNECTED));
    }

    @Test
    void fixesDisconnectedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("broken");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        RoadAutoRepair.Result result = RoadAutoRepair.fix(
            network,
            road,
            new RoadSystemConfig("test"),
            null,
            null);

        assertTrue(result.changed());
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, network.getRoad(road.getId())).isEmpty()
            || network.getRoads().size() > 1);
    }

    @Test
    void diagnosesSteepGrade() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("steep");
        road.setMaxSlope(5.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 0.0),
            PointOfVerticalIntersection.of(100.0, 20.0)
        )));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        List<RoadRepairIssue> issues = RoadAutoRepair.diagnose(
            network, road, config, IntersectionProbeResult.resolved(), false);

        assertTrue(issues.contains(RoadRepairIssue.STEEP_GRADE));
    }

    @Test
    void fixesSteepGrade() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("steep");
        road.setMaxSlope(5.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 0.0),
            PointOfVerticalIntersection.of(100.0, 20.0)
        )));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadAutoRepair.Result result = RoadAutoRepair.fix(network, road, config, null, null);

        assertTrue(result.changed());
        assertFalse(RoadAutoRepair.diagnose(
            network, road, config, IntersectionProbeResult.resolved(), false)
            .contains(RoadRepairIssue.STEEP_GRADE));
    }
}
