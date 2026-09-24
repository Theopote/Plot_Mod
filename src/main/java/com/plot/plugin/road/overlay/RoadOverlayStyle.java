package com.plot.plugin.road.overlay;

import com.plot.plugin.ui.PluginUiColors;
import imgui.ImColor;

/** 各道路叠加状态的描边/填充样式。 */
public final class RoadOverlayStyle {
    private final int outlineColor;
    private final int fillColor;
    private final float outlineThickness;
    private final boolean dashed;
    private final boolean drawCenterline;

    public RoadOverlayStyle(
            int outlineColor,
            int fillColor,
            float outlineThickness,
            boolean dashed,
            boolean drawCenterline) {
        this.outlineColor = outlineColor;
        this.fillColor = fillColor;
        this.outlineThickness = outlineThickness;
        this.dashed = dashed;
        this.drawCenterline = drawCenterline;
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

    public boolean drawCenterline() {
        return drawCenterline;
    }

    public static RoadOverlayStyle forState(RoadOverlayState state) {
        return switch (state) {
            case REGISTERED -> new RoadOverlayStyle(
                ImColor.rgba(140, 170, 200, 150),
                ImColor.rgba(77, 166, 255, 24),
                1.2f,
                false,
                false);
            case SELECTED -> new RoadOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 48),
                2.0f,
                false,
                true);
            case PRIMARY -> new RoadOverlayStyle(
                ImColor.rgba(255, 220, 96, 255),
                ImColor.rgba(255, 200, 64, 68),
                2.8f,
                false,
                true);
            case CANDIDATE -> new RoadOverlayStyle(
                ImColor.rgba(180, 180, 180, 140),
                ImColor.rgba(200, 200, 200, 18),
                1.0f,
                true,
                false);
            case PICK_ACTIVE -> new RoadOverlayStyle(
                PluginUiColors.ACCENT_BLUE,
                ImColor.rgba(77, 166, 255, 40),
                2.0f,
                false,
                true);
            case WARNING -> new RoadOverlayStyle(
                PluginUiColors.WARNING,
                ImColor.rgba(255, 160, 96, 44),
                2.0f,
                true,
                true);
            case PREVIEWED -> new RoadOverlayStyle(
                ImColor.rgba(160, 220, 255, 140),
                ImColor.rgba(128, 192, 255, 22),
                1.0f,
                true,
                false);
        };
    }
}
