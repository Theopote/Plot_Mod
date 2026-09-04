package com.plot.plugin.building.golden;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * A. Regression Golden：Snapshot 指标锁定，检测「行为有没有变化」。
 * <p>
 * expected 来自 {@link GoldenBuildingExpectations}，可由
 * {@link GoldenBuildingSnapshotGeneratorTest} 刷新。
 * <p>
 * 正确性（「行为是不是正确」）见 {@link BuildingSemanticAcceptanceTest}。
 * 尤其 B07：刷新 snapshot 不能代替 {@code wallBlocks > 0} 等语义断言。
 */
class GoldenBuildingRegressionTest {

    static Stream<GoldenBuildingCaseFactory.Case> goldenCases() {
        return GoldenBuildingCaseFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void regressionSnapshotMatchesExpected(GoldenBuildingCaseFactory.Case goldenCase) {
        GoldenBuildingMetrics expected = GoldenBuildingExpectations.forCase(goldenCase.id());
        GoldenBuildingMetrics actual = GoldenBuildingHarness.generate(goldenCase.footprint());
        GoldenBuildingAssertions.assertMetrics(
            goldenCase.id(),
            expected,
            actual,
            GoldenBuildingTolerance.standard());
    }
}
