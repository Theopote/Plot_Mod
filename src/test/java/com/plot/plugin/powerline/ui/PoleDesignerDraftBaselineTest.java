package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignerDraftBaselineTest {

    @Test
    void unchangedDraftIsClean() {
        var design = PoleDesignCatalog.simpleWoodPole().copy();
        String baseline = PoleDesignerDraftBaseline.capture(design);
        assertFalse(PoleDesignerDraftBaseline.isDirty(design, baseline));
    }

    @Test
    void structuralEditMarksDraftDirty() {
        var design = PoleDesignCatalog.simpleWoodPole().copy();
        String baseline = PoleDesignerDraftBaseline.capture(design);
        design.getLayers().get(0).setHeight(99);
        assertTrue(PoleDesignerDraftBaseline.isDirty(design, baseline));
    }

    @Test
    void savedDraftMatchesNewBaseline() {
        var design = PoleDesignCatalog.simpleWoodPole().copy();
        design.getLayers().get(0).setHeight(99);
        String baseline = PoleDesignerDraftBaseline.capture(design);
        assertFalse(PoleDesignerDraftBaseline.isDirty(design, baseline));
    }
}
