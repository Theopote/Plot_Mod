package com.plot.plugin.powerline.model;

import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineStyleEditScopeTest {

    @Test
    void standaloneStyleUsesDedicatedFootprintId() {
        PowerLineFootprint standalone = SingleTowerStyleFootprint.createDefault();
        assertTrue(SingleTowerStyleFootprint.isStandaloneStyle(standalone.getId()));
    }

    @Test
    void adoptedLineStyleIsProjectBacked() {
        PowerLineFootprint line = new PowerLineFootprint("line-a", "Line A");
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        assertFalse(SingleTowerStyleFootprint.isStandaloneStyle(line.getId()));
    }
}
