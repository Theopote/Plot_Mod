package com.plot.plugin.ui;

import com.plot.ui.theme.UITheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PluginSectionColorsTest {
    @Test
    void darkAndLightPalettesAreNonNullWithDistinctLevelAccents() {
        PluginSectionColors dark = PluginSectionColors.forTheme(true, UITheme.DARK_THEME);
        PluginSectionColors light = PluginSectionColors.forTheme(false, UITheme.LIGHT_THEME);
        assertNotNull(dark);
        assertNotNull(light);
        assertNotEquals(dark.surface, light.surface);
        assertNotEquals(dark.accentLevel0, dark.accentLevel1);
        assertNotEquals(dark.accentLevel1, dark.accentLevel2);
        assertNotEquals(light.accentLevel0, light.accentLevel2);
        assertNotEquals(dark.surface, dark.accentFor(PluginSectionCard.SectionTone.LEVEL_0));
        assertNotEquals(light.surface, light.border);
    }

    @Test
    void heightCacheStartsEmptyAndClearable() {
        PluginSectionCard.clearCachesForTests();
        assertNull(PluginSectionCard.measuredHeightForTests("##lamp_sec_pole"));
        assertNull(PluginSectionCard.openStateForTests("##lamp_sec_pole"));
    }
}
