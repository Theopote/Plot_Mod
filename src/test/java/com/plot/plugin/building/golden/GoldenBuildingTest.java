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
     * B10 厚墙（10×8，wallThickness=3）：
     * <ul>
     *   <li>墙体量必须大于同轮廓薄墙（thickness=1）</li>
     *   <li>开洞沿<strong>内</strong>法向镂空，AABB 不得超出 footprint（曾误把负 bounds
     *       当成「厚墙外扩」固化进 Golden；根因是 CCW 轮廓内法向反向）</li>
     * </ul>
     */
    @Test
    void b10ThickWallInvariants() {
        GoldenBuildingMetrics thick = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b10ThickWall().footprint());
        GoldenBuildingMetrics thin = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.rectangle(10, 8, 2, 3, 1));

        assertTrue(thick.wallBlocks() > thin.wallBlocks(), "thick wall must exceed thin wall block count");
        assertTrue(thick.wallBlocks() > thick.floorBlocks(), "thick wall mass should dominate floor slabs");
        assertTrue(thick.minX() >= 0, "openings must carve inward; minX must stay in footprint");
        assertTrue(thick.minZ() >= 0, "openings must carve inward; minZ must stay in footprint");
        assertTrue(thick.maxX() < 10, "blocks use cell centers; maxX stays within [0,10)");
        assertTrue(thick.maxZ() < 8, "blocks use cell centers; maxZ stays within [0,8)");
        assertTrue(thick.warnings().isEmpty());
    }
}
