package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.ResolutionResult;
import com.plot.plugin.earthwork.design.ResolvedDesignSource;
import com.plot.plugin.earthwork.design.ResolvedDesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedDesignSurfaceTest {

    @Test
    void buildingPadResolvesWithLockedPolicyAndBuildingSource() {
        EarthworkSite site = new EarthworkSite();
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.setBuildingFootprintRef("b1");
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        site.addZone(pad);

        BuildingFootprint footprint = new BuildingFootprint("b1", pad.getOuterPoints(), false);
        footprint.setManualBaseElevation(72);
        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70)));

        Map<String, ResolvedDesignSurface> resolved = DesignSurfaceResolver.resolveZoneSurfaces(
            site, terrain, id -> footprint, null);

        ResolvedDesignSurface surface = resolved.get("pad");
        assertEquals(ResolvedDesignSource.BUILDING_BASE_ELEVATION, surface.source());
        assertEquals(ResolutionResult.Status.RESOLVED, surface.status());
        assertEquals(VerticalAdjustmentPolicy.Mode.LOCKED, surface.verticalPolicy().getMode());
        assertFalse(surface.isSolverVariable());
        assertEquals(72, surface.evaluateAt(terrainColumnCell(5, 5, 70)));
    }

    @Test
    void buildingLinkedPitIsDerivedAndNotSolverVariable() {
        EarthworkSite site = new EarthworkSite();
        GradingZone pit = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.setBuildingFootprintRef("b1");
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.getDesignSurface().setBasementFloorDepth(4);
        pit.getDesignSurface().setWorkingMarginBlocks(0);
        site.addZone(pit);

        BuildingFootprint footprint = new BuildingFootprint("b1", pit.getOuterPoints(), false);
        footprint.setManualBaseElevation(72);
        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70)));

        Map<String, ResolvedDesignSurface> resolved = DesignSurfaceResolver.resolveZoneSurfaces(
            site, terrain, id -> footprint, null);

        ResolvedDesignSurface surface = resolved.get("pit");
        assertEquals(ResolvedDesignSource.DERIVED_BUILDING_PIT, surface.source());
        assertEquals(ResolutionResult.Status.RESOLVED, surface.status());
        assertEquals(VerticalAdjustmentPolicy.Mode.DERIVED, surface.verticalPolicy().getMode());
        assertFalse(surface.isSolverVariable());
        assertEquals(68, surface.evaluateAt(terrainColumnCell(5, 5, 70)));
    }

    @Test
    void landscapeBestFitIsBoundedSolverVariable() {
        EarthworkSite site = new EarthworkSite();
        GradingZone landscape = new GradingZone("lawn", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        landscape.setType(GradingZoneType.LANDSCAPE);
        site.addZone(landscape);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70)));

        Map<String, ResolvedDesignSurface> resolved = DesignSurfaceResolver.resolveZoneSurfaces(
            site, terrain, id -> null, null);

        ResolvedDesignSurface surface = resolved.get("lawn");
        assertEquals(ResolvedDesignSource.BEST_FIT, surface.source());
        assertEquals(ResolutionResult.Status.RESOLVED, surface.status());
        assertEquals(VerticalAdjustmentPolicy.Mode.ADJUSTABLE, surface.verticalPolicy().getMode());
        assertEquals(-3, surface.verticalPolicy().getMinOffset());
        assertEquals(3, surface.verticalPolicy().getMaxOffset());
        assertTrue(surface.isSolverVariable());
    }

    @Test
    void composeResultExposesResolvedSurfaces() {
        EarthworkSite site = new EarthworkSite();
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(8, 0), new Vec2d(8, 8), new Vec2d(0, 8)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.getDesignSurface().setElevation(71);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(4, 4), 4, 4, 70)));

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(site, terrain, null);
        assertTrue(result.resolvedSurfaces().containsKey("pad"));
        assertEquals(
            ResolvedDesignSource.MANUAL_CONSTANT,
            result.resolvedSurfaces().get("pad").source());
        assertEquals(result.zoneEvaluators().keySet(), result.resolvedSurfaces().keySet());
    }

    private static com.plot.plugin.earthwork.grading.DesignTerrainCell terrainColumnCell(
            int x, int z, int groundY) {
        return new com.plot.plugin.earthwork.grading.DesignTerrainCell(
            x, z, new Vec2d(x + 0.5, z + 0.5), groundY);
    }
}
