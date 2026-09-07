package com.plot.plugin.powerline.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
