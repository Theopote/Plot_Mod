package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerPoleLayoutUtilsTest {

    @Test
    void straightPathInsertsEvenSpacingNotExceedingMax() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 20.0, IdentityCoordinateService.INSTANCE);

        assertEquals(6, poles.size());
        assertEquals(0.0, poles.getFirst().x, 1e-6);
        assertEquals(100.0, poles.getLast().x, 1e-6);
        for (int i = 1; i < poles.size(); i++) {
            double spacing = poles.get(i - 1).distance(poles.get(i));
            assertTrue(spacing <= 20.0 + 1e-6);
            assertTrue(spacing >= 19.9);
        }
    }

    @Test
    void cornerVerticesAreForcedPoles() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);

        assertEquals(3, poles.size());
        assertEquals(10.0, poles.get(1).x, 1e-6);
        assertEquals(0.0, poles.get(1).y, 1e-6);
    }

    @Test
    void closeCornersAreBothRetainedWithoutMerging() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(5, 0),
            new Vec2d(5, 5),
            new Vec2d(0, 5));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);

        assertEquals(4, poles.size());
        assertEquals(5.0, poles.get(1).distance(poles.get(2)), 1e-6);
    }

    @Test
    void mandatoryPolePointsIncludeCornersAndEndpoints() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        List<Vec2d> mandatory = PowerPoleLayoutUtils.mandatoryPolePoints(path, 5.0);

        assertEquals(3, mandatory.size());
        assertEquals(10.0, mandatory.get(1).x, 1e-6);
        assertEquals(0.0, mandatory.get(1).y, 1e-6);
    }

    @Test
    void duplicatePathVerticesDoNotCreateStackedPoles() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 0),
            new Vec2d(100, 0));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);

        assertEquals(3, poles.size());
        for (int i = 1; i < poles.size(); i++) {
            assertTrue(
                poles.get(i - 1).distance(poles.get(i)) > 0.15,
                "consecutive poles should not stack");
        }
        assertEquals(100.0, poles.getLast().x, 1e-6);
    }

    @Test
    void zeroLengthSegmentDoesNotDuplicateMandatoryPole() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 0),
            new Vec2d(0, 0),
            new Vec2d(40, 0));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);

        assertEquals(2, poles.size());
        assertEquals(0.0, poles.getFirst().x, 1e-6);
        assertEquals(40.0, poles.getLast().x, 1e-6);
    }

    @Test
    void autoSpacingFollowsPolylineBetweenMandatoryPoints() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(50, 2),
            new Vec2d(100, 0));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 25.0, IdentityCoordinateService.INSTANCE);

        assertTrue(
            poles.stream().anyMatch(p -> p.y > 0.5),
            "interpolated poles should follow the polyline, not the A-B chord");
        for (Vec2d pole : poles) {
            double stationing = PowerPoleLayoutUtils.computeStationing(
                path, pole, IdentityCoordinateService.INSTANCE);
            Vec2d onPath = PowerPoleLayoutUtils.pointAtStationing(
                path, stationing, IdentityCoordinateService.INSTANCE);
            assertTrue(
                pole.distance(onPath) < 0.2,
                "pole should lie on path at its stationing");
        }
    }

    @Test
    void computedSitesCoverMandatoryPoints() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        List<Vec2d> mandatory = PowerPoleLayoutUtils.mandatoryPolePoints(path, 5.0);
        List<com.plot.plugin.powerline.model.PowerPoleSite> sites =
            PowerPoleLayoutUtils.computePoleSites(path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);

        for (Vec2d point : mandatory) {
            assertTrue(PowerPoleLayoutUtils.hasSiteNear(
                sites, point, 0.15, IdentityCoordinateService.INSTANCE));
        }
    }
}
