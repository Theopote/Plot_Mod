package com.plot.infrastructure.event.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineToBlockGhostSettingsTest {

    @Test
    void defaultColorIsCyanFamily() {
        assertEquals(0x1AFFFF, LineToBlockGhostSettings.DEFAULT_COLOR_RGB);
    }

    @Test
    void opacityDefaultsInVisibleRange() {
        assertTrue(LineToBlockGhostSettings.DEFAULT_OPACITY >= 0.15f);
        assertTrue(LineToBlockGhostSettings.DEFAULT_OPACITY <= 0.95f);
    }
}
