package com.plot.plugin.road.golden;

import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;

import java.util.List;

/**
 * 从生成结果提取 Golden Test 统计量。
 */
public final class RoadGoldenMetricsCollector {
    private RoadGoldenMetricsCollector() {
    }

    public static RoadGoldenMetrics collect(RoadBenchmarkHarness.PreviewRun run) {
        if (run == null || run.aggregate() == null) {
            return RoadGoldenMetrics.empty();
        }
        RoadGenerationResult aggregate = run.aggregate();
        int junctionBlocks = run.junctionResults().values().stream()
            .mapToInt(j -> j.getSolids().primitives().size())
            .sum();
        int markingBlocks = run.junctionResults().values().stream()
            .flatMap(j -> j.getSolids().primitives().stream())
            .filter(p -> p.layer() == RoadSolidLayer.MARKING)
            .mapToInt(p -> 1)
            .sum();
        return new RoadGoldenMetrics(
            aggregate.roadBlocks.size() + aggregate.sidewalkBlocks.size(),
            aggregate.fillVolume,
            aggregate.cutVolume,
            markingBlocks,
            aggregate.bridgeBlocks.size(),
            aggregate.tunnelBlocks.size(),
            junctionBlocks,
            aggregate.cutVolume,
            aggregate.fillVolume,
            aggregate.placementRecords.size(),
            aggregate.bridgeCount,
            aggregate.tunnelCount,
            List.of());
    }
}
