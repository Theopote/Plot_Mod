package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.TunnelPreset;
import com.plot.plugin.road.tunnel.TunnelPresetCatalog;
import com.plot.plugin.road.tunnel.TunnelStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TunnelStylePresetSelectionTest {

    @Test
    void appliedPresetClearsWhenStyleDiverges() {
        TunnelStyleEditor.PresetSelection selection = new TunnelStyleEditor.PresetSelection();
        TunnelStyle style = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.MODERN_HIGHWAY, style);
        selection.apply(TunnelPreset.MODERN_HIGHWAY);
        assertEquals(TunnelPreset.MODERN_HIGHWAY, selection.applied());

        style.setClearHeight(style.getClearHeight() + 1);
        selection.syncWithStyle(style);
        assertNull(selection.applied(), "manual edit must fall back to custom");
    }

    @Test
    void appliedPresetSurvivesIdenticalSync() {
        TunnelStyleEditor.PresetSelection selection = new TunnelStyleEditor.PresetSelection();
        TunnelStyle style = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.METRO, style);
        selection.apply(TunnelPreset.METRO);
        selection.syncWithStyle(style);
        assertEquals(TunnelPreset.METRO, selection.applied());
    }
}
