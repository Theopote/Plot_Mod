package com.plot.plugin.earthwork.model;

import com.plot.utils.PlotI18n;

/**
 * 整平目标曲面类型。
 */
public enum GradingSurfaceMode {
    FLAT("plugin.earthwork.surface.flat"),
    FIXED_SLOPE("plugin.earthwork.surface.fixed_slope"),
    THREE_POINT("plugin.earthwork.surface.three_point"),
    FIT_SLOPE("plugin.earthwork.surface.fit_slope");

    private final String i18nKey;

    GradingSurfaceMode(String i18nKey) {
        this.i18nKey = i18nKey;
    }

    public String label() {
        return PlotI18n.tr(i18nKey);
    }

    public static GradingSurfaceMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return FLAT;
        }
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ignored) {
            return FLAT;
        }
    }
}
