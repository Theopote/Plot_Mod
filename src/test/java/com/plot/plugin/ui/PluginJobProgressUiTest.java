package com.plot.plugin.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginJobProgressUiTest {

    @Test
    void fractionAndPercent() {
        assertEquals(0f, PluginJobProgressUi.fraction(0, 0));
        assertEquals(0.5f, PluginJobProgressUi.fraction(1, 2));
        assertEquals(1f, PluginJobProgressUi.fraction(5, 3));
        assertEquals(50, PluginJobProgressUi.percent(1, 2));
        assertEquals(100, PluginJobProgressUi.percent(10, 8));
    }
}
