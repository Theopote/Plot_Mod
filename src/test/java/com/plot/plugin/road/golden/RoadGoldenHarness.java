package com.plot.plugin.road.golden;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.terrain.TerrainSampler;

/**
 * 运行道路生成管线并收集 Golden / Acceptance 输入。
 */
public final class RoadGoldenHarness {
    private RoadGoldenHarness() {
    }

    public record Run(
            RoadNetwork network,
            TerrainSampler terrain,
            RoadBenchmarkHarness.PreviewRun preview,
            RoadGoldenMetrics metrics) {
    }

    public static Run run(RoadGoldenScenario scenario) {
        RoadSystemConfig config = scenario.config();
        RoadGenerator generator = new RoadGenerator(
            config,
            null,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            generator,
            scenario.terrain());
        RoadGoldenMetrics metrics = RoadGoldenMetricsCollector.collect(preview);
        return new Run(scenario.network(), scenario.terrain(), preview, metrics);
    }

    public static RoadGoldenMetrics generate(RoadGoldenScenario scenario) {
        return run(scenario).metrics();
    }
}
