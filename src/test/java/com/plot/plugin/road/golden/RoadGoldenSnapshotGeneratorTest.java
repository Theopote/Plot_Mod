package com.plot.plugin.road.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * 刷新 Golden Regression expected 值。运行后把 stdout 复制到 {@link RoadGoldenExpectations}。
 */
class RoadGoldenSnapshotGeneratorTest {

    static Stream<RoadGoldenScenario> goldenScenarios() {
        return RoadGoldenScenarioFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void printSnapshotMetrics(RoadGoldenScenario scenario) {
        RoadGoldenMetrics metrics = RoadGoldenHarness.generate(scenario);
        System.out.printf(
            "public static final RoadGoldenMetrics %s = new RoadGoldenMetrics(%d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, List.of());%n",
            scenario.id().replace("-", ""),
            metrics.surfaceBlocks(),
            metrics.fillBlocks(),
            metrics.cutBlocks(),
            metrics.markingBlocks(),
            metrics.bridgeBlocks(),
            metrics.tunnelBlocks(),
            metrics.junctionBlocks(),
            metrics.cutVolume(),
            metrics.fillVolume(),
            metrics.placementRecords(),
            metrics.bridgeCount(),
            metrics.tunnelCount());
    }

    @Test
    void generateAllExpectationsHeader() {
        System.out.println("// Paste into RoadGoldenExpectations.java");
    }
}
