package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinePoleDesignOverridesTest {

    @Test
    void saveLineInstanceDoesNotMutateBuiltinTemplate() {
        PowerLineFootprint line = newLine("Test Line");
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject project = new PowerLineDesignProject();

        PoleDesign builtin = PoleDesignCatalog.findBuiltin(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        int originalLayerHeight = builtin.getLayers().getFirst().getHeight();

        PoleDesign edited = builtin.copy();
        PoleLayer layer = edited.getLayers().getFirst();
        layer.setHeight(layer.getHeight() + 4);

        LinePoleDesignOverrides.saveLineInstance(line, edited, project);

        assertEquals(originalLayerHeight, builtin.getLayers().getFirst().getHeight());
        assertTrue(LinePoleDesignOverrides.isLineInstanceDesignId(line.getPoleDesignId()));
        PoleDesign instance = project.getDesign(line.getPoleDesignId());
        assertNotNull(instance);
        assertEquals(originalLayerHeight + 4, instance.getLayers().getFirst().getHeight());
    }

    @Test
    void resetToBasePresetRemovesLineInstanceFork() {
        PowerLineFootprint line = newLine("Reset Line");
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.classicWood();
        PowerLineStyleEditor.selectPreset(line, preset);
        String basePoleId = line.getPoleDesignId();
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign edited = PoleDesignCatalog.findBuiltin(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID).copy();
        LinePoleDesignOverrides.saveLineInstance(line, edited, project);

        assertNotNull(project.getDesign(LinePoleDesignOverrides.lineInstanceDesignId(line)));

        PowerLineStyleEditor.resetToBasePreset(line, project);

        assertFalse(LinePoleDesignOverrides.isLineInstanceDesignId(line.getPoleDesignId()));
        assertEquals(basePoleId, line.getPoleDesignId());
        assertEquals(null, project.getDesign(LinePoleDesignOverrides.lineInstanceDesignId(line)));
    }

    @Test
    void resolveOpenDesignIdPrefersExistingInstance() {
        PowerLineFootprint line = newLine("Open Line");
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign edited = PoleDesignCatalog.findBuiltin(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID).copy();
        LinePoleDesignOverrides.saveLineInstance(line, edited, project);

        String openId = LinePoleDesignOverrides.resolveOpenDesignId(
            line,
            PoleDesignCatalog.SIMPLE_WOOD_POLE_ID,
            project);

        assertEquals(line.getPoleDesignId(), openId);
        assertNotEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, openId);
    }

    private static PowerLineFootprint newLine(String name) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setName(name);
        return line;
    }
}
