package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewKeyTest {

    @Test
    void matchesWhileParametersUnchanged() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);
        assertTrue(key.matches(line, designs));
    }

    @Test
    void mismatchesWhenGenerationParameterChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);

        line.setPoleHeight(20.0);
        assertFalse(key.matches(line, designs));
    }

    @Test
    void mismatchesWhenFootprintIdChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, new PowerLineDesignProject());

        PowerLineFootprint other = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        assertFalse(key.matches(other, new PowerLineDesignProject()));
    }

    @Test
    void mismatchesWhenDesignProjectChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);

        PoleDesign custom = PoleDesignCatalog.simpleWoodPole().copy();
        custom.setName("Edited");
        designs.addDesign(custom);
        assertFalse(key.matches(line, designs));
    }
}
