package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineStyleCardRendererTest {

    @Test
    void previewKindMapsFamilyIds() {
        assertEquals(
            PowerLineStyleCardRenderer.StylePreviewKind.WOOD,
            PowerLineStyleCardRenderer.previewKindFor(null));
        assertEquals(
            PowerLineStyleCardRenderer.StylePreviewKind.WOOD,
            PowerLineStyleCardRenderer.previewKindFor(""));
        assertEquals(
            PowerLineStyleCardRenderer.StylePreviewKind.LATTICE,
            PowerLineStyleCardRenderer.previewKindFor(TowerFamily.STANDARD_LATTICE_3_PHASE_ID));
        assertEquals(
            PowerLineStyleCardRenderer.StylePreviewKind.ADAPTIVE,
            PowerLineStyleCardRenderer.previewKindFor(TowerFamily.GRADED_LATTICE_3_PHASE_ID));
    }
}
