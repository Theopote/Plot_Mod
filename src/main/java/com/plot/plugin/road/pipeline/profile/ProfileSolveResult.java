package com.plot.plugin.road.pipeline.profile;

import java.util.List;

/**
 * Longitudinal profile output: per-segment targets plus chart series for UI/preview.
 */
public record ProfileSolveResult(
        List<SegmentHeightInfo> heightInfos,
        List<Double> profileDistances,
        List<Integer> profileGroundHeights,
        List<Integer> profileGuideLine,
        List<Double> profileDesignElevations,
        List<Integer> profileBuildHeights,
        boolean manualEndpointConstraintFeasible) {

    public static ProfileSolveResult empty() {
        return new ProfileSolveResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true);
    }

    /** Build heights; alias for legacy callers. */
    public List<Integer> profileTargetHeights() {
        return profileBuildHeights;
    }
}
