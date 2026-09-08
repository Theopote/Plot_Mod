package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineOverviewRendererTest {

    @Test
    void hitTestLineSelectsNearestPath() {
        PowerLineFootprint near = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineFootprint far = new PowerLineFootprint(List.of(new Vec2d(0, 20), new Vec2d(40, 20)));
        String hit = PowerLineOverviewRenderer.hitTestLine(List.of(near, far), 20, 1, 5);
        assertEquals(near.getId(), hit);
    }

    @Test
    void hitTestLineReturnsNullWhenTooFar() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        assertNull(PowerLineOverviewRenderer.hitTestLine(List.of(line), 20, 20, 2));
    }

    @Test
    void mapHeightUsesMinimum() {
        assertEquals(80f, PowerLineOverviewRenderer.mapHeightForWidth(0f), 0.01f);
    }
}
