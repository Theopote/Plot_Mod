package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneOverlapAnalyzerTest {

    @Test
    void detectsOverlappingZones() {
        EarthworkSite site = new EarthworkSite();

        GradingZone yard = new GradingZone("yard", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        yard.setPriority(50);

        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(5, 0),
            new Vec2d(15, 0),
            new Vec2d(15, 10),
            new Vec2d(5, 10)
        ));
        pad.setPriority(100);

        site.addZone(yard);
        site.addZone(pad);

        List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps = ZoneOverlapAnalyzer.findOverlaps(site);
        assertEquals(1, overlaps.size());
        assertEquals("pad", overlaps.getFirst().winnerZoneId());
        assertTrue(overlaps.getFirst().overlapCells() > 0);
    }

    @Test
    void ignoresSeparatedZones() {
        EarthworkSite site = new EarthworkSite();

        site.addZone(new GradingZone("left", List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        )));
        site.addZone(new GradingZone("right", List.of(
            new Vec2d(6, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 4),
            new Vec2d(6, 4)
        )));

        assertTrue(ZoneOverlapAnalyzer.findOverlaps(site).isEmpty());
    }

    @Test
    void findOverlapsInvolvingFiltersByZoneId() {
        EarthworkSite site = new EarthworkSite();
        site.addZone(new GradingZone("a", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)
        )));
        site.addZone(new GradingZone("b", List.of(
            new Vec2d(5, 0), new Vec2d(15, 0), new Vec2d(15, 10), new Vec2d(5, 10)
        )));
        site.addZone(new GradingZone("c", List.of(
            new Vec2d(20, 0), new Vec2d(25, 0), new Vec2d(25, 5), new Vec2d(20, 5)
        )));

        assertEquals(1, ZoneOverlapAnalyzer.findOverlapsInvolving(site, "a").size());
        assertTrue(ZoneOverlapAnalyzer.findOverlapsInvolving(site, "c").isEmpty());
    }
}
