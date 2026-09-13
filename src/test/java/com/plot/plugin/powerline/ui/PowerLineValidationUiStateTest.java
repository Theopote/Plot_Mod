package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineValidationUiStateTest {

    @Test
    void overlayEnabledByDefault() {
        assertTrue(new PowerLineValidationUiState().isOverlayEnabled());
    }

    @Test
    void lineChecksEnabledByDefaultOnNewFootprint() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        assertTrue(line.isLineChecksEnabled());
        assertTrue(line.isTerrainAvoidanceEnabled());
    }

    @Test
    void overlayCanBeTurnedOffExplicitly() {
        PowerLineValidationUiState state = new PowerLineValidationUiState();
        state.setOverlayEnabled(false);
        assertFalse(state.isOverlayEnabled());
    }
}
