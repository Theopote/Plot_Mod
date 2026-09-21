package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoleDesignerLayoutPanelTest {

    @Test
    void previewColumnWidthNeverFallsBelowMinimum() {
        PowerLinePluginState state = new PowerLinePluginState();
        state.setPoleDesignerPreviewColumnWidth(120f);
        assertEquals(PoleDesignerLayoutPanel.MIN_PREVIEW_COLUMN_WIDTH, state.getPoleDesignerPreviewColumnWidth(), 0.01f);
    }
}
