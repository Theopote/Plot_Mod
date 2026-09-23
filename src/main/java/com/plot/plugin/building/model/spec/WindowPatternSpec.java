package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 窗洞排列模式（全局默认或 {@link WallFacadeSpec} 分墙段覆盖）。
 * <p>
 * 用户语义：窗宽 + 窗间墙宽；内部步距 {@code spacing = width + pierWidth}。
 */
public final class WindowPatternSpec {
    private final int width;
    private final int pierWidth;
    private final int height;
    private final int sillHeight;
    private final boolean enabled;

    /**
     * 兼容旧数据：{@code spacing} 为相邻窗起始列间距（= width + pierWidth）。
     */
    public WindowPatternSpec(int spacing, int width, int height, int sillHeight) {
        this.width = clamp(width, 1, BuildingFootprint.MAX_WINDOW_WIDTH);
        this.pierWidth = clamp(Math.max(0, spacing - this.width), 0, 32);
        this.height = clamp(height, 1, 16);
        this.sillHeight = clamp(sillHeight, 0, 16);
        this.enabled = spacing > 0;
    }

    public static WindowPatternSpec of(int width, int pierWidth, int height, int sillHeight) {
        return new WindowPatternSpec(width, pierWidth, height, sillHeight, true);
    }

    /** 该墙段不开窗。 */
    public static WindowPatternSpec disabled() {
        return new WindowPatternSpec(1, 0, 2, 1, false);
    }

    private WindowPatternSpec(int width, int pierWidth, int height, int sillHeight, boolean enabled) {
        this.width = clamp(width, 1, BuildingFootprint.MAX_WINDOW_WIDTH);
        this.pierWidth = clamp(pierWidth, 0, 32);
        this.height = clamp(height, 1, 16);
        this.sillHeight = clamp(sillHeight, 0, 16);
        this.enabled = enabled;
    }

    public static WindowPatternSpec from(BuildingFootprint footprint) {
        if (!footprint.isWindowsEnabled()) {
            return disabled();
        }
        return of(
            footprint.getWindowWidth(),
            footprint.getWindowPierWidth(),
            footprint.getWindowHeight(),
            footprint.getWindowSillHeight()
        );
    }

    /** 相邻窗起始列间距（= width + pierWidth）。 */
    public int spacing() {
        return enabled ? width + pierWidth : 0;
    }

    public int width() {
        return width;
    }

    public int pierWidth() {
        return pierWidth;
    }

    public int height() {
        return height;
    }

    public int sillHeight() {
        return sillHeight;
    }

    public boolean enabled() {
        return enabled;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
