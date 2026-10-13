package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;

/**
 * Per-road grey voxel preview held by the profile editor window session.
 */
public final class VoxelProfilePreviewSlot {

    private VoxelProfilePreviewState state = VoxelProfilePreviewState.NOT_COMPUTED;
    private VoxelProfilePreviewKey key;
    private VoxelProfileCompileResult result;
    private String failureReason = "";

    public VoxelProfilePreviewState state() {
        return state;
    }

    public VoxelProfilePreviewKey key() {
        return key;
    }

    public VoxelProfileCompileResult result() {
        return result;
    }

    public String failureReason() {
        return failureReason;
    }

    public boolean isReady() {
        return state == VoxelProfilePreviewState.READY && result != null && result.hasSamples();
    }

    public boolean isStale() {
        return state == VoxelProfilePreviewState.STALE;
    }

    public void markStale() {
        if (state == VoxelProfilePreviewState.READY || state == VoxelProfilePreviewState.FAILED) {
            state = VoxelProfilePreviewState.STALE;
        } else if (state == VoxelProfilePreviewState.NOT_COMPUTED) {
            state = VoxelProfilePreviewState.STALE;
        }
    }

    public void markComputing(VoxelProfilePreviewKey compileKey) {
        this.key = compileKey;
        this.state = VoxelProfilePreviewState.COMPUTING;
    }

    public void markReady(VoxelProfilePreviewKey compileKey, VoxelProfileCompileResult compileResult) {
        this.key = compileKey;
        this.result = compileResult;
        this.failureReason = "";
        this.state = VoxelProfilePreviewState.READY;
    }

    public void markFailed(VoxelProfilePreviewKey compileKey, String reason) {
        this.key = compileKey;
        this.failureReason = reason != null ? reason : "failed";
        this.state = VoxelProfilePreviewState.FAILED;
    }

    public void clear() {
        state = VoxelProfilePreviewState.NOT_COMPUTED;
        key = null;
        result = null;
        failureReason = "";
    }
}
