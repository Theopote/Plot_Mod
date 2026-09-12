package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineBuildMetricsTest {

    @Test
    void openLineUsesPoleCountMinusOneSpans() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(100, 0)));

        assertEquals(25.0, PowerLineBuildMetrics.typicalSpanBlocks(line, 100.0, 5, 40.0), 1e-6);
    }

    @Test
    void closedLoopUsesPoleCountSpans() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 100, 50, 0);
        PowerLineFootprint line = PowerLinePathLayout.adopt(rectangle, IdentityCoordinateService.INSTANCE);

        assertTrue(line.isClosedLoop());
        assertEquals(20.0, PowerLineBuildMetrics.typicalSpanBlocks(line, 100.0, 5, 40.0), 1e-6);
    }

    @Test
    void fallsBackToMaxSpacingWhenSpanCountInvalid() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        assertEquals(40.0, PowerLineBuildMetrics.typicalSpanBlocks(line, 100.0, 1, 40.0), 1e-6);
        assertEquals(40.0, PowerLineBuildMetrics.typicalSpanBlocks(line, 0.0, 5, 40.0), 1e-6);
        assertEquals(40.0, PowerLineBuildMetrics.typicalSpanBlocks(line, 100.0, 0, 40.0), 1e-6);
    }
}
