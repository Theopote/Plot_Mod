package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PoleSpacingProfile;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.api.geometry.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineUiPresetsTest {
    @Test
    void applySpacingUsesActiveStyleCharacter() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicWood().apply(line);
        PowerLineUiPresets.applySpacing(line, PowerLineUiPresets.SpacingDensity.SPARSE);
        assertEquals(PoleSpacingProfile.streetWood().sparseMaxSpacing(), line.getMaxPoleSpacing(), 1.0);
        assertEquals(15.0, line.getMinPoleSpacing());
        assertEquals(PowerLineUiPresets.SpacingDensity.SPARSE, PowerLineUiPresets.detectSpacing(line));
    }

    @Test
    void applySagSetsRatio() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applySag(line, PowerLineUiPresets.WireSag.NATURAL);
        assertEquals(0.15, line.getSagRatio(), 0.001);
        assertEquals(PowerLineUiPresets.WireSag.NATURAL, PowerLineUiPresets.detectSag(line));
    }

    @Test
    void presetSagCapsAtLooseMaximum() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applySag(line, PowerLineUiPresets.WireSag.LOOSE);
        assertEquals(PowerLineUiPresets.PRESET_SAG_MAX_RATIO, line.getSagRatio(), 0.001);
    }

    @Test
    void advancedSagAllowsHigherRatio() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applyAdvancedSag(line, 0.30);
        assertEquals(0.30, line.getSagRatio(), 0.001);
        assertNull(PowerLineUiPresets.detectSag(line));
        assertFalse(PowerLineUiPresets.isPresetSag(line));
    }

    @Test
    void advancedSagClampsAtMaximum() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applyAdvancedSag(line, 0.50);
        assertEquals(PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO, line.getSagRatio(), 0.001);
    }
}
