package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.profile.RoadProfileChartData;

/**
 * Per-road grey voxel preview held by the profile editor window session.
 * Distinguishes last successful compile from the latest failure diagnostics.
 */
public final class VoxelProfilePreviewSlot {

    private VoxelProfilePreviewState state = VoxelProfilePreviewState.NOT_COMPUTED;
    private VoxelProfilePreviewKey key;
    private VoxelProfileCompileResult lastGoodResult;
    private String failureReason = "";

    public VoxelProfilePreviewState state() {
        return state;
    }

    public VoxelProfilePreviewKey key() {
        return key;
    }

    /** Last successful compile; never replaced by a failed compile. */
    public VoxelProfileCompileResult lastGoodResult() {
        return lastGoodResult;
    }

    /** Alias for {@link #lastGoodResult()}. */
    public VoxelProfileCompileResult result() {
        return lastGoodResult;
    }

    public String failureReason() {
        return failureReason;
    }

    public boolean isReady() {
        return state == VoxelProfilePreviewState.READY
            && lastGoodResult != null
            && lastGoodResult.feasible()
            && lastGoodResult.hasSamples();
    }

    public boolean isStale() {
        return state == VoxelProfilePreviewState.STALE;
    }

    /**
     * Chart overlay that may be drawn: only last successful samples, never failed payload.
     * Callers must still treat READY vs STALE/FAILED for visual styling.
     */
    public RoadProfileChartData displayOverlay() {
        if (lastGoodResult == null || !lastGoodResult.feasible() || !lastGoodResult.hasSamples()) {
            return null;
        }
        if (state != VoxelProfilePreviewState.READY
                && state != VoxelProfilePreviewState.STALE
                && state != VoxelProfilePreviewState.COMPUTING
                && state != VoxelProfilePreviewState.FAILED) {
            return null;
        }
        return lastGoodResult.chartOverlay();
    }

    /** Whether a last-good compile is available for stale/failed comparison display. */
    public boolean hasLastGoodSamples() {
        return lastGoodResult != null && lastGoodResult.feasible() && lastGoodResult.hasSamples();
    }

    public void markStale() {
        if (state != VoxelProfilePreviewState.STALE) {
            state = VoxelProfilePreviewState.STALE;
        }
    }

    public void markComputing(VoxelProfilePreviewKey compileKey) {
        this.key = compileKey;
        this.state = VoxelProfilePreviewState.COMPUTING;
    }

    public void markReady(VoxelProfilePreviewKey compileKey, VoxelProfileCompileResult compileResult) {
        this.key = compileKey;
        this.lastGoodResult = compileResult;
        this.failureReason = "";
        this.state = VoxelProfilePreviewState.READY;
    }

    public void markFailed(VoxelProfilePreviewKey compileKey, String reason) {
        this.key = compileKey;
        this.failureReason = reason != null ? reason : "failed";
        // Keep lastGoodResult untouched so UI can show it as stale comparison.
        this.state = VoxelProfilePreviewState.FAILED;
    }

    public void clear() {
        state = VoxelProfilePreviewState.NOT_COMPUTED;
        key = null;
        lastGoodResult = null;
        failureReason = "";
    }
}
