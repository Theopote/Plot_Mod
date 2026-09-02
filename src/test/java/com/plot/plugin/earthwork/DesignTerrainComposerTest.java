package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignTerrainComposerTest {

    @Test
    void higherPriorityZoneWinsInOverlap() {
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
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(1, 1), 1, 1, 65)
        ));

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(site, terrain, null);
        DesignTerrainCell inner = result.grid().get(5, 5);
        DesignTerrainCell outerOnly = result.grid().get(1, 1);

        assertEquals(75, inner.targetY());
        assertEquals(pad.getId(), inner.zoneId());
        assertEquals(60, outerOnly.targetY());
        assertEquals(yard.getId(), outerOnly.zoneId());
    }

    @Test
    void exclusionZonePreservesExistingGround() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(50);
        site.addZone(zone);

        ExclusionZone exclusion = new ExclusionZone("ex-1");
        exclusion.setOuterPoints(List.of(
            new Vec2d(3, 3),
            new Vec2d(7, 3),
            new Vec2d(7, 7),
            new Vec2d(3, 7)
        ));
        exclusion.setMode(ExclusionZone.MODE_PRESERVE_EXISTING);
        site.setExclusionZones(List.of(exclusion));

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 68),
            new TerrainSnapshot.Column(new Vec2d(1, 1), 1, 1, 62)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        DesignTerrainCell excluded = grid.get(5, 5);
        DesignTerrainCell graded = grid.get(1, 1);

        assertTrue(excluded.excluded());
        assertEquals(68, excluded.targetY());
        assertEquals(50, graded.targetY());
    }

    @Test
    void smallerAreaWinsWhenPriorityTied() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone large = new GradingZone("zone-large", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        large.setPriority(50);
        large.getRegion().setAutoBalance(false);
        large.getRegion().setManualTargetElevation(60);

        GradingZone small = new GradingZone("zone-small", List.of(
            new Vec2d(2, 2),
            new Vec2d(8, 2),
            new Vec2d(8, 8),
            new Vec2d(2, 8)
        ));
        small.setPriority(50);
        small.getRegion().setAutoBalance(false);
        small.getRegion().setManualTargetElevation(72);

        site.addZone(large);
        site.addZone(small);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)
        ));

        DesignTerrainCell cell = DesignTerrainComposer.compose(site, terrain, null).grid().get(5, 5);
        assertEquals(72, cell.targetY());
        assertEquals("zone-small", cell.zoneId());
    }
}
