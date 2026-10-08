package com.plot.plugin.road.tunnel;

/**
 * How tunnel lighting blocks are distributed along the corridor.
 */
public enum TunnelLightingMode {
    NONE,
    WALL_BANDS,
    CEILING_BAND,
    BOTH_SIDES;

    public static TunnelLightingMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
