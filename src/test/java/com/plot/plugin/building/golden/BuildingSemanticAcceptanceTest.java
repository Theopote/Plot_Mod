package com.plot.plugin.building.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B. Semantic Acceptance：手写正确性断言，检测「行为是不是正确」。
 * <p>
 * 不可由 Snapshot 自动生成 expected。与 {@link GoldenBuildingRegressionTest} 共用案例库，
 * 但断言职责分离——见 {@link GoldenTestKinds}。
 */
class BuildingSemanticAcceptanceTest {

    static Stream<GoldenBuildingCaseFactory.Case> goldenCases() {
        return GoldenBuildingCaseFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void universalSemanticInvariants(GoldenBuildingCaseFactory.Case goldenCase) {
        GoldenBuildingHarness.Run run = GoldenBuildingHarness.run(goldenCase.footprint());
        SemanticAcceptanceAssertions.assertUniversal(goldenCase.id(), run);
    }

    /**
     * B07：证明 Semantic Acceptance 不可被 Snapshot 替代。
     * <p>
     * Regression 仍比对 {@link GoldenBuildingExpectations#B07}，但「wallBlocks &gt; 0」等
     * 正确性只能写在这里——刷新 snapshot 绝不能覆盖本断言。
     */
    @Test
    void b07RequiresSemanticProtectionBeyondSnapshot() {
        GoldenBuildingMetrics metrics = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b07NarrowCorridor().footprint());

        // 显式语义（非 snapshot）
        assertTrue(metrics.wallBlocks() > 0);
        SemanticAcceptanceAssertions.assertB07InnerOffsetDegradation(metrics);

        // 与当前 snapshot 一致 ≠ 可省略上面的语义；两者必须同时存在
        GoldenBuildingAssertions.assertMetrics(
            "B07",
            GoldenBuildingExpectations.B07,
            metrics,
            GoldenBuildingTolerance.standard());
    }

    @Test
    void b10ThickWallInvariants() {
        GoldenBuildingMetrics thick = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b10ThickWall().footprint());
        GoldenBuildingMetrics thin = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.rectangle(10, 8, 2, 3, 1));
        SemanticAcceptanceAssertions.assertB10ThickWallInvariants(thick, thin);
    }

    @Test
    void b11DoorAndWindowSemantics() {
        var footprint = GoldenBuildingCaseFactory.b11DoorsAndWindows().footprint();
        GoldenBuildingHarness.Run run = GoldenBuildingHarness.run(footprint);
        SemanticAcceptanceAssertions.assertB11HasDoorAndWindowOpenings(footprint, run.metrics());
        SemanticAcceptanceAssertions.assertDoorsTouchWall("B11", run);
    }
}
