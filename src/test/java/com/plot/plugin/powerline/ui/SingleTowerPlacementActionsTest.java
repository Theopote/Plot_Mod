package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.SingleTowerStyleFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTowerPlacementActionsTest {

    @Test
    void standaloneStyleAvailableWithoutProjectLines() {
        PowerLinePluginState state = new PowerLinePluginState();

        assertTrue(state.getProject().getLines().isEmpty());
        assertTrue(SingleTowerStyleFootprint.isStandaloneStyle(state.getSingleTowerStyle().getId()));
        assertNotNull(PowerLineStyleEditor.basePreset(state.getSingleTowerStyle()));
    }
}
