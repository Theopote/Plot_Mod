package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.api.geometry.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineUiPresetsTest {
    @Test
    void applySpacingSetsMinAndMax() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applySpacing(line, PowerLineUiPresets.SpacingDensity.SPARSE);
        assertEquals(30.0, line.getMaxPoleSpacing());
        assertEquals(10.0, line.getMinPoleSpacing());
        assertEquals(PowerLineUiPresets.SpacingDensity.SPARSE, PowerLineUiPresets.detectSpacing(line));
    }

    @Test
    void applySagSetsRatio() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applySag(line, PowerLineUiPresets.WireSag.NATURAL);
        assertEquals(0.15, line.getSagRatio(), 0.001);
        assertEquals(PowerLineUiPresets.WireSag.NATURAL, PowerLineUiPresets.detectSag(line));
    }
}
