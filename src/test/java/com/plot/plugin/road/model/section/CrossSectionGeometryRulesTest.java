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
    void centerMarkingLateralRangeFollowsCenterSeamNotGeometricZero() {
        // 3×6 + 白1 + 双黄2 = 21；中央缝在第二/三车道之间，不在几何 0
        List<Integer> lanes = List.of(6, 6, 6);
        int[] range = CrossSectionGeometryRules.centerMarkingLateralRange(
            lanes, CenterLineStyle.DOUBLE_SOLID, true);
        assertEquals(3, range[0]);
        assertEquals(4, range[1]);

        int[] twoLane = CrossSectionGeometryRules.centerMarkingLateralRange(
            List.of(3, 3), CenterLineStyle.DOUBLE_SOLID, false);
        assertEquals(0, twoLane[0]);
        assertEquals(1, twoLane[1]);

        int[] none = CrossSectionGeometryRules.centerMarkingLateralRange(
            lanes, CenterLineStyle.NONE, true);
        assertEquals(0, none[0]);
        assertTrue(none[1] < none[0]);
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
    void threeLaneMedianBindsToOffsetCenterMarking() {
        RoadSystemConfig defaults = new RoadSystemConfig("geom-3lane");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(3);
        section.getCarriageway().setDefaultLaneWidth(6);
        section.getMarkings().setLaneDividers(true);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        section.getMedian().setEnabled(true);
        section.getMedian().setHeight(2);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.DOUBLE_SOLID, true, 0);

        ResolvedCrossSection resolved = section.resolve(defaults);
        assertEquals(21, resolved.carriagewayWidth);
        assertTrue(resolved.includeMedian);
        assertEquals(3, resolved.medianMinLateral);
        assertEquals(4, resolved.medianMaxLateral);
        assertFalse(resolved.occupiesMedianColumn(0));
    }

    @Test
    void narrowCarriagewayDisallowsDoublePier() {
        assertFalse(RoadParameterLimits.allowsDoublePier(1));
        assertFalse(RoadParameterLimits.allowsDoublePier(2));
        assertTrue(RoadParameterLimits.allowsDoublePier(3));
    }
}
