package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 窗洞排列模式（Phase 2：全局默认模式，Phase 5 将按立面拆分）。
 */
public final class WindowPatternSpec {
    private final int spacing;
    private final int width;
    private final int height;
    private final int sillHeight;

    public WindowPatternSpec(int spacing, int width, int height, int sillHeight) {
        this.spacing = clamp(spacing, 0, 32);
        this.width = clamp(width, 1, 4);
        this.height = clamp(height, 1, 6);
        this.sillHeight = clamp(sillHeight, 0, 8);
    }

    public static WindowPatternSpec from(BuildingFootprint footprint) {
        return new WindowPatternSpec(
            footprint.getWindowSpacing(),
            footprint.getWindowWidth(),
            footprint.getWindowHeight(),
            footprint.getWindowSillHeight()
        );
    }

    public int spacing() {
        return spacing;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int sillHeight() {
        return sillHeight;
    }

    public boolean enabled() {
        return spacing > 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
