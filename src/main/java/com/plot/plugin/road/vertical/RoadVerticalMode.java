package com.plot.plugin.road.vertical;

/** User intent for converting terrain and vertical design into road elevations. */
public enum RoadVerticalMode {
    FLAT,
    /** Uses terrain samples as targets while still enforcing grade and shared-junction constraints. */
    FIT_TERRAIN,
    /** v1 automatic grade-limited voxel elevations; does not synthesize PVIs or vertical curves. */
    AUTO_SMOOTH,
    MANUAL_PROFILE;

    public static RoadVerticalMode fromStored(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
