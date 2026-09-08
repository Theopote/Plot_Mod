package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineSpacingCardRendererTest {

    @Test
    void poleCountIncreasesWithDensity() {
        assertEquals(6, PowerLineSpacingCardRenderer.poleCountFor(PowerLineUiPresets.SpacingDensity.DENSE));
        assertEquals(4, PowerLineSpacingCardRenderer.poleCountFor(PowerLineUiPresets.SpacingDensity.NORMAL));
        assertEquals(3, PowerLineSpacingCardRenderer.poleCountFor(PowerLineUiPresets.SpacingDensity.SPARSE));
    }
}
