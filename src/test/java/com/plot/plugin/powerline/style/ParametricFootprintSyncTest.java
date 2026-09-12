package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametricFootprintSyncTest {

    @Test
    void syncsMatchingProfileParametersToFootprint() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);

        PoleDesign draft = new PoleDesign("draft", "Draft");
        TowerParametricEditor.enableParametricClassic(draft, new TowerParameterSet(
            44.0,
            line.getParametricTowerConfig().parameters().baseWidth(),
            line.getParametricTowerConfig().parameters().armSpan(),
            1.0,
            1.0,
            null,
            line.getParametricTowerConfig().parameters().density()));

        assertTrue(ParametricFootprintSync.syncFromDesign(line, draft));
        assertEquals(44.0, line.getParametricTowerConfig().parameters().height(), 0.01);
        assertTrue(PowerLineStyleEditor.isModified(line));
    }

    @Test
    void skipsWhenProfileMismatch() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);

        PoleDesign draft = new PoleDesign("draft", "Draft");
        TowerParametricEditor.enableParametricHeavy(draft, TowerParameterSet.heavyDefaults());

        assertFalse(ParametricFootprintSync.syncFromDesign(line, draft));
        assertEquals(
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID,
            line.getParametricTowerConfig().profileId());
    }

    @Test
    void skipsWhenParametersAlreadyMatch() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        TowerGeneratorConfig config = line.getParametricTowerConfig();

        PoleDesign draft = new PoleDesign("draft", "Draft");
        draft.setGeneratorConfig(config.copy());
        draft.setTowerStructure(PowerLineStyleParametricCatalog.compileRepresentative(config).getTowerStructure());

        assertFalse(ParametricFootprintSync.syncFromDesign(line, draft));
    }
}
