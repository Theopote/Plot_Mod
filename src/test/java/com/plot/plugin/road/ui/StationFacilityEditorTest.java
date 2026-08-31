package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationFacilityEditorTest {

    @Test
    void buildRunsSkipsInvalidRows() {
        StationFacilityEditor.FacilityRunDraft valid = StationFacilityEditor.FacilityRunDraft.defaultRun(100.0);
        valid.endStation = 40f;

        StationFacilityEditor.FacilityRunDraft invalid = StationFacilityEditor.FacilityRunDraft.defaultRun(100.0);
        invalid.endStation = 0f;

        List<StationFacilityRun> runs = StationFacilityEditor.buildRuns(List.of(valid, invalid));
        assertEquals(1, runs.size());
        assertEquals(RoadFacilityKind.GUARDRAIL, runs.getFirst().getKind());
    }

    @Test
    void toRunUsesOpenEndAndRetainingWallHeight() {
        StationFacilityEditor.FacilityRunDraft draft = StationFacilityEditor.FacilityRunDraft.defaultRun(80.0);
        draft.openEnded = true;
        draft.kind = RoadFacilityKind.RETAINING_WALL;
        draft.side = RoadFacilitySide.RIGHT;
        draft.height = 3f;
        draft.materialBuffer.set("minecraft:stone_bricks");

        StationFacilityRun run = StationFacilityEditor.toRun(draft);
        assertNull(run.getEndStation());
        assertEquals(3.0, run.getHeight());
        assertEquals("minecraft:stone_bricks", run.getMaterial());
        assertEquals(RoadFacilitySide.RIGHT, run.getSide());
    }

    @Test
    void runsEqualComparesAllFields() {
        StationFacilityRun left = StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH);
        StationFacilityRun right = StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH);
        assertTrue(StationFacilityEditor.runsEqual(List.of(left), List.of(right)));
    }
}
