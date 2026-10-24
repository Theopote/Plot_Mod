package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticNodeElevationContinuityTest {
    @Test
    void movesSoftEndpointsAsWellAsInteriorNodesIntoOneFeasibleRoad() {
        var f = fixture();
        var solved = AutomaticNodeElevationContinuity.project(f.network, f.constraints, f.heights);
        assertTrue(Math.abs(solved.get(f.a) - solved.get(f.b)) <= 1);
        assertTrue(Math.abs(solved.get(f.b) - solved.get(f.c)) <= 1);
        assertNotEquals(80, solved.get(f.a));
        assertNotEquals(40, solved.get(f.c));
    }

    @Test
    void oneHardAnchorDrivesSoftNodesWithoutMovingTheUserLock() {
        var f = fixture();
        f.network.getNode(f.a).setManualElevation(80.0);
        var solved = AutomaticNodeElevationContinuity.project(f.network, f.constraints, f.heights);
        assertEquals(80, solved.get(f.a));
        assertTrue(solved.get(f.b) >= 79);
        assertTrue(solved.get(f.c) >= 78);
    }

    @Test
    void conflictingHardAnchorsAreNotSilentlyChanged() {
        var f = fixture();
        f.network.getNode(f.a).setManualElevation(80.0);
        f.network.getNode(f.c).setManualElevation(40.0);
        assertEquals(f.heights, AutomaticNodeElevationContinuity.project(f.network, f.constraints, f.heights));
    }

    private record Fixture(RoadNetwork network,
            Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> constraints,
            Map<String, Integer> heights, String a, String b, String c) { }

    private static Fixture fixture() {
        var network = new RoadNetwork();
        var road = network.createRoad("auto");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(20, 0));
        var c = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(b.getPosition(), c.getPosition()), road.getId());
        var constraints = Map.of(
            a.getId(), List.of(new JunctionFeasibleElevationResolver.ApproachConstraint(b.getId(), 20, 5)),
            b.getId(), List.of(new JunctionFeasibleElevationResolver.ApproachConstraint(a.getId(), 20, 5),
                new JunctionFeasibleElevationResolver.ApproachConstraint(c.getId(), 20, 5)),
            c.getId(), List.of(new JunctionFeasibleElevationResolver.ApproachConstraint(b.getId(), 20, 5)));
        return new Fixture(network, constraints,
            new LinkedHashMap<>(Map.of(a.getId(), 80, b.getId(), 60, c.getId(), 40)), a.getId(), b.getId(), c.getId());
    }
}
