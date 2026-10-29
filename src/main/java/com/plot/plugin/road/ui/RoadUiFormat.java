package com.plot.plugin.road.ui;

import java.util.Locale;

/**
 * 道路 UI 浮点数显示格式（与电力线路 {@code PowerLineUiFormat} 对齐）。
 * <p>
 * Minecraft {@code Text.translatable} 对 {@code %.Xf} 不可靠，会把 {@code Double}
 * 原样打出；凡进入 i18n 的长度/距离请先 {@link #formatMeters} / {@link #formatDistance}
 * 再以 {@code %s} 传入。
 * <p>
 * 道路长度约定 {@code 1 m = 1} Minecraft 方块；展示值须为世界投影长度，见 {@link RoadLengthDisplay}。
 */
public final class RoadUiFormat {
    public static final String SLIDER = "%.2f";
    /** ImGui slider/drag 带米单位。 */
    public static final String SLIDER_METERS = "%.2fm";

    private RoadUiFormat() {
    }

    public static String format(double value) {
        return String.format(Locale.ROOT, SLIDER, value);
    }

    /** 始终以米展示，保留两位小数（如 {@code 2101.05 m}）。 */
    public static String formatMeters(double meters) {
        return String.format(Locale.ROOT, "%.2f m", meters);
    }

    /** 距离展示：&lt;1000 m 用 m，否则 km；均保留两位小数。 */
    public static String formatDistance(double meters) {
        if (meters >= 1000.0) {
            return String.format(Locale.ROOT, "%.2f km", meters / 1000.0);
        }
        return formatMeters(meters);
    }
}
