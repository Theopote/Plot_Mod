package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedCrossSectionMedianTest {

    @Test
    void highwayLikeWidthSplitsMedianOutOfLaneWidths() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(11);
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().syncLaneCount(2);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getShoulder().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getMarkings().setLaneDividers(true);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);

        assertEquals(11, resolved.carriagewayWidth);
        assertEquals(10, resolved.laneOnlyWidth);
        assertEquals(10, resolved.laneWidths.stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, resolved.laneWidths.size());
        for (Double offset : resolved.laneDividerOffsets) {
            assertFalse(Math.abs(offset) < 0.5, "divider must not sit at median center, offset=" + offset);
        }
        assertTrue(resolved.occupiesMedianColumn(0));
        assertEquals(11, resolved.totalHardSurfaceWidthBlocks());
    }

    @Test
    void avenueWithSidewalkReportsTotalHardWidth() {
        RoadSystemConfig defaults = bareDefaults();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(11);
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().syncLaneCount(2);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getShoulder().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getSidewalk().setEnabled(true);
        section.getSidewalk().setWidth(2);

        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);

        assertEquals(2, resolved.outerBandBlockCount());
        assertEquals(15, resolved.totalHardSurfaceWidthBlocks());
    }

    private static RoadSystemConfig bareDefaults() {
        RoadSystemConfig defaults = new RoadSystemConfig("median-test");
        defaults.setIncludeShoulder(false);
        defaults.setIncludeSidewalk(false);
        return defaults;
    }
}
