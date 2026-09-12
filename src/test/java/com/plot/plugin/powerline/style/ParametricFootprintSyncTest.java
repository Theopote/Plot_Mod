package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametricFootprintSyncTest {

    @Test
    void syncPushesFirstParametricConfigToLine() {
        PowerLineFootprint line = styledLine();
        PoleDesign draft = new PoleDesign("draft", "Draft");
        TowerParametricEditor.enableParametricClassic(draft, TowerParameterSet.classicDefaults());

        assertTrue(ParametricFootprintSync.syncFromDesign(line, draft, "family-edit"));
        assertTrue(line.hasParametricTowerConfig());
        assertEquals(36.0, line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void syncUpdatesChangedParameters() {
        PowerLineFootprint line = styledLine();
        PoleDesign draft = new PoleDesign("draft", "Draft");
        TowerParametricEditor.enableParametricClassic(draft, TowerParameterSet.classicDefaults());
        ParametricFootprintSync.syncFromDesign(line, draft, "family-edit");

        draft.setGeneratorConfig(draft.getGeneratorConfig().withParameters(
            new TowerParameterSet(44.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM)));

        assertTrue(ParametricFootprintSync.syncFromDesign(line, draft, "family-edit"));
        assertEquals(44.0, line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void syncSkipsMismatchedProfileOnFamilyLine() {
        PowerLineFootprint line = styledLine();
        PoleDesign draft = new PoleDesign("draft", "Draft");
        TowerParametricEditor.enableParametricCup(draft, TowerParameterSet.cupDefaults());

        assertFalse(ParametricFootprintSync.syncFromDesign(line, draft, "preset/other"));
    }

    private static PowerLineFootprint styledLine() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        return line;
    }
}
