package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoleSpacingModeLayoutTest {

    @Test
    void endpointsOnlyPlacesTwoPoles() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path,
            5.0,
            PoleSpacingMode.ENDPOINTS_ONLY,
            50.0,
            2,
            IdentityCoordinateService.INSTANCE);

        assertEquals(2, poles.size());
        assertEquals(0.0, poles.getFirst().x, 1e-6);
        assertEquals(10.0, poles.getLast().y, 1e-6);
    }

    @Test
    void endpointsWithCornersIncludesCornerVertex() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path,
            5.0,
            PoleSpacingMode.ENDPOINTS_WITH_CORNERS,
            50.0,
            2,
            IdentityCoordinateService.INSTANCE);

        assertEquals(3, poles.size());
        assertEquals(10.0, poles.get(1).x, 1e-6);
        assertEquals(0.0, poles.get(1).y, 1e-6);
    }

    @Test
    void towerCountDistributesEvenlyAlongPath() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
        List<Vec2d> poles = PowerPoleLayoutUtils.computePolePositions(
            path,
            5.0,
            PoleSpacingMode.TOWER_COUNT,
            50.0,
            5,
            IdentityCoordinateService.INSTANCE);

        assertEquals(5, poles.size());
        assertEquals(0.0, poles.getFirst().x, 1e-6);
        assertEquals(100.0, poles.getLast().x, 1e-6);
        assertEquals(25.0, poles.get(1).x, 1e-6);
    }

    @Test
    void footprintUsesPlacementMode() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10)));
        line.setPoleSpacingMode(PoleSpacingMode.ENDPOINTS_ONLY);
        line.setMaxPoleSpacing(50.0);

        assertEquals(2, line.estimatePoleCount(IdentityCoordinateService.INSTANCE));
    }
}
