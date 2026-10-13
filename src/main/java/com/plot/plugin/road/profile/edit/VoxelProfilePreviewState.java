package com.plot.plugin.road.profile.edit;

/** Lifecycle of the grey voxel-profile preview relative to the green design line. */
public enum VoxelProfilePreviewState {
    NOT_COMPUTED,
    STALE,
    COMPUTING,
    READY,
    FAILED
}
