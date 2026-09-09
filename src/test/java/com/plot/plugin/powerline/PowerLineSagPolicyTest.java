package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineSagPolicyTest {

    private static PowerLineFootprint sampleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
    }

    @Test
    void lineCapIsUsedWhenSet() {
        PowerLineFootprint line = sampleLine();
        line.setMaxSagDepth(8.0);
        assertEquals(8.0, PowerLineSagPolicy.resolveMaxSagDepth(line));
    }

    @Test
    void unlimitedWhenExplicitlyZero() {
        PowerLineFootprint line = sampleLine();
        line.setMaxSagDepth(0.0);
        assertEquals(0.0, PowerLineSagPolicy.resolveMaxSagDepth(line));
    }

    @Test
    void defaultCapWhenUnset() {
        PowerLineFootprint line = sampleLine();
        assertEquals(PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH, PowerLineSagPolicy.resolveMaxSagDepth(line));
    }
}
