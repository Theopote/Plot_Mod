package com.plot.plugin.earthwork.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkInsightChartsHeatmapSizeTest {

    @Test
    void tallSiteKeepsTallerThanWidePreview() {
        float[] size = EarthworkInsightCharts.heatmapDisplaySize(200f, 0.5);
        assertTrue(size[1] > size[0], "tall site should render taller than wide");
        assertEquals(size[0] / size[1], 0.5, 1e-3);
    }

    @Test
    void wideSiteKeepsWiderThanTallPreview() {
        float[] size = EarthworkInsightCharts.heatmapDisplaySize(200f, 2.0);
        assertTrue(size[0] > size[1], "wide site should render wider than tall");
        assertEquals(size[0] / size[1], 2.0, 1e-3);
    }

    @Test
    void previewFitsWithinAvailableWidth() {
        float[] size = EarthworkInsightCharts.heatmapDisplaySize(100f, 3.0);
        assertTrue(size[0] <= 100f + 1e-3);
        assertEquals(size[0] / size[1], 3.0, 1e-3);
    }
}
