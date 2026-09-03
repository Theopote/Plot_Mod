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
            case LEVEL_PAD, BEST_FIT_PLANE, MATCH_EXISTING, DRAINAGE_SURFACE -> FLAT;
            case SINGLE_SLOPE_PLANE, THREE_POINT_PLANE -> SLOPED;
            case MULTI_PLANE -> SLOPED;
        };
    }

    public boolean isSupportedInMvp() {
        return this == FLAT || this == SLOPED;
    }

    public boolean isSupportedInComposer() {
        return this == FLAT
            || this == SLOPED
            || this == BUILDING_PAD
            || this == EXCAVATION_PIT
            || this == TERRAIN_FIT
            || this == LANDSCAPE
            || this == ROAD_CORRIDOR;
    }

    /** 类型默认将设计标高视为不可独立优化（建筑地坪、基坑、道路走廊）。 */
    public boolean locksDesignElevation() {
        return this == BUILDING_PAD || this == EXCAVATION_PIT || this == ROAD_CORRIDOR;
    }
}
