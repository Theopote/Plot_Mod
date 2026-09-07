package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.terrain.SiteTerrainCapture;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkCaptureBoundaryTest {

    @Test
    void resolveCaptureBoundaryExpandsByMaximumReach() {
        EarthworkSite site = new EarthworkSite("site");
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8)));
        ZoneEdgeSettings edge = pad.getEdgeSettings();
        edge.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edge.setMaximumReachBlocks(6);
        site.addZone(pad);

        List<Vec2d> capture = EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site);
        EarthworkSiteBoundaryUtils.CaptureBounds bounds =
            EarthworkSiteBoundaryUtils.CaptureBounds.fromBoundary(capture);

        assertNotNull(bounds);
        assertEquals(-2.0, bounds.minX(), 1e-6);
        assertEquals(-2.0, bounds.minY(), 1e-6);
        assertEquals(14.0, bounds.maxX(), 1e-6);
        assertEquals(14.0, bounds.maxY(), 1e-6);
    }

    @Test
    void terrainSnapshotCoversDetectsInsufficientExtent() {
        List<Vec2d> innerBoundary = List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));
        TerrainSnapshot snapshot = EarthworkTestFixtures.rectangleTerrain(-2, 13, -2, 13, 64);

        assertTrue(snapshot.covers(innerBoundary));
        assertFalse(snapshot.covers(
            EarthworkSiteBoundaryUtils.expandAxisAlignedBoundary(innerBoundary, 4)));
    }

    @Test
    void captureSiteReusesSnapshotOnlyWhenCoverageIsSufficient() {
        List<Vec2d> padOnly = List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8));
        List<Vec2d> withSlopeBand = EarthworkSiteBoundaryUtils.expandAxisAlignedBoundary(padOnly, 4);

        TerrainSnapshot padSnapshot = EarthworkTestFixtures.rectangleTerrain(4, 7, 4, 7, 64);
        TerrainSnapshot expandedSnapshot = EarthworkTestFixtures.rectangleTerrain(0, 11, 0, 11, 64);
        EarthworkSite site = new EarthworkSite("site");

        assertSame(padSnapshot, SiteTerrainCapture.captureSite(
            null, site, null, padOnly, padSnapshot));
        assertSame(expandedSnapshot, SiteTerrainCapture.captureSite(
            null, site, null, withSlopeBand, expandedSnapshot));
        assertFalse(padSnapshot.covers(withSlopeBand));
    }
}
