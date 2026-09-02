package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseFEdgeTreatmentTest {

    @Test
    void cutFillSlopeRaisesTargetOutsideFlatPad() {
        EarthworkSite site = createSite();
        GradingZone pad = createZone("pad", List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8)));
        pad.getDesignSurface().setManualTargetElevation(64);
        pad.getDesignSurface().setAutoBalance(false);
        pad.syncDesignSurfaceToRegion();
        ZoneEdgeSettings edgeSettings = pad.getEdgeSettings();
        edgeSettings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edgeSettings.setCutSlopePitchRatio(1);
        edgeSettings.setMaximumReachBlocks(4);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(3.5, 5), 3, 5, 70)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(64, grid.get(5, 5).targetY());
        assertTrue(grid.get(3, 5).targetY() > 64);
        assertTrue(grid.get(3, 5).targetY() < 70);
        assertEquals("pad", grid.get(3, 5).zoneId());
    }

    @Test
    void matchExistingPreservesGroundNearInteriorEdge() {
        EarthworkSite site = createSite();
        GradingZone pad = createZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.getDesignSurface().setManualTargetElevation(68);
        pad.getDesignSurface().setAutoBalance(false);
        pad.syncDesignSurfaceToRegion();
        ZoneEdgeSettings edgeSettings = pad.getEdgeSettings();
        edgeSettings.setDefaultTreatment(EdgeTreatment.MATCH_EXISTING);
        edgeSettings.setMaximumReachBlocks(2);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 72),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 60)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(72, grid.get(0, 0).targetY());
        assertEquals(68, grid.get(5, 5).targetY());
    }

    @Test
    void legacyResolverAppliesExteriorCutSlope() {
        ZoneEdgeSettings settings = new ZoneEdgeSettings();
        settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        settings.setCutSlopePitchRatio(1);
        settings.setMaximumReachBlocks(4);

        List<Vec2d> outline = List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8));
        int target = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
            new Vec2d(3.5, 5),
            70,
            64,
            outline,
            settings);
        assertTrue(target > 64);
        assertTrue(target < 70);
    }

    @Test
    void edgeSettingsRoundTripThroughProjectJson() throws Exception {
        EarthworkSite site = createSite();
        GradingZone zone = createZone("z1", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        settings.setCutSlopePitchRatio(2);
        settings.setFillSlopePitchNumerator(3);
        settings.setFillSlopePitchDenominator(2);
        settings.setMaximumReachBlocks(6);
        settings.setBenchWidthBlocks(2);
        site.addZone(zone);

        com.plot.plugin.earthwork.model.EarthworkProject project =
            new com.plot.plugin.earthwork.model.EarthworkProject();
        project.addSite(site);
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("earthwork-phase-f", ".json");
        project.saveTo(temp);

        com.plot.plugin.earthwork.model.EarthworkProject loaded =
            com.plot.plugin.earthwork.model.EarthworkProject.loadFrom(temp);
        GradingZone loadedZone = loaded.getActiveSite().getGradingZones().get("z1");
        ZoneEdgeSettings loadedSettings = loadedZone.getEdgeSettings();
        assertEquals(EdgeTreatment.CUT_FILL_SLOPE, loadedSettings.getDefaultTreatment());
        assertEquals(2, loadedSettings.getCutSlopePitchRatio());
        assertEquals(3, loadedSettings.getFillSlopePitchNumerator());
        assertEquals(2, loadedSettings.getFillSlopePitchDenominator());
        assertEquals(6, loadedSettings.getMaximumReachBlocks());
        assertEquals(2, loadedSettings.getBenchWidthBlocks());

        java.nio.file.Files.deleteIfExists(temp);
    }

    private static EarthworkSite createSite() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(12, 0), new Vec2d(12, 12), new Vec2d(0, 12)));
        return site;
    }

    private static GradingZone createZone(String id, List<Vec2d> points) {
        GradingZone zone = new GradingZone(id, points);
        zone.setName(id);
        DesignSurface surface = zone.getDesignSurface();
        surface.setAutoBalance(false);
        zone.syncDesignSurfaceToRegion();
        return zone;
    }
}
