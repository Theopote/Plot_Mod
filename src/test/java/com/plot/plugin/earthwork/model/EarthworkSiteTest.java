package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkSiteTest {

    @Test
    void singleZoneDelegatesToLegacyGenerator() {
        EarthworkSite site = new EarthworkSite();
        site.addZone(new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10)
        )));
        assertTrue(site.delegatesToLegacyGenerator());
        assertNotNull(site.getLegacyDelegateZone());
    }

    @Test
    void multipleZonesDoNotDelegate() {
        EarthworkSite site = new EarthworkSite();
        site.addZone(new GradingZone(List.of(new Vec2d(0, 0), new Vec2d(5, 0), new Vec2d(5, 5))));
        site.addZone(new GradingZone(List.of(new Vec2d(6, 0), new Vec2d(10, 0), new Vec2d(10, 5))));
        assertTrue(!site.delegatesToLegacyGenerator());
    }

    @Test
    void siteBoundaryRecomputedFromZones() {
        EarthworkSite site = new EarthworkSite();
        site.addZone(new GradingZone(List.of(
            new Vec2d(2, 3),
            new Vec2d(12, 3),
            new Vec2d(12, 13),
            new Vec2d(2, 13)
        )));
        site.recomputeSiteBoundaryFromZones();
        List<Vec2d> boundary = site.getSiteBoundary();
        assertEquals(4, boundary.size());
        assertEquals(2.0, boundary.get(0).x, 1e-6);
        assertEquals(3.0, boundary.get(0).y, 1e-6);
        assertEquals(12.0, boundary.get(1).x, 1e-6);
        assertEquals(13.0, boundary.get(3).y, 1e-6);
    }
}
