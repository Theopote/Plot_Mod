package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerRoleClassifierTest {

    @Test
    void straightInteriorPoleIsSuspension() {
        List<PowerPoleSite> sites = sitesAlongLine(0, 0, 100, 0, 5);
        TowerRoleClassifier.classifySites(sites, 5.0);
        for (int i = 1; i < sites.size() - 1; i++) {
            assertEquals(TowerRole.SUSPENSION, sites.get(i).getRole());
        }
    }

    @Test
    void startIsTerminal() {
        List<PowerPoleSite> sites = sitesAlongLine(0, 0, 100, 0, 3);
        TowerRoleClassifier.classifySites(sites, 5.0);
        assertEquals(TowerRole.TERMINAL, sites.getFirst().getRole());
    }

    @Test
    void endIsTerminal() {
        List<PowerPoleSite> sites = sitesAlongLine(0, 0, 100, 0, 3);
        TowerRoleClassifier.classifySites(sites, 5.0);
        assertEquals(TowerRole.TERMINAL, sites.getLast().getRole());
    }

    @Test
    void ninetyDegreeCornerIsAngle() {
        List<PowerPoleSite> sites = new ArrayList<>();
        sites.add(site(0, 0));
        sites.add(site(40, 0));
        sites.add(site(40, 40));
        TowerRoleClassifier.classifySites(sites, 5.0);
        assertEquals(TowerRole.TERMINAL, sites.get(0).getRole());
        assertEquals(TowerRole.ANGLE, sites.get(1).getRole());
        assertEquals(TowerRole.TERMINAL, sites.get(2).getRole());
        assertEquals(90.0, sites.get(1).getDeflectionAngle(), 0.5);
    }

    @Test
    void smallDeflectionStaysSuspension() {
        List<PowerPoleSite> sites = new ArrayList<>();
        sites.add(site(0, 0));
        sites.add(site(50, 0));
        sites.add(site(100, 2));
        TowerRoleClassifier.classifySites(sites, 5.0);
        assertEquals(TowerRole.SUSPENSION, sites.get(1).getRole());
    }

    @Test
    void manualOverrideWins() {
        List<PowerPoleSite> sites = sitesAlongLine(0, 0, 100, 0, 3);
        sites.get(1).setRole(TowerRole.DEAD_END);
        sites.get(1).setRoleAutoAssigned(false);
        TowerRoleClassifier.classifySites(sites, 5.0);
        assertEquals(TowerRole.DEAD_END, sites.get(1).getRole());
    }

    @Test
    void deflectionAngleStraightIsZero() {
        Vec2d incoming = new Vec2d(10, 0);
        Vec2d outgoing = new Vec2d(5, 0);
        assertEquals(0.0, TowerRoleClassifier.computeDeflectionAngle(incoming, outgoing), 1e-6);
    }

    @Test
    void deflectionAngleRightTurnIsNinety() {
        Vec2d incoming = new Vec2d(10, 0);
        Vec2d outgoing = new Vec2d(0, 10);
        assertEquals(90.0, TowerRoleClassifier.computeDeflectionAngle(incoming, outgoing), 0.5);
    }

    private static List<PowerPoleSite> sitesAlongLine(
            double x0, double y0, double x1, double y1, int count) {
        List<Vec2d> path = List.of(new Vec2d(x0, y0), new Vec2d(x1, y1));
        List<Vec2d> positions = PowerPoleLayoutUtils.computePolePositions(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);
        List<PowerPoleSite> sites = new ArrayList<>();
        for (Vec2d position : positions) {
            sites.add(new PowerPoleSite(position));
        }
        if (sites.size() > count) {
            return sites.subList(0, count);
        }
        return sites;
    }

    private static PowerPoleSite site(double x, double y) {
        return new PowerPoleSite(new Vec2d(x, y));
    }
}
