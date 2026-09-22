package com.plot.plugin.building.realistic;

import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.golden.GoldenBuildingHarness;
import com.plot.plugin.building.golden.GoldenBuildingMetrics;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实生成链测试 harness：flat site（无 World）与 sampled site（合成地形列）。
 */
public final class RealisticGenerationChainHarness {
    private static final BuildingGenerationPipeline PIPELINE =
        BuildingGenerationPipeline.createDefault();

    public enum SiteMode {
        FLAT,
        SAMPLED
    }

    private RealisticGenerationChainHarness() {
    }

    public static GoldenBuildingHarness.Run runSingle(BuildingFootprint footprint, SiteMode siteMode) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = createContext(footprint, siteMode, result);
        PIPELINE.generate(context);
        return new GoldenBuildingHarness.Run(
            context,
            result,
            com.plot.plugin.building.golden.GoldenBuildingMetricsCollector.collect(context, result));
    }

    public static DistrictGenerationResult runDistrict(List<TaggedFootprint> catalog, SiteMode siteMode) {
        List<BuildingFootprint> buildings = new ArrayList<>(catalog.size());
        for (TaggedFootprint tagged : catalog) {
            buildings.add(tagged.footprint());
        }
        return DistrictMassingGenerator.generate(buildings, footprint -> generateOne(footprint, siteMode));
    }

    public static BuildingGenerationResult generateOne(BuildingFootprint footprint, SiteMode siteMode) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = createContext(footprint, siteMode, result);
        return PIPELINE.generate(context);
    }

    private static BuildingGenerationContext createContext(
            BuildingFootprint footprint,
            SiteMode siteMode,
            BuildingGenerationResult result) {
        if (siteMode == SiteMode.SAMPLED) {
            return BuildingGenerationContext.forTestingWithSampledSite(
                footprint,
                GoldenBuildingTestFixtures.coordinates(),
                GoldenBuildingTestFixtures.projection(),
                result);
        }
        return BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
    }
}
