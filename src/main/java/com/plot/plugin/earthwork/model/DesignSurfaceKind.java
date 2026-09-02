package com.plot.plugin.earthwork.model;

/**
 * 设计面求解类型（与 {@link GradingSurfaceMode} 对应，用于 JSON v2）。
 */
public enum DesignSurfaceKind {
    FLAT,
    FIXED_SLOPE,
    THREE_POINT,
    FIT_SLOPE,
    CONSTANT_ELEVATION,
    EXCAVATION_PIT;

    public static DesignSurfaceKind fromId(String id) {
        if (id == null || id.isBlank()) {
            return FLAT;
        }
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ignored) {
            return FLAT;
        }
    }

    public static DesignSurfaceKind fromSurfaceMode(GradingSurfaceMode surfaceMode) {
        if (surfaceMode == null) {
            return FLAT;
        }
        return switch (surfaceMode) {
            case FLAT -> FLAT;
            case FIXED_SLOPE -> FIXED_SLOPE;
            case THREE_POINT -> THREE_POINT;
            case FIT_SLOPE -> FIT_SLOPE;
        };
    }

    public GradingSurfaceMode toSurfaceMode() {
        return switch (this) {
            case FLAT, CONSTANT_ELEVATION -> GradingSurfaceMode.FLAT;
            case FIXED_SLOPE -> GradingSurfaceMode.FIXED_SLOPE;
            case THREE_POINT -> GradingSurfaceMode.THREE_POINT;
            case FIT_SLOPE, EXCAVATION_PIT -> GradingSurfaceMode.FIT_SLOPE;
        };
    }
}
