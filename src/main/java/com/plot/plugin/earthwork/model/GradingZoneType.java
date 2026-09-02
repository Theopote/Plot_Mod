package com.plot.plugin.earthwork.model;

/**
 * 土方设计分区类型。
 */
public enum GradingZoneType {
    FLAT,
    SLOPED,
    BUILDING_PAD,
    EXCAVATION_PIT,
    TERRAIN_FIT,
    ROAD_CORRIDOR,
    LANDSCAPE;

    public static GradingZoneType fromId(String id) {
        if (id == null || id.isBlank()) {
            return FLAT;
        }
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ignored) {
            return FLAT;
        }
    }

    public static GradingZoneType fromSurfaceMode(GradingSurfaceMode surfaceMode) {
        if (surfaceMode == null) {
            return FLAT;
        }
        return switch (surfaceMode) {
            case FLAT, FIT_SLOPE -> FLAT;
            case FIXED_SLOPE, THREE_POINT -> SLOPED;
        };
    }

    public boolean isSupportedInMvp() {
        return this == FLAT || this == SLOPED;
    }
}
