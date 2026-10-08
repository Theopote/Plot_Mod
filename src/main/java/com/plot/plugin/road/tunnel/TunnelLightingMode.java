package com.plot.plugin.road.tunnel;

/**
 * How tunnel lighting blocks are distributed along the corridor.
 */
public enum TunnelLightingMode {
    NONE,
    WALL_BANDS,
    CEILING_BAND,
    WALL_AND_CEILING;

    public static TunnelLightingMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }

    public boolean placesWallLights() {
        return this == WALL_BANDS || this == WALL_AND_CEILING;
    }

    public boolean placesCeilingLights() {
        return this == CEILING_BAND || this == WALL_AND_CEILING;
    }
}
