package com.plot.plugin.road.vertical;

/** User intent for converting terrain and vertical design into road elevations. */
public enum RoadVerticalMode {
    FLAT,
    FIT_TERRAIN,
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
