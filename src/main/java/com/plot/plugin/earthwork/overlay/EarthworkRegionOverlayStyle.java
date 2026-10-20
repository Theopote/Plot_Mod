package com.plot.plugin.earthwork.overlay;

import com.plot.plugin.ui.PluginUiColors;
import imgui.ImColor;

/** 各叠加状态的描边/填充样式；REGISTERED 使用区域稳定色板。 */
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
        return forState(state, PluginUiColors.ACCENT_BLUE);
    }

    /**
     * @param regionColor ABGR 区域稳定色（与总览缩略图一致）
     */
    public static EarthworkRegionOverlayStyle forState(EarthworkRegionOverlayState state, int regionColor) {
        int mutedFill = withAlpha(regionColor, 28);
        int selectedFill = withAlpha(regionColor, 52);
        return switch (state) {
            case REGISTERED -> new EarthworkRegionOverlayStyle(
                withAlpha(regionColor, 170),
                mutedFill,
                1.2f,
                false);
            case SELECTED -> new EarthworkRegionOverlayStyle(
                regionColor,
                selectedFill,
                2.4f,
                false);
            case CANDIDATE -> new EarthworkRegionOverlayStyle(
                ImColor.rgba(180, 180, 180, 150),
                ImColor.rgba(200, 200, 200, 20),
                1.0f,
                false);
            case PICK_ACTIVE -> new EarthworkRegionOverlayStyle(
                regionColor,
                withAlpha(regionColor, 44),
                2.0f,
                false);
            case INVALID -> new EarthworkRegionOverlayStyle(
                PluginUiColors.ERROR,
                ImColor.rgba(255, 96, 96, 36),
                1.8f,
                true);
        };
    }

    /** ImGui ABGR：替换 alpha 字节。 */
    private static int withAlpha(int abgr, int alpha) {
        return (abgr & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
