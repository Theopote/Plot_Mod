package com.plot.plugin.building.generation.resolve;

import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import com.plot.api.building.BuildingPadElevationMode;
import com.plot.api.building.BuildingPadElevationStatus;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationSiteResolverPadUnresolvedTest {

    @Test
    void unresolvedEarthworkOwnedPadUsesTerrainWithWarning() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        List<BuildingSiteColumnSample> samples = List.of(
            BuildingSiteAnalyzerTestHelper.land(68),
            BuildingSiteAnalyzerTestHelper.land(70));
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            samples, com.plot.plugin.building.site.TerrainElevationStrategy.BALANCED);
        var padLink = new BuildingPadElevationStatus(
            BuildingPadElevationMode.EARTHWORK_OWNED,
            "Pad A",
            "Site 1",
            null);

        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null,
            null,
            padLink,
            analysis,
            samples.stream().map(BuildingSiteColumnSample::groundY).toList(),
            result);

        assertEquals(FoundationElevationSource.TERRAIN, site.source());
        assertEquals(analysis.balancedGroundElevation(), site.actualFoundationElevation());
        assertTrue(result.warnings.contains("plugin.building.warn.earthwork_pad_unresolved_using_terrain"));
    }

    @Test
    void resolvedPadDoesNotEmitUnresolvedWarning() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            List.of(BuildingSiteAnalyzerTestHelper.land(64)),
            com.plot.plugin.building.site.TerrainElevationStrategy.BALANCED);

        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            null,
            72,
            new BuildingPadElevationStatus(
                BuildingPadElevationMode.EARTHWORK_OWNED,
                "Pad A",
                "Site 1",
                72),
            analysis,
            List.of(64),
            result);

        assertEquals(FoundationElevationSource.EARTHWORK_PAD, site.source());
        assertFalse(result.warnings.contains("plugin.building.warn.earthwork_pad_unresolved_using_terrain"));
        assertTrue(result.warnings.contains("plugin.building.warn.using_earthwork_pad_elevation"));
    }

    @Test
    void manualElevationOverridesUnresolvedPadLink() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            List.of(BuildingSiteAnalyzerTestHelper.land(64)),
            com.plot.plugin.building.site.TerrainElevationStrategy.BALANCED);
        var padLink = new BuildingPadElevationStatus(
            BuildingPadElevationMode.EARTHWORK_OWNED,
            "Pad A",
            "Site 1",
            null);

        GenerationSiteResolver.ResolvedSiteElevation site = GenerationSiteResolver.decide(
            80,
            null,
            padLink,
            analysis,
            List.of(64),
            result);

        assertEquals(FoundationElevationSource.MANUAL, site.source());
        assertFalse(result.warnings.contains("plugin.building.warn.earthwork_pad_unresolved_using_terrain"));
    }

    @Test
    void isEarthworkOwnedUnresolvedDetectsLinkedWithoutElevation() {
        assertTrue(BuildingSiteElevationResolver.isEarthworkOwnedUnresolved(
            new BuildingPadElevationStatus(
                BuildingPadElevationMode.EARTHWORK_OWNED,
                "Pad",
                "Site",
                null)));
        assertFalse(BuildingSiteElevationResolver.isEarthworkOwnedUnresolved(
            BuildingPadElevationStatus.none()));
        assertFalse(BuildingSiteElevationResolver.isEarthworkOwnedUnresolved(
            new BuildingPadElevationStatus(
                BuildingPadElevationMode.BUILDING_LINKED,
                "Pad",
                "Site",
                null)));
    }

    /** package-private test helper mirroring {@link com.plot.plugin.building.site.BuildingSiteAnalyzerTest}. */
    static final class BuildingSiteAnalyzerTestHelper {
        private BuildingSiteAnalyzerTestHelper() {
        }

        static BuildingSiteColumnSample land(int groundY) {
            return new BuildingSiteColumnSample(groundY, groundY, java.util.OptionalInt.empty(), 0, 0, true);
        }
    }
}
