package com.plot.plugin.road.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大型路网性能基准（N01–N05）。
 * <p>
 * CI 默认跑 N01(100) / N02(500) / N03(1000)。更大档位需：
 * {@code -Dplot.road.benchmark=full}
 */
class RoadNetworkPerformanceBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetworkBenchmark");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "N01, 100",
        "N02, 500",
        "N03, 1000"
    })
    void ciGateNetworkScales(String scaleId, int edgeCount) {
        RoadBenchmarkResult result = RoadBenchmarkHarness.run(scaleId, edgeCount);
        LOGGER.info(result.summary());
        System.out.println("[RoadNetworkBenchmark] " + result.summary());

        assertTrue(result.edgeCount() == edgeCount, "edge count");
        assertTrue(result.snapshotMillis() >= 0);
        assertTrue(result.previewMillis() >= 0);
        assertTrue(result.placementRecords() > 0, scaleId + " must produce placements");
        assertTrue(result.serializedJsonChars() > 0);
    }

    @Test
    void fullSuiteWhenPropertyEnabled() {
        String flag = System.getProperty("plot.road.benchmark", "");
        if (flag.isBlank()) {
            flag = System.getenv().getOrDefault("PLOT_ROAD_BENCHMARK", "");
        }
        if (!"full".equalsIgnoreCase(flag)) {
            LOGGER.info("Skip N04–N05; enable with -Dplot.road.benchmark=full or PLOT_ROAD_BENCHMARK=full");
            return;
        }
        RoadBenchmarkResult n04 = RoadBenchmarkHarness.run("N04", 2500);
        RoadBenchmarkResult n05 = RoadBenchmarkHarness.run("N05", 5000);
        LOGGER.info(n04.summary());
        LOGGER.info(n05.summary());
        System.out.println("[RoadNetworkBenchmark] " + n04.summary());
        System.out.println("[RoadNetworkBenchmark] " + n05.summary());

        assertTrue(n04.edgeCount() == 2500);
        assertTrue(n05.edgeCount() == 5000);
        assertTrue(n04.placementRecords() > 0);
        assertTrue(n05.placementRecords() > 0);
    }
}
