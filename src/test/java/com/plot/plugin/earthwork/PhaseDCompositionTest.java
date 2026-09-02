package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhaseDCompositionTest {

    @Test
    void breaklineSideOverridesPriorityInOverlap() {
        EarthworkSite site = overlappingFlatZones(60, 50, 75, 100);
        Breakline breakline = new Breakline("bl-1");
        breakline.setPoints(List.of(new Vec2d(5, 0), new Vec2d(5, 10)));
        breakline.setLeftZoneId(site.getGradingZones().values().stream()
            .filter(zone -> zone.getPriority() == 50)
            .findFirst()
            .orElseThrow()
            .getId());
        breakline.setRightZoneId(site.getGradingZones().values().stream()
            .filter(zone -> zone.getPriority() == 100)
            .findFirst()
            .orElseThrow()
            .getId());
        site.addBreakline(breakline);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(4, 5), 4, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(6, 5), 6, 5, 65)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(60, grid.get(4, 5).targetY());
        assertEquals(75, grid.get(6, 5).targetY());
    }

    @Test
    void boundaryBlendInterpolatesRunnerUpElevation() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone yard = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        yard.setPriority(50);
        yard.getRegion().setAutoBalance(false);
        yard.getRegion().setManualTargetElevation(60);

        GradingZone pad = new GradingZone(List.of(
            new Vec2d(2, 2),
            new Vec2d(8, 2),
            new Vec2d(8, 8),
            new Vec2d(2, 8)
        ));
        pad.setPriority(100);
        pad.getRegion().setAutoBalance(false);
        pad.getRegion().setManualTargetElevation(75);

        site.addZone(yard);
        site.addZone(pad);
        site.getCompositionPolicy().setBlendWidthBlocks(3);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(60, grid.get(2, 5).targetY());
        assertEquals(75, grid.get(5, 5).targetY());
    }

    @Test
    void breaklineClassifierReturnsNullOutsideInfluenceBand() {
        Breakline breakline = new Breakline("bl-1");
        breakline.setPoints(List.of(new Vec2d(5, 0), new Vec2d(5, 10)));
        breakline.setLeftZoneId("left");
        breakline.setRightZoneId("right");

        assertNull(BreaklineClassifier.resolveMandatedZoneId(new Vec2d(8, 5), List.of(breakline), 1.0));
        assertEquals("right", BreaklineClassifier.resolveMandatedZoneId(new Vec2d(6, 5), List.of(breakline), 2.0));
    }

    private static EarthworkSite overlappingFlatZones(int yardElev, int yardPriority, int padElev, int padPriority) {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone yard = new GradingZone("yard", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        yard.setPriority(yardPriority);
        yard.getRegion().setAutoBalance(false);
        yard.getRegion().setManualTargetElevation(yardElev);

        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        pad.setPriority(padPriority);
        pad.getRegion().setAutoBalance(false);
        pad.getRegion().setManualTargetElevation(padElev);

        site.addZone(yard);
        site.addZone(pad);
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);
        site.setCompositionPolicy(policy);
        return site;
    }
}
