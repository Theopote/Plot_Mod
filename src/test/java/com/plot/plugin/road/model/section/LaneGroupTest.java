package com.plot.plugin.road.model.section;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaneGroupTest {

    @Test
    void resolveLaneWidthsEvenlySplitsTotalWidth() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(3);
        assertEquals(List.of(3, 3, 3), group.resolveLaneWidths(9));
    }

    @Test
    void resolveLaneDividerOffsetsForTwoLanes() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(2);
        assertEquals(List.of(0.0), group.resolveLaneDividerOffsets(6));
    }

    @Test
    void resolveLaneDividerOffsetsForThreeLanes() {
        LaneGroup group = new LaneGroup();
        group.setLaneCount(3);
        List<Double> offsets = group.resolveLaneDividerOffsets(9);
        assertEquals(2, offsets.size());
        assertEquals(-1.5, offsets.get(0), 1e-6);
        assertEquals(1.5, offsets.get(1), 1e-6);
    }
}
