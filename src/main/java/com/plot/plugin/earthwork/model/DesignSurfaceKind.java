package com.plot.plugin.earthwork.model;

/**
 * 设计面求解类型（JSON v2 {@code designSurface.kind}）。
 */
public enum DesignSurfaceKind {
    /** 水平坪（原 FLAT） */
    LEVEL_PAD,
    /** 单坡平面（原 FIXED_SLOPE） */
    SINGLE_SLOPE_PLANE,
    /** 三点平面（原 THREE_POINT） */
    THREE_POINT_PLANE,
    /** 最小二乘拟合坡面（原 FIT_SLOPE） */
    BEST_FIT_PLANE,
    /** 现状整体偏移：DesignY = ExistingY + ΔY */
    MATCH_EXISTING,
    /** 分区内多个子坡面 */
    MULTI_PLANE,
    /** 排水坡面（暂委托 BEST_FIT_PLANE 求解） */
    DRAINAGE_SURFACE,
    CONSTANT_ELEVATION,
    EXCAVATION_PIT,
    ROAD_CORRIDOR;

    public String i18nKey() {
        return "plugin.earthwork.design_surface." + name().toLowerCase();
    }

    public static DesignSurfaceKind fromId(String id) {
        if (id == null || id.isBlank()) {
            return LEVEL_PAD;
        }
        return switch (id.trim().toUpperCase()) {
            case "FLAT" -> LEVEL_PAD;
            case "FIXED_SLOPE" -> SINGLE_SLOPE_PLANE;
            case "THREE_POINT" -> THREE_POINT_PLANE;
            case "FIT_SLOPE" -> BEST_FIT_PLANE;
            default -> {
                try {
                    yield valueOf(id.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    yield LEVEL_PAD;
                }
            }
        };
    }

    public static DesignSurfaceKind fromSurfaceMode(GradingSurfaceMode surfaceMode) {
        if (surfaceMode == null) {
            return LEVEL_PAD;
        }
        return switch (surfaceMode) {
            case LEVEL_PAD -> LEVEL_PAD;
            case SINGLE_SLOPE_PLANE -> SINGLE_SLOPE_PLANE;
            case THREE_POINT_PLANE -> THREE_POINT_PLANE;
            case BEST_FIT_PLANE -> BEST_FIT_PLANE;
            case MATCH_EXISTING -> MATCH_EXISTING;
            case MULTI_PLANE -> MULTI_PLANE;
            case DRAINAGE_SURFACE -> DRAINAGE_SURFACE;
        };
    }

    public GradingSurfaceMode toSurfaceMode() {
        return switch (this) {
            case LEVEL_PAD, CONSTANT_ELEVATION -> GradingSurfaceMode.LEVEL_PAD;
            case SINGLE_SLOPE_PLANE -> GradingSurfaceMode.SINGLE_SLOPE_PLANE;
            case THREE_POINT_PLANE -> GradingSurfaceMode.THREE_POINT_PLANE;
            case BEST_FIT_PLANE, EXCAVATION_PIT, ROAD_CORRIDOR, DRAINAGE_SURFACE -> GradingSurfaceMode.BEST_FIT_PLANE;
            case MATCH_EXISTING -> GradingSurfaceMode.MATCH_EXISTING;
            case MULTI_PLANE -> GradingSurfaceMode.MULTI_PLANE;
        };
    }

    public boolean isPlaneBased() {
        return this == LEVEL_PAD
            || this == SINGLE_SLOPE_PLANE
            || this == THREE_POINT_PLANE
            || this == BEST_FIT_PLANE
            || this == DRAINAGE_SURFACE;
    }
}
