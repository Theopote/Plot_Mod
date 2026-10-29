package com.plot.plugin.road.crossing;

import com.plot.plugin.road.model.RoadNetwork;

/**
 * Snapshot network plus the crossing-arm materialization report for that copy.
 */
public record SnapshotMaterialization(
        RoadNetwork network,
        CrossingMaterializationReport report) {

    public SnapshotMaterialization {
        if (network == null) {
            throw new IllegalArgumentException("network");
        }
        report = report != null ? report : CrossingMaterializationReport.empty();
    }
}
