package com.plot.plugin.road.terrain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorWorldBlockListenerTest {

    @AfterEach
    void tearDown() {
        RoadCorridorWorldBlockListener.setEnabled(false);
        RoadCorridorTerrainPrefetch.clear();
    }

    @Test
    void setEnabledTogglesGuardWithoutRequiringNativeRegister() {
        RoadCorridorWorldBlockListener.setEnabled(true);
        assertTrue(RoadCorridorWorldBlockListener.isEnabled());
        RoadCorridorWorldBlockListener.setEnabled(false);
        assertFalse(RoadCorridorWorldBlockListener.isEnabled());
    }
}
