package com.plot.plugin.building.generation.resolve;

import com.plot.plugin.building.benchmark.DistrictMassingFixtures;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGenerationContextFactorySampledSiteTest {

    @Test
    void sampledSitePathAttachesTerrainPreviewWithVariation() {
        BuildingGenerationResult flat = new BuildingGenerationResult();
        BuildingGenerationContext.forTesting(
            DistrictMassingFixtures.massingFootprint(0, 3),
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            flat);
        assertNull(flat.sitePreview);

        BuildingGenerationResult sampled = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTestingWithSampledSite(
            DistrictMassingFixtures.massingFootprint(0, 3),
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            sampled);

        assertTrue(context.isValid());
        assertNotNull(sampled.sitePreview);
        assertTrue(sampled.sitePreview.maxGroundElevation() >= sampled.sitePreview.minGroundElevation());
    }
}
