package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionGeometryRulesTest {

    @Test
    void medianWidthFollowsSinglePierParity() {
        // 奇数路宽：合法直径为奇数
        assertEquals(1, CrossSectionGeometryRules.clampMedianWidth(5, 2, 1));
        assertEquals(1, CrossSectionGeometryRules.clampMedianWidth(5, 2, 2));
        assertTrue(CrossSectionGeometryRules.isMedianFit(5, 2, 1));
        assertFalse(CrossSectionGeometryRules.isMedianFit(5, 2, 2));
        assertEquals(5, CrossSectionGeometryRules.laneOnlyWidth(5, 2, 4));
    }

    @Test
    void highwayMedianUnchangedWhenParityMatches() {
        assertTrue(CrossSectionGeometryRules.isMedianFit(11, 2, 1));
        assertEquals(1, CrossSectionGeometryRules.clampMedianWidth(11, 2, 1));
        assertEquals(11, CrossSectionGeometryRules.laneOnlyWidth(11, 2, 1));
    }

    @Test
    void resolveKeepsMedianOutOfCarriagewayWidth() {
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

        // 行驶宽 2+2=4；隔离带按单柱奇偶钳到 2，不加入 W
        assertEquals(2, result.section().medianWidth);
        assertEquals(4, result.section().carriagewayWidth);
        assertEquals(4, result.section().laneOnlyWidth);
        assertEquals(3, result.section().medianHeight);
        assertEquals(
            result.section().laneOnlyWidth,
            result.section().carriagewayWidth);
    }

    @Test
    void resolveReportsGravityMaterialReplacement() {
        RoadSystemConfig defaults = new RoadSystemConfig("mat-report");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(5);
        section.getCarriageway().setLaneCount(1);
        section.getShoulder().setEnabled(true);
        section.getShoulder().setWidth(1);
        section.getShoulder().setMaterial("minecraft:gravel");
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getMedian().setEnabled(false);

        CrossSectionResolveReport.ResolveResult result =
            ResolvedCrossSection.resolveWithReport(section, defaults);

        assertFalse(result.report().materialReplacements().isEmpty());
        assertEquals(
            "minecraft:coarse_dirt",
            result.section().shoulderMaterial);
    }

    @Test
    void narrowCarriagewayDisallowsDoublePier() {
        assertFalse(RoadParameterLimits.allowsDoublePier(1));
        assertFalse(RoadParameterLimits.allowsDoublePier(2));
        assertTrue(RoadParameterLimits.allowsDoublePier(3));
    }
}
