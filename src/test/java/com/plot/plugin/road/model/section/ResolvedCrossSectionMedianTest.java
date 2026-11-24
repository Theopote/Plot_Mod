package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedCrossSectionMedianTest {

    @Test
    void highwayLikeWidthKeepsMedianOutOfLaneWidths() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(11);
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().syncLaneCount(2);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getMedian().setHeight(2);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getMarkings().setLaneDividers(true);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);

        // 旧档 11 含分隔带 1：反推行驶宽 4+4，派生 W=双黄2+行驶8=10；隔离带叠在路表
        // 双黄中央标线 → 隔离带宽度派生为 2
        assertEquals(10, resolved.carriagewayWidth);
        assertEquals(10, resolved.laneOnlyWidth);
        assertEquals(8, resolved.laneWidths.stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, resolved.laneWidths.size());
        assertEquals(2, resolved.medianWidth);
        assertEquals(2, resolved.medianHeight);
        assertTrue(resolved.laneDividerOffsets.stream().anyMatch(o -> Math.abs(o) < 1.0));
        assertTrue(resolved.occupiesMedianColumn(-1));
        assertTrue(resolved.occupiesMedianColumn(0));
        assertEquals(10, resolved.totalHardSurfaceWidthBlocks());
    }

    @Test
    void avenueWithSidewalkReportsTotalHardWidth() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(4);
        section.getMarkings().setLaneDividers(true);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, true, 0);
        section.getShoulder().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getSidewalk().setEnabled(true);
        section.getSidewalk().setWidth(2);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);

        // W=4+4+1缝=9；两侧人行道 2+2 → 总硬宽 13；无中央标线时隔离带宽=分隔 1
        assertEquals(9, resolved.carriagewayWidth);
        assertEquals(2, resolved.outerBandBlockCount());
        assertEquals(13, resolved.totalHardSurfaceWidthBlocks());
        assertEquals(1, resolved.medianWidth);
        assertEquals(RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS, resolved.medianHeight);
        assertTrue(resolved.occupiesMedianColumn(0));
    }

    @Test
    void fourLaneDoubleSolidMedianCoversOnlyCenterColumns() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(4);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setLaneDividers(true);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(4);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.DOUBLE_SOLID, true, 0);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);
        assertEquals(2, resolved.medianWidth);
        assertEquals(-1, resolved.medianMinLateral);
        assertEquals(0, resolved.medianMaxLateral);
        assertTrue(resolved.occupiesMedianColumn(-1));
        assertTrue(resolved.occupiesMedianColumn(0));
        // 侧向白线列不在隔离带范围内
        assertFalse(resolved.occupiesMedianColumn(-5));
        assertFalse(resolved.occupiesMedianColumn(4));
    }

    @Test
    void medianDisabledWithoutCenterLineOrDividers() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setLaneDividers(false);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(2);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, false, 0);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);
        assertFalse(resolved.includeMedian);
        assertEquals(0, resolved.medianWidth);
    }

    @Test
    void medianHeightClampsToOneThroughThree() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setLaneDividers(true);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, true, 0);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getMedian().setHeight(9);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);
        assertEquals(RoadParameterLimits.MAX_MEDIAN_HEIGHT_BLOCKS, resolved.medianHeight);
    }

    private static RoadSystemConfig bareDefaults() {
        RoadSystemConfig defaults = new RoadSystemConfig("median-test");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        return defaults;
    }
}
