package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkValidationReportTest {

    @Test
    void blocksBuildOnIntersectionIncomplete() {
        RoadNetworkValidationReport report = new RoadNetworkValidationReport(List.of(
            RoadNetworkValidationReport.Item.error("plugin.road.validation.intersections_incomplete")));

        assertTrue(report.hasErrors());
        assertTrue(report.blocksBuild());
    }

    @Test
    void blocksBuildOnPendingIntersectionsAndTopologyIssues() {
        RoadNetworkValidationReport pending = new RoadNetworkValidationReport(List.of(
            RoadNetworkValidationReport.Item.warning("plugin.road.validation.intersections_pending")));
        RoadNetworkValidationReport topology = new RoadNetworkValidationReport(List.of(
            RoadNetworkValidationReport.Item.warning("plugin.road.validation.topology_issues", 2)));

        assertTrue(pending.blocksBuild());
        assertTrue(topology.blocksBuild());
    }

    @Test
    void warningsAloneDoNotBlockBuild() {
        RoadNetworkValidationReport report = new RoadNetworkValidationReport(List.of(
            RoadNetworkValidationReport.Item.warning("plugin.road.validation.dead_ends", 3),
            RoadNetworkValidationReport.Item.ok("plugin.road.validation.intersections_resolved")));

        assertTrue(report.hasWarnings());
        assertFalse(report.blocksBuild());
    }
}
