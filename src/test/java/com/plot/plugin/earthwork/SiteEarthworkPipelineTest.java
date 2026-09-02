package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.donutZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.tinyCompanionZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteEarthworkPipelineTest {

    @Test
    void executeProducesSameResultAsGeneratorGenerateSite() {
        EarthworkSite site = twoZoneSiteForCompose();
        site.addZone(donutZone("donut", 9, 3, 6, 60));
        site.addZone(tinyCompanionZone("companion"));

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        EarthworkGenerator generator = new EarthworkGenerator(null, solidColumnSampler(terrain, STONE));
        EarthworkPipelineContext context = EarthworkPipelineContext.of(site, null, terrain);

        EarthworkGenerator.EarthworkGenerationResult fromPipeline = generator.sitePipeline().execute(context);
        EarthworkGenerator.EarthworkGenerationResult fromGenerator = generator.generateSite(site, null, terrain, null);

        assertTrue(fromPipeline.siteGeneration);
        assertEquals(fromGenerator.volumeReport.geometricCutVolume(), fromPipeline.volumeReport.geometricCutVolume());
        assertEquals(fromGenerator.volumeReport.geometricFillVolume(), fromPipeline.volumeReport.geometricFillVolume());
        assertEquals(fromGenerator.placementRecords.size(), fromPipeline.placementRecords.size());
        assertNotNull(fromPipeline.designTerrainGrid);
        assertEquals(
            fromGenerator.designTerrainGrid.get(5, 5).targetY(),
            fromPipeline.designTerrainGrid.get(5, 5).targetY());
    }
}
