package com.plot.plugin.road.repair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRepairIssueTest {

    @Test
    void centerlineSelfGeometryRequiresManualFix() {
        assertFalse(RoadRepairIssue.CENTERLINE_SELF_INTERSECTION.isAutoFixable());
        assertFalse(RoadRepairIssue.CENTERLINE_SELF_OVERLAP.isAutoFixable());
    }

    @Test
    void topologyAndProfileIssuesAreAutoFixable() {
        assertTrue(RoadRepairIssue.TOPOLOGY_DISCONNECTED.isAutoFixable());
        assertTrue(RoadRepairIssue.CENTERLINE_NON_LINEAR.isAutoFixable());
        assertTrue(RoadRepairIssue.STEEP_GRADE.isAutoFixable());
        assertTrue(RoadRepairIssue.HORIZONTAL_ALIGNMENT_MISMATCH.isAutoFixable());
    }

    @Test
    void i18nKeysFollowFixRoadIssueConvention() {
        for (RoadRepairIssue issue : RoadRepairIssue.values()) {
            String expected = "plugin.road.fix_road.issue." + issue.name().toLowerCase();
            assertTrue(issue.titleKey().equals(expected), issue.name());
            assertTrue(issue.detailKey().equals(expected + ".detail"), issue.name());
        }
    }
}
