package com.plot.plugin.road.ui;

/** 道路 UI 浮点数显示格式（与电力线路 {@code PowerLineUiFormat} 对齐）。 */
public final class RoadUiFormat {
    public static final String SLIDER = "%.2f";

    private RoadUiFormat() {
    }

    public static String format(double value) {
        return String.format(SLIDER, value);
    }
}
