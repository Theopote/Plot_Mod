package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionResult;

import java.util.List;

/**
 * Aggregate result of materializing every registered two-road crossing.
 */
public record CrossingMaterializationReport(
        int attemptedCount,
        int succeededCount,
        List<CrossingMaterializationFailure> failures) {

    public CrossingMaterializationReport {
        failures = failures != null ? List.copyOf(failures) : List.of();
    }

    public static CrossingMaterializationReport empty() {
        return new CrossingMaterializationReport(0, 0, List.of());
    }

    public boolean isComplete() {
        return failures.isEmpty();
    }

    public IntersectionResult toIntersectionResult() {
        return isComplete() ? IntersectionResult.COMPLETE : IntersectionResult.INCOMPLETE;
    }
}
