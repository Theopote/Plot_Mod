package com.plot.plugin.earthwork.model;

import com.plot.utils.PlotI18n;

/**
 * 整平目标曲面类型（UI / {@link GradingRegion} 载体）。
 */
public enum GradingSurfaceMode {
    LEVEL_PAD("plugin.earthwork.surface.level_pad"),
    SINGLE_SLOPE_PLANE("plugin.earthwork.surface.single_slope_plane"),
    THREE_POINT_PLANE("plugin.earthwork.surface.three_point_plane"),
    BEST_FIT_PLANE("plugin.earthwork.surface.best_fit_plane"),
    MATCH_EXISTING("plugin.earthwork.surface.match_existing"),
    MULTI_PLANE("plugin.earthwork.surface.multi_plane"),
    DRAINAGE_SURFACE("plugin.earthwork.surface.drainage_surface");

    private final String i18nKey;

    GradingSurfaceMode(String i18nKey) {
        this.i18nKey = i18nKey;
    }

    public String label() {
        return PlotI18n.tr(i18nKey);
    }

    public static GradingSurfaceMode fromId(String id) {
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
}
