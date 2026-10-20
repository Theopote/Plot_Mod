package com.plot.plugin.earthwork.overlay;

import com.plot.plugin.ui.PluginUiColors;
import imgui.ImColor;

/** 各叠加状态的描边/填充样式。 */
public final class EarthworkRegionOverlayStyle {
    private final int outlineColor;
    private final int fillColor;
    private final float outlineThickness;
    private final boolean dashed;

    public EarthworkRegionOverlayStyle(int outlineColor, int fillColor, float outlineThickness, boolean dashed) {
        this.outlineColor = outlineColor;
        this.fillColor = fillColor;
        this.outlineThickness = outlineThickness;
        this.dashed = dashed;
    }

    public int outlineColor() {
        return outlineColor;
    }

    public int fillColor() {
        return fillColor;
    }

    public float outlineThickness() {
        return outlineThickness;
    }

    public boolean dashed() {
        return dashed;
    }

    public static EarthworkRegionOverlayStyle forState(EarthworkRegionOverlayState state) {
        return switch (state) {
            case REGISTERED -> new EarthworkRegionOverlayStyle(
                ImColor.rgba(140, 170, 200, 170),
                ImColor.rgba(77, 166, 255, 28),
                1.2f,
                false);
            case SELECTED -> new EarthworkRegionOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 52),
                2.4f,
                false);
            case CANDIDATE -> new EarthworkRegionOverlayStyle(
                ImColor.rgba(180, 180, 180, 150),
                ImColor.rgba(200, 200, 200, 20),
                1.0f,
                false);
            case PICK_ACTIVE -> new EarthworkRegionOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 44),
                2.0f,
                false);
            case INVALID -> new EarthworkRegionOverlayStyle(
                PluginUiColors.ERROR,
                ImColor.rgba(255, 96, 96, 36),
                1.8f,
                true);
        };
    }
}
