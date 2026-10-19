package com.plot.ui.tools.impl.drawing.helper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawMeasureSettingsTest {

    @AfterEach
    void restoreDefaults() {
        DrawMeasureSettings.setEnabled(true);
        DrawMeasureSettings.setDecimalPlaces(2);
        DrawMeasureSettings.setShowUnit(true);
        DrawMeasureSettings.resetLabelColor();
        DrawMeasureSettings.setLabelOpacity(0.9f);
    }

    @Test
    void decimalPlacesAreClampedToZeroThroughFour() {
        DrawMeasureSettings.setDecimalPlaces(-3);
        assertEquals(0, DrawMeasureSettings.getDecimalPlaces());

        DrawMeasureSettings.setDecimalPlaces(99);
        assertEquals(4, DrawMeasureSettings.getDecimalPlaces());
    }

    @Test
    void labelOpacityIsClampedToUnitInterval() {
        DrawMeasureSettings.setLabelOpacity(-0.5f);
        assertEquals(0.0f, DrawMeasureSettings.getLabelOpacity(), 1e-6f);

        DrawMeasureSettings.setLabelOpacity(2.0f);
        assertEquals(1.0f, DrawMeasureSettings.getLabelOpacity(), 1e-6f);
    }

    @Test
    void customLabelColorStripsAlphaChannel() {
        DrawMeasureSettings.setLabelColorRgb(0xFF123456);

        assertTrue(DrawMeasureSettings.usesCustomLabelColor());
        assertEquals(0x123456, DrawMeasureSettings.getLabelColorRgb());
    }

    @Test
    void resetLabelColorRestoresThemeDefault() {
        DrawMeasureSettings.setLabelColorRgb(0xABCDEF);
        DrawMeasureSettings.resetLabelColor();

        assertFalse(DrawMeasureSettings.usesCustomLabelColor());
        assertEquals(DrawMeasureSettings.defaultLabelColorRgb(), DrawMeasureSettings.getLabelColorRgb());
    }

    @Test
    void effectiveLabelColorCombinesRgbAndOpacity() {
        DrawMeasureSettings.setLabelColorRgb(0x010203);
        DrawMeasureSettings.setLabelOpacity(0.5f);

        int argb = DrawMeasureSettings.getEffectiveLabelColorArgb();
        assertEquals(0x80, (argb >>> 24) & 0xFF);
        assertEquals(0x010203, argb & 0xFFFFFF);
    }
}
