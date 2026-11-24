package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionDraftComparerTest {

    @Test
    void equalsDetectsMedianHeightChange() {
        CrossSectionDraft left = CrossSectionDraft.fromConfig(new RoadSystemConfig("road_system"));
        CrossSectionDraft right = CrossSectionDraft.fromConfig(new RoadSystemConfig("road_system"));
        assertTrue(CrossSectionDraftComparer.equals(left, right));

        right.setMedianHeight(left.medianHeight() + 1);
        assertFalse(CrossSectionDraftComparer.equals(left, right));
    }

    @Test
    void equalsDetectsDefaultLaneWidthChange() {
        CrossSectionDraft left = CrossSectionDraft.fromConfig(new RoadSystemConfig("road_system"));
        CrossSectionDraft right = CrossSectionDraft.fromConfig(new RoadSystemConfig("road_system"));
        assertTrue(CrossSectionDraftComparer.equals(left, right));

        right.setDefaultLaneWidth(left.defaultLaneWidth() + 1);
        assertFalse(CrossSectionDraftComparer.equals(left, right));
    }
}
