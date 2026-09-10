package com.plot.plugin.earthwork.pipeline;

import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick 垂直边与自然坡应共用 Site 主路径，不因 {@code delegatesToLegacyGenerator} 切换算法。
 */
class SitePipelineUnifiedPathTest {

    @Test
    void singleZoneVerticalQuickUsesSitePipelineNotLegacyDelegate() {
        EarthworkSite site = new EarthworkSite();
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        site.addZone(new GradingZone(region));

        assertTrue(site.delegatesToLegacyGenerator(), "legacy-compatible shape");

        TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, 64);
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE, solidColumnSampler(terrain, STONE));
        EarthworkGenerationResult result = pipelines.site().execute(
            EarthworkPipelineContext.of(site, null, terrain, region));

        assertTrue(result.siteGeneration);
        assertNotNull(result.designTerrainGrid);
        assertTrue(result.designTerrainGrid.cellCount() > 0);
        assertTrue(result.volumeReport.geometricFillVolume() > 0L);
    }
}
