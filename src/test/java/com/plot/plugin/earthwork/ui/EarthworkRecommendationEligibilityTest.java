package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EarthworkRecommendationEligibilityTest {
    @Test void singleFlatPadCanApplyAbsoluteRecommendedHeight() {
        var site = new EarthworkSite("site");
        var zone = zone("pad");
        site.addZone(zone);
        assertTrue(EarthworkInsightCharts.canApplyRecommendedElevation(site, zone.getRegion()));
    }

    @Test void multiZoneDesignCannotBeReplacedByOneRegionsHeight() {
        var site = new EarthworkSite("site");
        var zone = zone("pad");
        site.addZone(zone);
        site.addZone(zone("other"));
        assertFalse(EarthworkInsightCharts.canApplyRecommendedElevation(site, zone.getRegion()));
    }

    @Test void slopedDesignCannotApplyAbsoluteFlatHeight() {
        var site = new EarthworkSite("site");
        var zone = zone("pad");
        zone.getRegion().setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        site.addZone(zone);
        assertFalse(EarthworkInsightCharts.canApplyRecommendedElevation(site, zone.getRegion()));
    }

    private GradingZone zone(String id) {
        return new GradingZone(id, List.of(new Vec2d(0,0), new Vec2d(2,0), new Vec2d(2,2), new Vec2d(0,2)));
    }
}
