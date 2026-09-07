package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationSiteResolverSiteAnalysisSkipTest {
    private static final List<Vec2d> RECT = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 6),
        new Vec2d(0, 6));

    @Test
    void mustSkipWhenAnalysisFailedAndNoManualOrPad() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        var bundle = BuildingSiteAnalyzer.AnalysisBundle.failedFallback();

        assertTrue(GenerationSiteResolver.mustSkipDueToFailedSiteAnalysis(
            definition, footprint, bundle));
    }

    @Test
    void noSkipWhenManualElevationSet() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        footprint.setManualBaseElevation(72);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        var bundle = BuildingSiteAnalyzer.AnalysisBundle.failedFallback();

        assertFalse(GenerationSiteResolver.mustSkipDueToFailedSiteAnalysis(
            definition, footprint, bundle));
    }

    @Test
    void noSkipWhenAnalysisSucceeded() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        var bundle = BuildingSiteAnalyzer.AnalysisBundle.fallback();

        assertFalse(GenerationSiteResolver.mustSkipDueToFailedSiteAnalysis(
            definition, footprint, bundle));
    }

    @Test
    void skipBundleMarksGenerationSkipped() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        var bundle = BuildingSiteAnalyzer.AnalysisBundle.failedFallback();
        GenerationSiteResolver.SiteResolveBundle skipped = GenerationSiteResolver.SiteResolveBundle.skip(bundle);

        assertTrue(skipped.generationSkipped());
        assertTrue(result.warnings.isEmpty());
    }
}
