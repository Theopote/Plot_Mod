package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.donutZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.tinyCompanionZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteEarthworkPipelineTest {

    @Test
    void executeProducesStableSiteGenerationResult() {
        EarthworkSite site = twoZoneSiteForCompose();
        site.addZone(donutZone("donut", 9, 3, 6, 60));
        site.addZone(tinyCompanionZone("companion"));

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(
            null, solidColumnSampler(terrain, STONE));
        EarthworkPipelineContext context = EarthworkPipelineContext.of(site, null, terrain);

        EarthworkGenerationResult first = pipelines.site().execute(context);
        EarthworkGenerationResult second = pipelines.site().execute(context);

        assertTrue(first.siteGeneration);
        assertEquals(first.volumeReport.geometricCutVolume(), second.volumeReport.geometricCutVolume());
        assertEquals(first.volumeReport.geometricFillVolume(), second.volumeReport.geometricFillVolume());
        assertEquals(first.placementRecords.size(), second.placementRecords.size());
        assertNotNull(first.designTerrainGrid);
        assertEquals(
            first.designTerrainGrid.get(5, 5).targetY(),
            second.designTerrainGrid.get(5, 5).targetY());
    }

    @Test
    void volumesMatchDesignGridAndPlacementCounts() {
        EarthworkSite site = twoZoneSiteForCompose();
        site.addZone(donutZone("donut", 9, 3, 6, 60));
        site.addZone(tinyCompanionZone("companion"));

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(
            null, solidColumnSampler(terrain, STONE));
        EarthworkGenerationResult result = pipelines.site().execute(
            EarthworkPipelineContext.of(site, null, terrain));

        assertNotNull(result.designTerrainGrid);
        SiteEarthworkReport fromGrid = EarthworkOptimizationSolver.collectZoneVolumes(
            result.designTerrainGrid, site);
        assertEquals(fromGrid.totals().geometricCutVolume(), result.volumeReport.geometricCutVolume());
        assertEquals(fromGrid.totals().geometricFillVolume(), result.volumeReport.geometricFillVolume());
        assertEquals(result.placementRecords.size(), result.volumeReport.totalChangedBlocks());
        assertEquals(result.volumeReport.totalChangedBlocks(), result.siteVolumeReport.totals().totalChangedBlocks());
        assertEquals(result.volumeReport.geometricCutVolume(), result.projectReport.volumeReport().geometricCutVolume());
        assertEquals(result.volumeReport.geometricFillVolume(), result.projectReport.volumeReport().geometricFillVolume());
        assertTrue(result.elevationVolumeCurve != null && !result.elevationVolumeCurve.isEmpty());
        assertNotEquals(result.elevationVolumeCurve.balanceY(), Integer.MIN_VALUE);
    }
}
