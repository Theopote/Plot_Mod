package com.plot.plugin.building.golden;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * 12 个标准建筑轮廓的 Golden 回归测试。
 */
class GoldenBuildingTest {

    static Stream<GoldenBuildingCaseFactory.Case> goldenCases() {
        return GoldenBuildingCaseFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void goldenCaseMatchesExpectedMetrics(GoldenBuildingCaseFactory.Case goldenCase) {
        GoldenBuildingMetrics expected = GoldenBuildingExpectations.forCase(goldenCase.id());
        GoldenBuildingMetrics actual = GoldenBuildingHarness.generate(goldenCase.footprint());
        GoldenBuildingAssertions.assertMetrics(
            goldenCase.id(),
            expected,
            actual,
            GoldenBuildingTolerance.standard());
    }
}
