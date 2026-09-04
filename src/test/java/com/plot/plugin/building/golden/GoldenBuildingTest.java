package com.plot.plugin.building.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * B07 除锁定指标外，必须满足 inner offset 降级行为不变量。
     * 防止 Golden snapshot 再次把「零墙体」错误实现固化。
     */
    @Test
    void b07InnerOffsetDegradationInvariants() {
        GoldenBuildingMetrics actual = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b07NarrowCorridor().footprint());

        assertTrue(actual.wallBlocks() > 0, "narrow corridor must still generate solid wall mass");
        assertEquals(0, actual.floorBlocks(), "interior floor must be skipped when inner offset fails");
        assertTrue(actual.warnings().contains("plugin.building.warn.inner_offset_failed"));
        assertTrue(actual.warnings().contains("plugin.building.warn.roof_downgrade"));
        assertEquals("FLAT", actual.effectiveRoofType());
    }

    /**
     * B10 厚墙：墙体量大于薄墙同轮廓，且 bounds 可超出 footprint 原点（负 minX/minZ 为预期几何）。
     */
    @Test
    void b10ThickWallInvariants() {
        GoldenBuildingMetrics thick = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b10ThickWall().footprint());
        GoldenBuildingMetrics thin = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.rectangle(10, 8, 2, 3, 1));

        assertTrue(thick.wallBlocks() > thin.wallBlocks(), "thick wall must exceed thin wall block count");
        assertTrue(thick.wallBlocks() > thick.floorBlocks(), "thick wall mass should dominate floor slabs");
        assertTrue(thick.minX() < 0, "thick walls extend west of footprint origin");
        assertTrue(thick.minZ() < 0, "thick walls extend north of footprint origin");
        assertTrue(thick.warnings().isEmpty());
    }
}
