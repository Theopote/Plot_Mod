package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignerWindowMetricsTest {

    @Test
    void initialSizeUsesPreferredDimensionsOnLargeViewport() {
        assertEquals(760f, PoleDesignerWindowMetrics.initialWidth(1920f), 0.01f);
        assertEquals(900f, PoleDesignerWindowMetrics.initialHeight(1080f), 0.01f);
    }

    @Test
    void initialSizeClampsToViewportOnSmallDisplays() {
        assertEquals(560f, PoleDesignerWindowMetrics.initialWidth(560f), 0.01f);
        assertEquals(520f, PoleDesignerWindowMetrics.initialHeight(520f), 0.01f);
    }

    @Test
    void maxSizeNeverFallsBelowMinimum() {
        assertTrue(PoleDesignerWindowMetrics.maxWidth(400f) >= PoleDesignerWindowMetrics.MIN_WIDTH);
        assertTrue(PoleDesignerWindowMetrics.maxHeight(300f) >= PoleDesignerWindowMetrics.MIN_HEIGHT);
    }
}
