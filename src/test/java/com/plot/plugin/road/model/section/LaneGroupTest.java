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
        // W=7、minLateral=-3：左车道后中心缝占格中心在 0.5
        List<Double> offsets = group.resolveLaneDividerOffsets(CenterLineStyle.SINGLE_DASHED, true);
        assertEquals(List.of(0.5), offsets);
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
        // W=16、minLateral=-7：白线 / 双黄 / 白线，双黄落在路面居中 [0,1]
        assertEquals(4, offsets.size());
        assertEquals(-3.5, offsets.get(0), 1e-6);
        assertEquals(0.5, offsets.get(1), 1e-6);
        assertEquals(1.5, offsets.get(2), 1e-6);
        assertEquals(5.5, offsets.get(3), 1e-6);
    }
}
