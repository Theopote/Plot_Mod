package com.plot.plugin.building.overlay;

import com.plot.plugin.ui.PluginUiColors;
import imgui.ImColor;

/** 各叠加状态的描边/填充样式。 */
public final class BuildingOverlayStyle {
    private final int outlineColor;
    private final int fillColor;
    private final float outlineThickness;
    private final boolean dashed;

    public BuildingOverlayStyle(int outlineColor, int fillColor, float outlineThickness, boolean dashed) {
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

    public static BuildingOverlayStyle forState(BuildingOverlayState state) {
        return switch (state) {
            case REGISTERED -> new BuildingOverlayStyle(
                ImColor.rgba(140, 170, 200, 170),
                ImColor.rgba(77, 166, 255, 28),
                1.2f,
                false);
            case SELECTED -> new BuildingOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 52),
                2.2f,
                false);
            case PRIMARY -> new BuildingOverlayStyle(
                ImColor.rgba(255, 220, 96, 255),
                ImColor.rgba(255, 200, 64, 72),
                3.0f,
                false);
            case INVALID -> new BuildingOverlayStyle(
                PluginUiColors.ERROR,
                ImColor.rgba(255, 96, 96, 36),
                1.8f,
                true);
            case WARNING -> new BuildingOverlayStyle(
                PluginUiColors.WARNING,
                ImColor.rgba(255, 160, 96, 40),
                1.8f,
                true);
            case PREVIEWED -> new BuildingOverlayStyle(
                ImColor.rgba(160, 220, 255, 140),
                ImColor.rgba(128, 192, 255, 24),
                1.0f,
                true);
            case CANDIDATE -> new BuildingOverlayStyle(
                ImColor.rgba(180, 180, 180, 150),
                ImColor.rgba(200, 200, 200, 20),
                1.0f,
                false);
            case PICK_ACTIVE -> new BuildingOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 44),
                2.0f,
                false);
            case ALREADY_ADOPTED -> new BuildingOverlayStyle(
                ImColor.rgba(120, 150, 180, 130),
                ImColor.rgba(77, 166, 255, 22),
                1.2f,
                false);
        };
    }
}
