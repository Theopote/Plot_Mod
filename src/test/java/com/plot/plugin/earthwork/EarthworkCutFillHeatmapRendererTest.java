package com.plot.plugin.earthwork;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EarthworkCutFillHeatmapRendererTest {

    @Test
    void cutFillAndUnchangedUseDistinctColors() {
        int cut = EarthworkCutFillHeatmapRenderer.colorFor(-3);
        int fill = EarthworkCutFillHeatmapRenderer.colorFor(4);
        int flat = EarthworkCutFillHeatmapRenderer.colorFor(0);
        assertNotEquals(cut, fill);
        assertNotEquals(cut, flat);
        assertNotEquals(fill, flat);
    }
}
