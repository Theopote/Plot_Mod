package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.ExcavationPitSurfaceEvaluator;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkWorldScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT =
        SnapshotCoordinateService.uniformScale(4.0);

    private static final List<Vec2d> PAD = List.of(
        new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8));

    private static final List<Vec2d> SQUARE = List.of(
        new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));

    @Test
    void blocksToCanvasScalesWithProjection() {
        EarthworkCanvasScale identity = EarthworkCanvasScale.capture(IdentityCoordinateService.INSTANCE, PAD);
        EarthworkCanvasScale scaled = EarthworkCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, PAD);

        assertEquals(6.0, identity.uniformBlocksToCanvas(6, PAD), 1e-6);
        assertEquals(1.5, scaled.uniformBlocksToCanvas(6, PAD), 1e-6);
    }

    @Test
    void captureBoundaryExpansionUsesWorldBlocks() {
        EarthworkSite site = new EarthworkSite("site");
        GradingZone pad = new GradingZone("pad", PAD);
        ZoneEdgeSettings edge = pad.getEdgeSettings();
        edge.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edge.setMaximumReachBlocks(6);
        site.addZone(pad);

        EarthworkCanvasScale identity = EarthworkCanvasScale.capture(IdentityCoordinateService.INSTANCE, PAD);
        EarthworkCanvasScale scaled = EarthworkCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, PAD);

        List<Vec2d> identityCapture = EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site, identity);
        List<Vec2d> scaledCapture = EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site, scaled);

        double identityMargin = worldMarginAlongX(PAD, identityCapture, IdentityCoordinateService.INSTANCE);
        double scaledMargin = worldMarginAlongX(PAD, scaledCapture, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(6.0, identityMargin, 0.5);
        assertEquals(6.0, scaledMargin, 0.5);
    }

    @Test
    void legacyExteriorSlopeIsScaleInvariantAtSameWorldDistance() {
        ZoneEdgeSettings settings = new ZoneEdgeSettings();
        settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        settings.setCutSlopePitchRatio(1);
        settings.setMaximumReachBlocks(4);

        EarthworkCanvasScale identity = EarthworkCanvasScale.capture(IdentityCoordinateService.INSTANCE, PAD);
        EarthworkCanvasScale scaled = EarthworkCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, PAD);

        int identityTarget = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
            new Vec2d(3.5, 5), 70, 64, PAD, settings, identity);
        int scaledTarget = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
            new Vec2d(3.875, 5), 70, 64, PAD, settings, scaled);

        assertEquals(identityTarget, scaledTarget);
        assertTrue(identityTarget > 64);
        assertTrue(identityTarget < 70);
    }

    @Test
    void excavationPitWorkingMarginUsesWorldBlocks() {
        EarthworkCanvasScale identity = EarthworkCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE);
        EarthworkCanvasScale scaled = EarthworkCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE);

        int identityNearEdge = ExcavationPitSurfaceEvaluator.evaluateTargetY(
            new Vec2d(1, 5), SQUARE, 50, 2, 1, identity);
        int scaledNearEdge = ExcavationPitSurfaceEvaluator.evaluateTargetY(
            new Vec2d(0.25, 5), SQUARE, 50, 2, 1, scaled);

        assertEquals(identityNearEdge, scaledNearEdge);
        assertTrue(identityNearEdge > 50);
    }

    @Test
    void exteriorSlopeComposeIsScaleInvariantAtSameWorldDistance() {
        EarthworkSite site = createExteriorSlopeSite();
        TerrainSnapshot identityTerrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(3.5, 5), 3, 5, 70)));
        TerrainSnapshot scaledTerrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 20, 20, 70),
            new TerrainSnapshot.Column(new Vec2d(3.875, 5), 15, 20, 70)));

        DesignTerrainGrid identityGrid = DesignTerrainComposer.compose(
            site, identityTerrain, IdentityCoordinateService.INSTANCE).grid();
        DesignTerrainGrid scaledGrid = DesignTerrainComposer.compose(
            site, scaledTerrain, FOUR_BLOCKS_PER_CANVAS_UNIT).grid();

        assertEquals(64, identityGrid.get(5, 5).targetY());
        assertEquals(64, scaledGrid.get(20, 20).targetY());
        assertEquals(identityGrid.get(3, 5).targetY(), scaledGrid.get(15, 20).targetY());
    }

    private static EarthworkSite createExteriorSlopeSite() {
        EarthworkSite site = new EarthworkSite("site");
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(12, 0), new Vec2d(12, 12), new Vec2d(0, 12)));

        GradingZone pad = new GradingZone("pad", PAD);
        pad.getDesignSurface().setManualTargetElevation(64);
        pad.getDesignSurface().setAutoBalance(false);
        pad.syncDesignSurfaceToRegion();
        ZoneEdgeSettings edgeSettings = pad.getEdgeSettings();
        edgeSettings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edgeSettings.setCutSlopePitchRatio(1);
        edgeSettings.setMaximumReachBlocks(4);
        site.addZone(pad);
        return site;
    }

    private static double worldMarginAlongX(
            List<Vec2d> pad,
            List<Vec2d> capture,
            ICoordinateService coordinates) {
        double padMinX = Double.POSITIVE_INFINITY;
        double captureMinX = Double.POSITIVE_INFINITY;
        for (Vec2d point : pad) {
            padMinX = Math.min(padMinX, coordinates.canvasToMinecraftWorld(point).x);
        }
        for (Vec2d point : capture) {
            captureMinX = Math.min(captureMinX, coordinates.canvasToMinecraftWorld(point).x);
        }
        return padMinX - captureMinX;
    }
}
