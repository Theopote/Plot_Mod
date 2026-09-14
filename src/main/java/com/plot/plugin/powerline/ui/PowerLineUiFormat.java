package com.plot.plugin.powerline.ui;

/** 电力线路 UI 浮点数显示格式（统一保留两位小数）。 */
public final class PowerLineUiFormat {
    public static final String SLIDER = "%.2f";
    public static final String SLIDER_PERCENT = "%.2f%%";
    public static final String DEGREES = "%.2f°";

    private PowerLineUiFormat() {
    }

    public static String format(double value) {
        return String.format(SLIDER, value);
    }
}
