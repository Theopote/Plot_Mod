package com.plot.plugin.road.golden;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * A. Regression Golden：Snapshot 指标锁定，检测「行为有没有变化」。
 */
class RoadGoldenRegressionTest {

    static Stream<RoadGoldenScenario> goldenCases() {
        return RoadGoldenScenarioFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void regressionSnapshotMatchesExpected(RoadGoldenScenario scenario) {
        RoadGoldenMetrics expected = RoadGoldenExpectations.forCase(scenario.id());
        RoadGoldenMetrics actual = RoadGoldenHarness.generate(scenario);
        RoadGoldenAssertions.assertMetrics(
            scenario.id(),
            expected,
            actual,
            RoadGoldenTolerance.standard());
    }
}
