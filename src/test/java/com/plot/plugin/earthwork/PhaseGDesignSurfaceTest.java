package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceFacet;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PhaseGDesignSurfaceTest {

    @Test
    void matchExistingAppliesVerticalOffset() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(square(12));

        GradingZone zone = new GradingZone(square(12));
        zone.getDesignSurface().setKind(DesignSurfaceKind.MATCH_EXISTING);
        zone.getDesignSurface().setVerticalOffset(3);
        zone.syncDesignSurfaceToRegion();
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(6, 6), 6, 6, 65),
            new TerrainSnapshot.Column(new Vec2d(2, 2), 2, 2, 70)
        ));

        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluators =
            DesignSurfaceResolver.resolveZoneEvaluators(site, terrain, null, null);
        DesignSurfaceResolver.ZoneTargetEvaluator evaluator = evaluators.get(zone.getId());
        assertEquals(68, evaluator.evaluateAt(cell(6, 6, 65)));
        assertEquals(73, evaluator.evaluateAt(cell(2, 2, 70)));
    }

    @Test
    void multiPlaneUsesSmallestContainingFacet() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(square(12));

        GradingZone zone = new GradingZone(square(12));
        DesignSurface surface = zone.getDesignSurface();
        surface.setKind(DesignSurfaceKind.MULTI_PLANE);

        DesignSurfaceFacet outer = new DesignSurfaceFacet("outer", square(12));
        outer.getPlane().setKind(DesignSurfaceKind.LEVEL_PAD);
        outer.getPlane().setAutoBalance(false);
        outer.getPlane().setManualTargetElevation(60);

        DesignSurfaceFacet inner = new DesignSurfaceFacet("inner", square(6, 3, 3));
        inner.getPlane().setKind(DesignSurfaceKind.LEVEL_PAD);
        inner.getPlane().setAutoBalance(false);
        inner.getPlane().setManualTargetElevation(80);

        surface.setFacets(List.of(outer, inner));
        zone.syncDesignSurfaceToRegion();
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(10, 10), 10, 10, 65)
        ));

        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluators =
            DesignSurfaceResolver.resolveZoneEvaluators(site, terrain, null, null);
        DesignSurfaceResolver.ZoneTargetEvaluator evaluator = evaluators.get(zone.getId());

        assertEquals(80, evaluator.evaluateAt(cell(5, 5, 65)));
        assertEquals(60, evaluator.evaluateAt(cell(10, 10, 65)));
    }

    @Test
    void legacySurfaceModeIdsDeserializeToNewNames() {
        assertEquals(GradingSurfaceMode.LEVEL_PAD, GradingSurfaceMode.fromId("FLAT"));
        assertEquals(GradingSurfaceMode.SINGLE_SLOPE_PLANE, GradingSurfaceMode.fromId("FIXED_SLOPE"));
        assertEquals(GradingSurfaceMode.BEST_FIT_PLANE, GradingSurfaceMode.fromId("FIT_SLOPE"));
        assertEquals(DesignSurfaceKind.MATCH_EXISTING, DesignSurfaceKind.fromId("MATCH_EXISTING"));
    }

    @Test
    void matchExistingAndMultiPlaneBypassLegacyGenerator() {
        GradingZone matchZone = new GradingZone(square(8));
        matchZone.getDesignSurface().setKind(DesignSurfaceKind.MATCH_EXISTING);
        matchZone.syncDesignSurfaceToRegion();
        assertFalse(matchZone.isDelegatableToLegacyGenerator());

        GradingZone multiZone = new GradingZone(square(8));
        multiZone.getRegion().setSurfaceMode(GradingSurfaceMode.MULTI_PLANE);
        multiZone.getDesignSurface().setKind(DesignSurfaceKind.MULTI_PLANE);
        multiZone.syncDesignSurfaceToRegion();
        assertFalse(multiZone.isDelegatableToLegacyGenerator());
    }

    private static DesignTerrainCell cell(int worldX, int worldZ, int existingY) {
        return new DesignTerrainCell(worldX, worldZ, new Vec2d(worldX, worldZ), existingY);
    }

    private static List<Vec2d> square(double size) {
        return square(size, 0, 0);
    }

    private static List<Vec2d> square(double size, double originX, double originZ) {
        return List.of(
            new Vec2d(originX, originZ),
            new Vec2d(originX + size, originZ),
            new Vec2d(originX + size, originZ + size),
            new Vec2d(originX, originZ + size));
    }
}
