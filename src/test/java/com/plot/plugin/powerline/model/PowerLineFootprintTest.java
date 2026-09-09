package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineFootprintTest {

    @Test
    void raisingMinSpacingClampsMaxWhenNeeded() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            java.util.List.of(
                new com.plot.api.geometry.Vec2d(0, 0),
                new com.plot.api.geometry.Vec2d(10, 0)));
        footprint.setMaxPoleSpacing(12.0);
        footprint.setMinPoleSpacing(20.0);

        assertEquals(20.0, footprint.getMinPoleSpacing(), 1e-6);
        assertEquals(20.0, footprint.getMaxPoleSpacing(), 1e-6);
    }

    @Test
    void minSpacingCannotGoBelowConfigurableFloor() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        footprint.setMinPoleSpacing(2.0);
        assertEquals(PowerLineFootprint.MIN_CONFIGURABLE_SPACING, footprint.getMinPoleSpacing(), 1e-6);
    }

    @Test
    void loweringMaxSpacingKeepsMinWithinBounds() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            java.util.List.of(
                new com.plot.api.geometry.Vec2d(0, 0),
                new com.plot.api.geometry.Vec2d(10, 0)));
        footprint.setMinPoleSpacing(8.0);
        footprint.setMaxPoleSpacing(5.0);

        assertEquals(8.0, footprint.getMinPoleSpacing(), 1e-6);
        assertEquals(8.0, footprint.getMaxPoleSpacing(), 1e-6);
    }

    @Test
    void estimatePoleCountIncludesLayoutConstraints() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        footprint.setMaxPoleSpacing(80.0);
        int withoutConstraint = footprint.estimatePoleCount();

        footprint.addLayoutConstraint(new PoleLayoutConstraint(40.0, "mid"));
        int withConstraint = footprint.estimatePoleCount();

        assertEquals(PowerPoleLayoutUtils.computePoleSites(footprint).size(), withConstraint);
        assertTrue(withConstraint > withoutConstraint);
    }

    @Test
    void removeLayoutConstraintUpdatesCount() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        footprint.addLayoutConstraint(new PoleLayoutConstraint(20.0, "a"));
        footprint.addLayoutConstraint(new PoleLayoutConstraint(40.0, "b"));

        footprint.removeLayoutConstraint(0);

        assertEquals(1, footprint.getLayoutConstraints().size());
        assertEquals(40.0, footprint.getLayoutConstraints().getFirst().getRequiredStationing(), 1e-6);
    }
}
