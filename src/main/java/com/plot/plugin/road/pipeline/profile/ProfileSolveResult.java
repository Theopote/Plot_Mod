package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.profile.WaterCrossingChartMarker;

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
        List<BuildHeightSample> profileBuildSamples,
        BuildHeightProfile buildProfile,
        boolean manualEndpointConstraintFeasible,
        List<Integer> profileWaterHeights,
        List<WaterCrossing> profileWaterCrossings,
        List<WaterCrossingChartMarker> profileWaterCrossingMarkers) {

    public static ProfileSolveResult empty() {
        return new ProfileSolveResult(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            BuildHeightProfile.inactive(), true, List.of(), List.of(), List.of());
    }
}
