package com.plot.plugin.building.golden;

import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 运行默认管线并收集 Golden / Acceptance 输入。
 */
public final class GoldenBuildingHarness {
    private GoldenBuildingHarness() {
    }

    /**
     * 一次完整生成：Context + Result + Metrics（Acceptance 需要前两者）。
     */
    public record Run(
            BuildingGenerationContext context,
            BuildingGenerationResult result,
            GoldenBuildingMetrics metrics) {
    }

    public static Run run(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        BuildingGenerationPipeline.createDefault().generate(context);
        GoldenBuildingMetrics metrics = GoldenBuildingMetricsCollector.collect(context, result);
        return new Run(context, result, metrics);
    }

    /** Regression Golden 用：仅指标。 */
    public static GoldenBuildingMetrics generate(BuildingFootprint footprint) {
        return run(footprint).metrics();
    }
}
