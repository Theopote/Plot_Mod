package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionGeometryRulesTest {

    @Test
    void derivedMedianWidthFollowsCenterLineOnly() {
        assertEquals(2, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.DOUBLE_SOLID, false, 2));
        assertEquals(1, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.SINGLE_DASHED, false, 2));
        assertEquals(1, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.RAIL, true, 2));
        // 仅有同向白线、无中央标线时不可启用
        assertEquals(0, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.NONE, true, 2));
        assertEquals(0, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.NONE, false, 2));
        assertEquals(0, CrossSectionGeometryRules.derivedMedianWidth(
            CenterLineStyle.DOUBLE_SOLID, true, 1));
        assertFalse(CrossSectionGeometryRules.canEnableMedian(
            CenterLineStyle.NONE, true, 4));
        assertTrue(CrossSectionGeometryRules.canEnableMedian(
            CenterLineStyle.SINGLE_DASHED, false, 2));
    }

    @Test
    void medianLateralRangeStaysOnCenterMarkings() {
        int[] doubleYellow = CrossSectionGeometryRules.medianLateralRange(
            List.of(-3.5, 0.5, 1.5, 5.5), 2);
        assertEquals(0, doubleYellow[0]);
        assertEquals(1, doubleYellow[1]);

        int[] single = CrossSectionGeometryRules.medianLateralRange(List.of(0.5), 1);
        assertEquals(0, single[0]);
        assertEquals(0, single[1]);

        // 侧向白线过散时不跨路面，退回路面居中双格 [0,1]
        int[] scattered = CrossSectionGeometryRules.medianLateralRange(
            List.of(-6.0, 6.0), 2);
        assertEquals(0, scattered[0]);
        assertEquals(1, scattered[1]);
    }

    @Test
    void laneOnlyWidthIgnoresMedian() {
        assertEquals(5, CrossSectionGeometryRules.laneOnlyWidth(5, 2, 4));
        assertEquals(11, CrossSectionGeometryRules.laneOnlyWidth(11, 2, 1));
    }

    @Test
    void resolveDisablesMedianWithoutMarkings() {
        RoadSystemConfig defaults = new RoadSystemConfig("geom-test");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(2);
        section.getMarkings().setLaneDividers(false);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(4);
        section.getMedian().setHeight(3);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);

        CrossSectionResolveReport.ResolveResult result =
            ResolvedCrossSection.resolveWithReport(section, defaults);

        assertFalse(result.section().includeMedian);
        assertEquals(0, result.section().medianWidth);
        assertEquals(4, result.section().carriagewayWidth);
        assertEquals(4, result.section().laneOnlyWidth);
    }

    @Test
    void resolveDerivesDoubleSolidMedianWidth() {
        RoadSystemConfig defaults = new RoadSystemConfig("geom-double");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setLaneDividers(false);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getMedian().setHeight(3);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.DOUBLE_SOLID, false, 0);

        ResolvedCrossSection resolved = section.resolve(defaults);
        assertTrue(resolved.includeMedian);
        assertEquals(2, resolved.medianWidth);
        assertEquals(3, resolved.medianHeight);
        assertTrue(resolved.occupiesMedianColumn(0));
        assertTrue(resolved.occupiesMedianColumn(1));
        assertFalse(resolved.occupiesMedianColumn(-1));
        assertFalse(resolved.occupiesMedianColumn(2));
    }

    @Test
    void narrowCarriagewayDisallowsDoublePier() {
        assertFalse(RoadParameterLimits.allowsDoublePier(1));
        assertFalse(RoadParameterLimits.allowsDoublePier(2));
        assertTrue(RoadParameterLimits.allowsDoublePier(3));
    }
}
