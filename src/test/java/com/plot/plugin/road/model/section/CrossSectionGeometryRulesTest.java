package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionGeometryRulesTest {

    @Test
    void extremeMedianDoesNotFitFiveWideTwoLaneCarriageway() {
        assertFalse(CrossSectionGeometryRules.isMedianFit(5, 2, 4));
        assertEquals(3, CrossSectionGeometryRules.clampMedianWidth(5, 2, 4));
        assertEquals(2, CrossSectionGeometryRules.laneOnlyWidth(5, 2, 3));
        assertTrue(3 + 2 <= 5);
    }

    @Test
    void highwayMedianUnchanged() {
        assertTrue(CrossSectionGeometryRules.isMedianFit(11, 2, 1));
        assertEquals(1, CrossSectionGeometryRules.clampMedianWidth(11, 2, 1));
        assertEquals(10, CrossSectionGeometryRules.laneOnlyWidth(11, 2, 1));
    }

    @Test
    void resolveKeepsMedianAdditiveAndGrowsCarriageway() {
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
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);

        CrossSectionResolveReport.ResolveResult result =
            ResolvedCrossSection.resolveWithReport(section, defaults);

        // 分隔带改为加在总宽上：2+2+4=8，不再把 median 压进固定 W
        assertEquals(4, result.section().medianWidth);
        assertEquals(8, result.section().carriagewayWidth);
        assertEquals(4, result.section().laneOnlyWidth);
        assertEquals(
            result.section().medianWidth + result.section().laneOnlyWidth,
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
}
