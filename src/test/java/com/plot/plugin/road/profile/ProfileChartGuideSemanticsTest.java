package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileChartGuideSemanticsTest {

    @Test
    void fitTerrainUsesTerrainTrendSemantics() {
        assertEquals(
            ProfileChartGuideSemantics.TERRAIN_TREND,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.FIT_TERRAIN));
    }

    @Test
    void autoSmoothUsesGuideLineSemantics() {
        assertEquals(
            ProfileChartGuideSemantics.GUIDE_LINE,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.AUTO_SMOOTH));
    }

    @Test
    void manualProfileSuppressesGuideLine() {
        assertEquals(
            ProfileChartGuideSemantics.NONE,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.MANUAL_PROFILE));
    }
}
