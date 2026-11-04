package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.tunnel.TunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeSeparationClearanceTest {

    @Test
    void defaultsToUnderpassTunnelClearHeight() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(3.0);
        TunnelStyle tunnel = config.getTunnelStyle();
        tunnel.setClearHeight(7);
        tunnel.ensureConfigDefaults();

        RoadNetwork network = new RoadNetwork();
        Road upper = network.createRoad("upper");
        Road lower = network.createRoad("lower");
        lower.setTunnelStyle(tunnel.copy());

        assertEquals(7.0, GradeSeparationClearance.resolve(null, lower, config), 1e-6);
        assertEquals(
            7.0,
            GradeSeparationClearance.forTwoRoadCrossing(
                null, network, upper.getId(), upper.getId(), lower.getId(), config),
            1e-6);
    }

    @Test
    void raisesExplicitClearanceBelowTunnelMinimum() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.getTunnelStyle().setClearHeight(7);
        config.getTunnelStyle().ensureConfigDefaults();

        RoadNetwork network = new RoadNetwork();
        Road lower = network.createRoad("lower");
        lower.setTunnelStyle(config.getTunnelStyle().copy());

        assertEquals(7.0, GradeSeparationClearance.resolve(4.0, lower, config), 1e-6);
    }

    @Test
    void keepsExplicitClearanceAboveTunnelMinimum() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.getTunnelStyle().setClearHeight(7);
        config.getTunnelStyle().ensureConfigDefaults();

        RoadNetwork network = new RoadNetwork();
        Road lower = network.createRoad("lower");
        lower.setTunnelStyle(config.getTunnelStyle().copy());

        assertEquals(9.0, GradeSeparationClearance.resolve(9.0, lower, config), 1e-6);
    }

    @Test
    void nodeUsesMaxUnderpassTunnelClearHeightAsFloor() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.getTunnelStyle().setClearHeight(5);
        config.getTunnelStyle().ensureConfigDefaults();

        RoadNetwork network = new RoadNetwork();
        Road elevated = network.createRoad("elevated");
        Road underA = network.createRoad("under-a");
        Road underB = network.createRoad("under-b");

        TunnelStyle tall = new TunnelStyle();
        tall.setClearHeight(8);
        tall.ensureConfigDefaults();
        underA.setTunnelStyle(tall);

        TunnelStyle shortStyle = new TunnelStyle();
        shortStyle.setClearHeight(4);
        shortStyle.ensureConfigDefaults();
        underB.setTunnelStyle(shortStyle);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadNode west = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), elevated.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), underA.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), underB.getId());
        junction.setGradeSeparated(true);
        junction.setElevatedRoadId(elevated.getId());

        assertEquals(
            8.0,
            GradeSeparationClearance.forNode(null, junction, network, elevated.getId(), config),
            1e-6);
        assertEquals(
            8.0,
            GradeSeparationClearance.forNode(5.0, junction, network, elevated.getId(), config),
            1e-6);
        assertEquals(
            10.0,
            GradeSeparationClearance.forNode(10.0, junction, network, elevated.getId(), config),
            1e-6);
    }

    @Test
    void fallsBackToConfigTunnelClearHeightWithoutUnderpassRoad() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(3.0);
        config.getTunnelStyle().setClearHeight(6);
        config.getTunnelStyle().ensureConfigDefaults();

        assertEquals(6.0, GradeSeparationClearance.fromUnderpassRoad(null, config), 1e-6);
    }
}
