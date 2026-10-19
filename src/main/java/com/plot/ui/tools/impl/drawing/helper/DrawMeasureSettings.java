package com.plot.ui.tools.impl.drawing.helper;

/**
 * 绘制预览测量标签的用户偏好（设置与帮助 → 吸附与反馈）。
 */
public final class DrawMeasureSettings {

    private static boolean enabled = true;
    private static int decimalPlaces = 2;
    private static boolean showUnit = true;

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
}
