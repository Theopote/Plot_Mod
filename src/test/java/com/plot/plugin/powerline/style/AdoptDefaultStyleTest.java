package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdoptDefaultStyleTest {

    @Test
    void selectClassicWoodGivesNewLineABasePreset() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());

        assertEquals(PowerLineStylePreset.RUSTIC_WOOD_ID, line.getStylePresetId());
        assertEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, line.getPoleDesignId());
        assertNull(line.getTowerFamilyId());
        assertNotNull(PowerLineStylePresetCatalog.activePreset(line));
        assertEquals(
            PowerLineStylePresetCatalog.classicWood().getId(),
            PowerLineStylePresetCatalog.activePreset(line).getId());
    }
}
