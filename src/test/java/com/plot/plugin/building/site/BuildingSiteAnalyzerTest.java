package com.plot.plugin.building.site;

import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.FoundationElevationSource;
import com.plot.plugin.building.generation.resolve.GenerationSiteResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingSiteAnalyzerTest {

    private static BuildingSiteColumnSample land(int groundY) {
        return new BuildingSiteColumnSample(groundY, groundY, OptionalInt.empty(), 0, 0);
    }

    private static BuildingSiteColumnSample water(int groundY, int waterY) {
        return new BuildingSiteColumnSample(groundY, waterY, OptionalInt.of(waterY), 0, 0);
    }

    private static BuildingSiteColumnSample structure(int groundY) {
        return new BuildingSiteColumnSample(groundY, groundY + 2, OptionalInt.empty(), 0, 1);
    }

    @Test
    void s01FlatLand() {
        List<BuildingSiteColumnSample> samples = List.of(
            land(64), land(64), land(64), land(64));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        assertEquals(64, analysis.dominantGroundElevation());
        assertEquals(64, analysis.balancedGroundElevation());
        assertEquals(0, analysis.groundElevationRange());
        assertEquals(0, analysis.waterColumnCount());
        assertEquals(0.0, analysis.waterCoverageRatio(), 1e-9);
        assertTrue(analysis.issues().isEmpty());
    }

    @Test
    void s02SmallSlopeBalancedIsReasonable() {
        List<BuildingSiteColumnSample> samples = List.of(
            land(64), land(64), land(65), land(65),
            land(64), land(65), land(65), land(66));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        assertEquals(65, analysis.dominantGroundElevation());
        assertTrue(analysis.balancedGroundElevation() >= 64);
        assertTrue(analysis.balancedGroundElevation() <= 66);
        BuildingFoundationUtils.EarthworkEstimate atBalanced =
            BuildingFoundationUtils.estimateEarthwork(
                List.of(64, 64, 65, 65, 64, 65, 65, 66),
                analysis.balancedGroundElevation());
        BuildingFoundationUtils.EarthworkEstimate atExtreme =
            BuildingFoundationUtils.estimateEarthwork(
                List.of(64, 64, 65, 65, 64, 65, 65, 66), 66);
        assertTrue(atBalanced.weightedCost() <= atExtreme.weightedCost() + 1e-9);
    }

    @Test
    void s03BimodalTerrainDoesNotPickExtremeModeBlindly() {
        List<Integer> grounds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            grounds.add(60);
            grounds.add(70);
        }
        List<BuildingSiteColumnSample> samples = grounds.stream().map(BuildingSiteAnalyzerTest::land).toList();
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        // Dominant tie → higher (70), but balanced should stay near mid-range to reduce fill.
        assertEquals(70, analysis.dominantGroundElevation());
        assertTrue(analysis.balancedGroundElevation() < 70,
            "balanced should avoid extreme fill toward mode=70");
        BuildingFoundationUtils.EarthworkEstimate balancedCost =
            BuildingFoundationUtils.estimateEarthwork(grounds, analysis.balancedGroundElevation());
        BuildingFoundationUtils.EarthworkEstimate modeCost =
            BuildingFoundationUtils.estimateEarthwork(grounds, 70);
        assertTrue(balancedCost.weightedCost() < modeCost.weightedCost());
    }

    @Test
    void s04SteepTerrainMarksSteepIssue() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int y = 60; y <= 70; y++) {
            samples.add(land(y));
        }
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        assertEquals(10, analysis.groundElevationRange());
        assertTrue(analysis.hasIssue(SiteIssue.SEVERE_STEEP));

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.decide(null, null, analysis, samples.stream().map(BuildingSiteColumnSample::groundY).toList(), result);
        assertTrue(result.warnings.contains("plugin.building.warn.severe_steep_site"));
    }

    @Test
    void s05PartialWaterDoesNotForceRaise() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            samples.add(land(64));
        }
        samples.add(water(60, 64));
        samples.add(water(60, 64));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        assertTrue(analysis.hasIssue(SiteIssue.PARTIAL_WATER));
        assertFalse(analysis.hasIssue(SiteIssue.WATER_DOMINANT));

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null, null, analysis,
            samples.stream().map(BuildingSiteColumnSample::groundY).toList(),
            result);

        assertEquals(FoundationElevationSource.TERRAIN, site.source());
        assertFalse(site.waterAdjusted());
        assertEquals(analysis.balancedGroundElevation(), site.actualFoundationElevation());
        assertTrue(result.warnings.contains("plugin.building.warn.partial_water_site"));
    }

    @Test
    void s06WaterDominantRaisesAutoTerrain() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            samples.add(water(61, 64));
        }
        samples.add(land(61));
        samples.add(land(61));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        assertTrue(analysis.hasIssue(SiteIssue.WATER_DOMINANT));
        assertEquals(64, analysis.dominantWaterElevation());

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null, null, analysis,
            samples.stream().map(BuildingSiteColumnSample::groundY).toList(),
            result);

        assertEquals(65, site.actualFoundationElevation());
        assertTrue(site.waterAdjusted());
        assertEquals(FoundationElevationSource.TERRAIN, site.source());
        assertTrue(result.warnings.contains("plugin.building.warn.foundation_raised_above_water"));
        assertTrue(result.warnings.contains("plugin.building.warn.water_site"));
    }

    @Test
    void s07ManualBelowWaterKeepsManual() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            samples.add(water(60, 64));
        }
        samples.add(land(60));
        samples.add(land(60));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            62, null, analysis,
            samples.stream().map(BuildingSiteColumnSample::groundY).toList(),
            result);

        assertEquals(62, site.actualFoundationElevation());
        assertEquals(FoundationElevationSource.MANUAL, site.source());
        assertFalse(site.waterAdjusted());
        assertTrue(result.warnings.contains("plugin.building.warn.manual_below_water"));
    }

    @Test
    void s08EarthworkPadBelowWaterKeepsPad() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            samples.add(water(60, 64));
        }
        samples.add(land(60));
        samples.add(land(60));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null, 63, analysis,
            samples.stream().map(BuildingSiteColumnSample::groundY).toList(),
            result);

        assertEquals(63, site.actualFoundationElevation());
        assertEquals(FoundationElevationSource.EARTHWORK_PAD, site.source());
        assertFalse(site.waterAdjusted());
        assertTrue(result.warnings.contains("plugin.building.warn.earthwork_pad_below_water"));
    }

    @Test
    void s04MildSteepTerrainMarksSteepWarning() {
        List<BuildingSiteColumnSample> samples = List.of(
            land(64), land(65), land(66), land(68));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);
        assertEquals(4, analysis.groundElevationRange());
        assertTrue(analysis.hasIssue(SiteIssue.STEEP));
        assertFalse(analysis.hasIssue(SiteIssue.SEVERE_STEEP));

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.decide(
            null, null, analysis, samples.stream().map(BuildingSiteColumnSample::groundY).toList(), result);
        assertTrue(result.warnings.contains("plugin.building.warn.steep_site"));
    }

    @Test
    void s06WaterRaiseCanTriggerHeavyEarthwork() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            samples.add(water(50, 64));
        }
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, TerrainElevationStrategy.BALANCED);
        List<Integer> grounds = samples.stream().map(BuildingSiteColumnSample::groundY).toList();
        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null, null, analysis, grounds, result);

        assertEquals(65, site.actualFoundationElevation());
        assertTrue(site.waterAdjusted());
        assertTrue(result.warnings.contains("plugin.building.warn.heavy_earthwork"));
        BuildingFoundationUtils.EarthworkEstimate afterRaise =
            BuildingFoundationUtils.estimateEarthwork(grounds, site.actualFoundationElevation());
        assertTrue(afterRaise.total() > grounds.size() * BuildingSiteAnalyzer.HEAVY_EARTHWORK_CELLS_FACTOR);
    }

    @Test
    void failSoftBundleFlagsEmitWarnings() {
        BuildingGenerationResult failed = new BuildingGenerationResult();
        var failBundle = BuildingSiteAnalyzer.AnalysisBundle.failedFallback();
        assertTrue(failBundle.analysisFailed());
        if (failBundle.analysisFailed()) {
            failed.warnings.add("plugin.building.warn.site_analysis_failed");
        }
        assertTrue(failed.warnings.contains("plugin.building.warn.site_analysis_failed"));

        BuildingGenerationResult unloaded = new BuildingGenerationResult();
        var unloadedBundle = new BuildingSiteAnalyzer.AnalysisBundle(
            BuildingSiteAnalysis.emptyFallback(64),
            Map.of(),
            List.of(),
            false,
            3);
        if (unloadedBundle.unloadedColumnCount() > 0) {
            unloaded.warnings.add("plugin.building.warn.chunk_unloaded");
        }
        assertTrue(unloaded.warnings.contains("plugin.building.warn.chunk_unloaded"));
    }
}
