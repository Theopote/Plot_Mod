package com.plot.plugin.powerline.model;

import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTowerStyleFootprintTest {

    @Test
    void createDefaultUsesStandaloneIdAndPreset() {
        var footprint = SingleTowerStyleFootprint.createDefault();

        assertEquals(SingleTowerStyleFootprint.STYLE_ID, footprint.getId());
        assertTrue(footprint.getPathPoints().size() >= 2);
        assertNotNull(PowerLineStyleEditor.basePreset(footprint));
        assertEquals(
            PowerLineStylePresetCatalog.classicLattice().getId(),
            PowerLineStyleEditor.basePreset(footprint).getId());
    }

    @Test
    void isStandaloneStyleRecognizesStyleId() {
        assertTrue(SingleTowerStyleFootprint.isStandaloneStyle(SingleTowerStyleFootprint.STYLE_ID));
    }
}
