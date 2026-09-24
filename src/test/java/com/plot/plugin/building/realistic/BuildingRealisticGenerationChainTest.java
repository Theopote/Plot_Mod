package com.plot.plugin.building.realistic;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.golden.GoldenBuildingHarness;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import com.plot.plugin.building.site.TerrainElevationStrategy;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包 A：真实生成链场景（D-B15–D-B22）。
 * <p>
 * 矩阵登记见 {@code docs/development/BuildingMassingScenarioMatrix.md}。
 */
class BuildingRealisticGenerationChainTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RealisticDistrictGap");

    @Test
    void dB15_concaveAndLShapePitchedRoofsGenerateWithDocumentedDowngrades() {
        BuildingFootprint lShape = RealisticDistrictFixtures.lShape(0);
        lShape.setRoofType(BuildingFootprint.RoofType.HIP);
        lShape.setRoofPitchRatio(2);
        GoldenBuildingHarness.Run lRun = RealisticGenerationChainHarness.runSingle(
            lShape, RealisticGenerationChainHarness.SiteMode.FLAT);
        assertTrue(lRun.context().isValid());
        assertTrue(lRun.metrics().wallBlocks() > 0);
        assertEquals("HIP", lRun.metrics().effectiveRoofType());

        BuildingFootprint concave = RealisticDistrictFixtures.concave(0);
        concave.setRoofType(BuildingFootprint.RoofType.GABLE);
        concave.setRoofPitchRatio(2);
        GoldenBuildingHarness.Run concaveRun = RealisticGenerationChainHarness.runSingle(
            concave, RealisticGenerationChainHarness.SiteMode.FLAT);
        assertTrue(concaveRun.context().isValid());
        assertEquals("GABLE", concaveRun.metrics().effectiveRoofType());

        BuildingFootprint narrow = RealisticDistrictFixtures.narrowCorridor(0);
        GoldenBuildingHarness.Run narrowRun = RealisticGenerationChainHarness.runSingle(
            narrow, RealisticGenerationChainHarness.SiteMode.FLAT);
        assertTrue(narrowRun.metrics().wallBlocks() > 0);
        assertEquals(0, narrowRun.metrics().floorBlocks());
        assertEquals("FLAT", narrowRun.metrics().effectiveRoofType());
        assertTrue(narrowRun.metrics().warnings().contains("plugin.building.warn.roof_downgrade"));
        assertTrue(narrowRun.metrics().warnings().contains("plugin.building.warn.inner_offset_failed"));
    }

    @Test
    void dB16_ellipseAndCircleMultiFloorGenerate() {
        BuildingFootprint ellipse = RealisticDistrictFixtures.ellipseFootprint(0, 8, 4, 0.4);
        GoldenBuildingHarness.Run ellipseRun = RealisticGenerationChainHarness.runSingle(
            ellipse, RealisticGenerationChainHarness.SiteMode.SAMPLED);
        assertTrue(ellipseRun.context().isValid());
        assertTrue(ellipseRun.metrics().totalBlocks() > 0);
        assertTrue(ellipse.getOuterPoints().size() >= 12);
        assertEquals(5, ellipse.getFloors());

        BuildingFootprint circle = RealisticDistrictFixtures.circleFootprint(0, 5);
        GoldenBuildingHarness.Run circleRun = RealisticGenerationChainHarness.runSingle(
            circle, RealisticGenerationChainHarness.SiteMode.SAMPLED);
        assertTrue(circleRun.context().isValid());
        assertTrue(circleRun.metrics().wallBlocks() > 0);
        assertEquals(4, circle.getFloors());
    }

    @Test
    void dB17_overlappingFootprintsOnSampledTerrainLaterWins() {
        BuildingFootprint first = RealisticDistrictFixtures.gridRectangle(0, 4);
        BuildingFootprint second = copyForOverlap(first, "overlap-second");

        DistrictGenerationResult district = RealisticGenerationChainHarness.runDistrict(
            List.of(
                new TaggedFootprint(first, RealisticFootprintKind.OVERLAP_DUPLICATE,
                    BuildingFootprint.RoofType.FLAT),
                new TaggedFootprint(second, RealisticFootprintKind.OVERLAP_DUPLICATE,
                    BuildingFootprint.RoofType.FLAT)),
            RealisticGenerationChainHarness.SiteMode.SAMPLED);

        assertEquals(2, district.buildingsGenerated());
        assertTrue(district.hasBuildingOverlap());
        assertTrue(district.conflictingBlockCount() > 0);
        assertTrue(district.totalBlocks() > 0);
    }

    @Test
    void dB18_thickWallSmallFootprintInnerOffsetDegradesSafely() {
        BuildingFootprint small = GoldenBuildingCaseFactory.rectangle(3, 3, 2, 3, 4);
        GoldenBuildingHarness.Run run = RealisticGenerationChainHarness.runSingle(
            small, RealisticGenerationChainHarness.SiteMode.FLAT);
        assertTrue(run.context().isValid());
        assertTrue(run.metrics().wallBlocks() > 0);
        assertTrue(run.metrics().warnings().contains("plugin.building.warn.inner_offset_failed"));
        assertEquals(0, run.metrics().floorBlocks());
    }

    @Test
    void dB19_mixedDistrict100SampledSiteGapReport() {
        List<TaggedFootprint> catalog = RealisticDistrictFixtures.mixedDistrict100();
        DistrictGenerationResult district = RealisticGenerationChainHarness.runDistrict(
            catalog, RealisticGenerationChainHarness.SiteMode.SAMPLED);
        RealisticDistrictGapReport.Summary report = RealisticDistrictGapReport.analyze(district, catalog);
        LOGGER.info(report.format());
        System.out.println("[RealisticDistrictGap] " + report.format().replace("\n", " | "));
        MixedDistrictRegressionAssertions.assertBaseline(district, report);
    }

    @Test
    void dB20_roofEligibilityMatchesGeometryResolver() {
        BuildingFootprint lShape = RealisticDistrictFixtures.lShape(1);
        lShape.setRoofType(BuildingFootprint.RoofType.HIP);
        lShape.setRoofPitchRatio(2);
        BuildingDefinition lDefinition = BuildingDefinitionMapper.fromFootprint(lShape);
        assertEquals(
            BuildingFootprint.RoofType.HIP,
            RoofGenerationStage.resolveRoofType(
                lDefinition,
                lShape.getOuterPoints(),
                new com.plot.plugin.building.generation.BuildingGenerationResult()));

        BuildingFootprint narrow = RealisticDistrictFixtures.narrowCorridor(1);
        BuildingDefinition narrowDefinition = BuildingDefinitionMapper.fromFootprint(narrow);
        com.plot.plugin.building.generation.BuildingGenerationResult result =
            new com.plot.plugin.building.generation.BuildingGenerationResult();
        assertEquals(
            BuildingFootprint.RoofType.FLAT,
            RoofGenerationStage.resolveRoofType(
                narrowDefinition,
                narrow.getOuterPoints(),
                result));
        assertTrue(result.warnings.contains("plugin.building.warn.roof_downgrade"));
        assertFalse(BuildingGeometryUtils.isSlopedRoofEligible(narrow.getOuterPoints(), 4));
    }

    @Test
    void dB21_siteAnalysisSkipRequiresFailedBundleWithoutManualOrPad() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 8, 2, 3, 1);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        assertTrue(com.plot.plugin.building.generation.resolve.GenerationSiteResolver
            .mustSkipDueToFailedSiteAnalysis(
                definition,
                footprint,
                BuildingSiteAnalyzer.AnalysisBundle.failedFallback()));
    }

    @Test
    void dB22_steepSampledTerrainProducesSteepWarning() {
        List<BuildingSiteColumnSample> samples = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 6; z++) {
                int groundY = 60 + x;
                samples.add(new BuildingSiteColumnSample(
                    groundY, groundY, OptionalInt.empty(), 0, 0, true));
            }
        }
        var analysis = BuildingSiteAnalyzer.analyzeSamples(samples, TerrainElevationStrategy.BALANCED);
        assertTrue(analysis.maxGroundElevation() - analysis.minGroundElevation()
            >= BuildingSiteAnalyzer.SEVERE_STEEP_THRESHOLD);
    }

    private static BuildingFootprint copyForOverlap(BuildingFootprint source, String id) {
        BuildingFootprint copy = new BuildingFootprint(id, source.getOuterPoints(), source.isRectangular());
        copy.setFloors(source.getFloors());
        copy.setFloorHeight(source.getFloorHeight());
        copy.setWallThickness(source.getWallThickness());
        copy.setWindowsEnabled(source.isWindowsEnabled());
        copy.setWindowWidth(source.getWindowWidth());
        copy.setWindowPierWidth(source.getWindowPierWidth());
        return copy;
    }
}
