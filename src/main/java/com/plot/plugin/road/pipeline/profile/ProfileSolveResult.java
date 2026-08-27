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
        List<Integer> profileTargetHeights) {

    public static ProfileSolveResult empty() {
        return new ProfileSolveResult(List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
