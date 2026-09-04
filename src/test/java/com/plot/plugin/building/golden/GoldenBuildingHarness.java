package com.plot.plugin.building.golden;

import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 运行默认管线并收集 Golden 指标。
 */
public final class GoldenBuildingHarness {
    private GoldenBuildingHarness() {
    }

    public static GoldenBuildingMetrics generate(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        BuildingGenerationPipeline.createDefault().generate(context);
        return GoldenBuildingMetricsCollector.collect(context, result);
    }
}
