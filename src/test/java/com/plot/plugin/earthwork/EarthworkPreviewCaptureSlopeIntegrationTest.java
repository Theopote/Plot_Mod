package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkQuickEdge;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.isInsideClosedRect;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleCellCount;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玩家主路径集成：Capture 边界扩展 → Site 管线 → 放坡格参与设计与方量。
 */
class EarthworkPreviewCaptureSlopeIntegrationTest {

    private static final int PAD_MIN = 0;
    private static final int PAD_MAX = 9;
    private static final int GROUND_Y = 60;
    private static final int TARGET_Y = 64;
    private static final int MAX_REACH = 6;

    @Test
    void naturalSlopePadUsesExpandedCaptureThroughSitePipeline() {
        EarthworkSite site = slopedPadSite();
        GradingRegion region = site.getGradingZones().get("pad").getRegion();

        List<com.plot.api.geometry.Vec2d> padBoundary = site.getSiteBoundary();
        List<com.plot.api.geometry.Vec2d> captureBoundary =
            EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site);
        EarthworkSiteBoundaryUtils.CaptureBounds padBounds =
            EarthworkSiteBoundaryUtils.CaptureBounds.fromBoundary(padBoundary);
        EarthworkSiteBoundaryUtils.CaptureBounds captureBounds =
            EarthworkSiteBoundaryUtils.CaptureBounds.fromBoundary(captureBoundary);

        assertNotNull(padBounds);
        assertNotNull(captureBounds);
        assertTrue(captureBounds.minX() < padBounds.minX());
        assertTrue(captureBounds.minY() < padBounds.minY());
        assertTrue(captureBounds.maxX() > padBounds.maxX());
        assertTrue(captureBounds.maxY() > padBounds.maxY());

        TerrainSnapshot expandedTerrain = rectangleTerrain(-6, 15, -6, 15, GROUND_Y);
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(
            null, solidColumnSampler(expandedTerrain, STONE));
        EarthworkGenerationResult result = pipelines.site().execute(
            EarthworkPipelineContext.of(site, null, expandedTerrain, region));

        assertTrue(result.siteGeneration);
        assertNotNull(result.designTerrainGrid);
        assertTrue(result.existingTerrainSnapshot.covers(captureBoundary));
        assertTrue(result.calculationCellCount > rectangleCellCount(PAD_MIN, PAD_MAX, PAD_MIN, PAD_MAX));

        DesignTerrainGrid grid = result.designTerrainGrid;
        assertSlopedCellsOutsidePad(grid);
        assertToeReachesGround(grid);
        assertVolumeIncludesSlopeBand(result, grid);
    }

    private static EarthworkSite slopedPadSite() {
        GradingRegion region = new GradingRegion(
            "pad",
            rectangleOutline(PAD_MIN, PAD_MAX, PAD_MIN, PAD_MAX));
        region.setSurfaceMode(GradingSurfaceMode.LEVEL_PAD);
        region.setAutoBalance(false);
        region.setManualTargetElevation(TARGET_Y);
        region.setPreviewGridSize(1);
        region.setFillMaterial(DIRT);
        GradingZone pad = new GradingZone(region);
        EarthworkQuickEdge.NATURAL.applyTo(pad.getEdgeSettings());
        pad.getEdgeSettings().setMaximumReachBlocks(MAX_REACH);
        pad.syncDesignSurfaceToRegion();

        EarthworkSite site = new EarthworkSite("slope-site");
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();
        return site;
    }

    private static void assertSlopedCellsOutsidePad(DesignTerrainGrid grid) {
        DesignTerrainCell southBand = grid.get(5, -1);
        assertNotNull(southBand, "expected slope band south of pad");
        assertEquals("pad", southBand.zoneId());
        assertEquals(GROUND_Y, southBand.existingGroundY());
        assertTrue(southBand.targetY() > GROUND_Y);
        assertTrue(southBand.targetY() < TARGET_Y);

        DesignTerrainCell interior = grid.get(5, 5);
        assertNotNull(interior);
        assertEquals(TARGET_Y, interior.targetY());
    }

    private static void assertToeReachesGround(DesignTerrainGrid grid) {
        DesignTerrainCell toeSouth = grid.get(5, PAD_MIN - MAX_REACH);
        assertNotNull(toeSouth, "expected toe cell at maximum reach south of pad");
        assertEquals(GROUND_Y, toeSouth.existingGroundY());
        assertEquals(GROUND_Y, toeSouth.targetY(), "slope toe should meet existing ground");

        DesignTerrainCell toeWest = grid.get(PAD_MIN - MAX_REACH, 5);
        assertNotNull(toeWest, "expected toe cell at maximum reach west of pad");
        assertEquals(GROUND_Y, toeWest.targetY());
    }

    private static void assertVolumeIncludesSlopeBand(
            EarthworkGenerationResult result,
            DesignTerrainGrid grid) {
        long padOnlyFill = rectangleCellCount(PAD_MIN, PAD_MAX, PAD_MIN, PAD_MAX)
            * (TARGET_Y - GROUND_Y);
        assertTrue(result.volumeReport.geometricFillVolume() > padOnlyFill,
            "fill volume should include slope band beyond pad interior");

        long slopeFillBlocks = 0L;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (isInsideClosedRect(cell.worldX(), cell.worldZ(), PAD_MIN, PAD_MAX, PAD_MIN, PAD_MAX)) {
                continue;
            }
            if (cell.targetY() > cell.existingGroundY()) {
                slopeFillBlocks += cell.targetY() - cell.existingGroundY();
            }
        }
        assertTrue(slopeFillBlocks > 0L, "design grid should contain fill outside pad for slope band");
        assertTrue(result.volumeReport.geometricFillVolume() >= padOnlyFill + slopeFillBlocks);
    }
}
