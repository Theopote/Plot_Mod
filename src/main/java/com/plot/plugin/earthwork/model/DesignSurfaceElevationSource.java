package com.plot.plugin.earthwork.model;

/**
 * 常数设计面标高来源。
 */
public enum DesignSurfaceElevationSource {
    BAKED_ROAD,
    MANUAL,
    BUILDING_BASE_ELEVATION;

    public static DesignSurfaceElevationSource fromId(String id) {
        if (id == null || id.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ignored) {
            return MANUAL;
        }
    }
}
