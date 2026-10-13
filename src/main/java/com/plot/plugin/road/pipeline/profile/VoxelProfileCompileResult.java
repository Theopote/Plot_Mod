package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.profile.RoadProfileChartData;

import java.util.List;

/**
 * Immutable result of compiling a continuous design profile into Minecraft block heights.
 */
public record VoxelProfileCompileResult(
        boolean feasible,
        List<BuildHeightSample> samples,
        BuildHeightProfile buildProfile,
        RoadProfileChartData chartOverlay,
        String failureReason,
        BuildHeightSequenceSmoother.SmoothingDiagnostics diagnostics) {

    public static VoxelProfileCompileResult failed(String reason) {
        return new VoxelProfileCompileResult(
            false,
            List.of(),
            BuildHeightProfile.inactive(),
            null,
            reason != null ? reason : "compilation failed",
            new BuildHeightSequenceSmoother.SmoothingDiagnostics(0, 0, 0));
    }

    public boolean hasSamples() {
        return samples != null && samples.size() >= 2;
    }
}
