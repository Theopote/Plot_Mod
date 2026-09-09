package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSpacingCardRendererTest {

    @Test
    void poleCountReflectsStyleAndDensity() {
        PowerLineFootprint wood = lineWithStyle(PowerLineStylePresetCatalog.classicWood());
        PowerLineFootprint hv = lineWithStyle(PowerLineStylePresetCatalog.classicLattice());

        assertTrue(PowerLineSpacingCardRenderer.poleCountFor(wood, PowerLineUiPresets.SpacingDensity.DENSE)
            > PowerLineSpacingCardRenderer.poleCountFor(wood, PowerLineUiPresets.SpacingDensity.SPARSE));
        assertTrue(PowerLineSpacingCardRenderer.poleCountFor(hv, PowerLineUiPresets.SpacingDensity.SPARSE)
            < PowerLineSpacingCardRenderer.poleCountFor(wood, PowerLineUiPresets.SpacingDensity.SPARSE));
    }

    private static PowerLineFootprint lineWithStyle(com.plot.plugin.powerline.style.PowerLineStylePreset preset) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        preset.apply(line);
        return line;
    }
}
