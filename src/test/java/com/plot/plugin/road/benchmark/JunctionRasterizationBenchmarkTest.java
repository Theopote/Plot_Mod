package com.plot.plugin.road.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路口栅格化基准（J01–J05）：仅测量，不主动优化实现。
 */
class JunctionRasterizationBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/JunctionRasterBenchmark");

    static Stream<JunctionBenchmarkHarness.JunctionCase> junctionCases() {
        return JunctionBenchmarkHarness.standardCases().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("junctionCases")
    void measureJunctionRasterization(JunctionBenchmarkHarness.JunctionCase junctionCase) {
        JunctionBenchmarkHarness.JunctionMetrics metrics = JunctionBenchmarkHarness.run(junctionCase);
        LOGGER.info(metrics.summary());
        System.out.println("[JunctionRasterBenchmark] " + metrics.summary());
        assertTrue(metrics.generatedCells() > 0);
        assertTrue(metrics.rasterMillis() >= 0);
    }

    @Test
    void fullJunctionBenchmarkWhenPropertyEnabled() {
        String flag = System.getProperty("plot.road.benchmark", "");
        if (flag.isBlank()) {
            flag = System.getenv().getOrDefault("PLOT_ROAD_BENCHMARK", "");
        }
        if (!"full".equalsIgnoreCase(flag)) {
            return;
        }
        junctionCases().forEach(c -> LOGGER.info(JunctionBenchmarkHarness.run(c).summary()));
    }
}
