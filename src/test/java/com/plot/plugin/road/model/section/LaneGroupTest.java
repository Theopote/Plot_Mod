package com.plot.plugin.road.model.section;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LaneGroupTest {

    @Test
    void resolveLaneWidthsUsesUniformDefault() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(3);
        group.setDefaultLaneWidth(3);
        assertEquals(List.of(3, 3, 3), group.resolveLaneWidths());
    }

    @Test
    void resolveLaneWidthsHonorsExplicitLane() {
        LaneGroup group = new LaneGroup();
        group.syncLaneCount(2);
        group.setDefaultLaneWidth(3);
        group.setLaneWidthAt(0, 4);
        assertEquals(List.of(4, 3), group.resolveLaneWidths());
    }

    @Test
    void resolveLaneDividerOffsetsForTwoLanesWithCenterLine() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(2);
        group.setDefaultLaneWidth(3);
        // W=7: lanes at [-3.5,-0.5) and (0.5,3.5]; center seam [ -0.5, 0.5 )
        List<Double> offsets = group.resolveLaneDividerOffsets(CenterLineStyle.SINGLE_DASHED, true);
        assertEquals(List.of(0.0), offsets);
    }

    @Test
    void migrateDoesNotPackStaleDividerWidthIntoTravelLanes() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(2);
        group.setDefaultLaneWidth(3);
        group.setWidth(7); // 仍是「含 1 格同向分隔」的旧总宽
        assertFalse(group.migrateUniformLaneWidthFromStored(7, CenterLineStyle.NONE, false, 0));
        assertEquals(List.of(3, 3), group.resolveLaneWidths());
    }

    @Test
    void resolveLaneDividerOffsetsForFourLanesWithDoubleCenter() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(4);
        group.setDefaultLaneWidth(3);
        List<Double> offsets = group.resolveLaneDividerOffsets(CenterLineStyle.DOUBLE_SOLID, true);
        // side white @ after lane0; double yellow two lines; side white after lane2
        // centerSeam=(4-1)/2=1 → 白线 / 双黄 / 白线
        assertEquals(4, offsets.size());
        assertEquals(-4.5, offsets.get(0), 1e-6);
        assertEquals(-0.5, offsets.get(1), 1e-6);
        assertEquals(0.5, offsets.get(2), 1e-6);
        assertEquals(4.5, offsets.get(3), 1e-6);
    }
}
