package com.plot.ui.tools.impl.drawing.helper;

import com.plot.ui.theme.ThemeManager;

import java.awt.Color;

/**
 * 绘制预览测量标签的用户偏好（设置与帮助 → 吸附与反馈）。
 */
public final class DrawMeasureSettings {

    private static boolean enabled = true;
    private static int decimalPlaces = 2;
    private static boolean showUnit = true;
    private static Integer customLabelColorRgb = null;
    private static float labelOpacity = 0.9f;

    private DrawMeasureSettings() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static int getDecimalPlaces() {
        return decimalPlaces;
    }

    public static void setDecimalPlaces(int value) {
        decimalPlaces = Math.max(0, Math.min(4, value));
    }

    public static boolean isShowUnit() {
        return showUnit;
    }

    public static void setShowUnit(boolean value) {
        showUnit = value;
    }

    public static float getLabelOpacity() {
        return labelOpacity;
    }

    public static void setLabelOpacity(float value) {
        labelOpacity = Math.max(0.0f, Math.min(1.0f, value));
    }

    public static boolean usesCustomLabelColor() {
        return customLabelColorRgb != null;
    }

    public static int getLabelColorRgb() {
        return customLabelColorRgb != null ? customLabelColorRgb : defaultLabelColorRgb();
    }

    public static void setLabelColorRgb(int rgb) {
        customLabelColorRgb = rgb & 0xFFFFFF;
    }

    public static void resetLabelColor() {
        customLabelColorRgb = null;
    }

    public static int getEffectiveLabelColorArgb() {
        int alpha = Math.round(labelOpacity * 255.0f);
        return (alpha << 24) | (getLabelColorRgb() & 0xFFFFFF);
    }

    public static Color getLabelColor() {
        int argb = getEffectiveLabelColorArgb();
        return new Color(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                (argb >>> 24) & 0xFF);
    }

    public static int defaultLabelColorRgb() {
        try {
            return ThemeManager.getInstance().getCurrentTheme().warningText & 0xFFFFFF;
        } catch (Throwable ignored) {
            return 0xFFB347;
        }
    }
}
