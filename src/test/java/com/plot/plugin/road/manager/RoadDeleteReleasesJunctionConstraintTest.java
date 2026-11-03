package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadDeleteReleasesJunctionConstraintTest {

    @Test
    void deletingPartnerRoadReleasesOrphanJunctionFixedOnSurvivor() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("delete-junction"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();

        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 0));
        RoadNode a1 = network.createNode(new Vec2d(100, 0));
        RoadNode b0 = network.createNode(new Vec2d(50, -50));
        RoadNode b1 = network.createNode(new Vec2d(50, 50));
        network.createEdge(a0.getId(), a1.getId(), List.of(a0.getPosition(), a1.getPosition()), a.getId());
        network.createEdge(b0.getId(), b1.getId(), List.of(b0.getPosition(), b1.getPosition()), b.getId());
        a.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        b.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        a.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(50, 64),
            PointOfVerticalIntersection.of(100, 64))));
        b.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(50, 64),
            PointOfVerticalIntersection.of(100, 64))));
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        RoadProfileConstraintSynchronizer.seedCrossingAnchors(network, manager.getConfig());
        assertEquals(VerticalControlPointConstraint.JUNCTION_FIXED,
            a.getVerticalAlignment().getPvis().get(1).getConstraint());

        manager.deleteRoad(b.getId());

        Road survivor = manager.getNetwork().getRoad(a.getId());
        assertTrue(survivor != null);
        assertEquals(VerticalControlPointConstraint.FREE,
            survivor.getVerticalAlignment().getPvis().get(1).getConstraint());
    }
}
