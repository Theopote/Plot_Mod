package com.plot.plugin.road.tunnel;

/**
 * How tunnel lighting blocks are distributed along the corridor.
 */
public enum TunnelLightingMode {
    NONE,
    WALL_BANDS,
    CEILING_BAND,
    BOTH_SIDES;

    public static final TunnelLightingMode SIDE = WALL_BANDS;
    public static final TunnelLightingMode CEILING = CEILING_BAND;
    public static final TunnelLightingMode SIDE_AND_CEILING = BOTH_SIDES;

    public static TunnelLightingMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("SIDE".equals(normalized)) {
            return WALL_BANDS;
        }
        if ("CEILING".equals(normalized)) {
            return CEILING_BAND;
        }
        if ("SIDE_AND_CEILING".equals(normalized)) {
            return BOTH_SIDES;
        }
        return valueOf(normalized);
    }

    public boolean placesWallLights() {
        return this == WALL_BANDS || this == BOTH_SIDES;
    }

    public boolean placesCeilingLights() {
        return this == CEILING_BAND || this == BOTH_SIDES;
    }
}

