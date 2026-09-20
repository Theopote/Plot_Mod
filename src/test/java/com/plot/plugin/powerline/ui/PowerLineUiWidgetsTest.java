package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineUiWidgetsTest {

    @Test
    void stableLabelKeepsHiddenIdSuffix() {
        String label = PowerLineUiWidgets.stableLabel(
            "plugin.powerline.corner_angle",
            "corner_angle");
        assertTrue(label.endsWith("##corner_angle"));
        assertTrue(label.contains("##"));
    }

    @Test
    void stableSelectableLabelUsesObjectId() {
        String label = PowerLineUiWidgets.stableSelectableLabel("Tower A", "line-42");
        assertTrue(label.endsWith("##line-42"));
        assertTrue(label.startsWith("Tower A"));
    }

    @Test
    void textHelpersIgnoreBlankInput() {
        PowerLineUiWidgets.text(null);
        PowerLineUiWidgets.text("   ");
        PowerLineUiWidgets.textColored(0xFFFFFFFF, null);
        PowerLineUiWidgets.textColored(0xFFFFFFFF, "");
    }
}
