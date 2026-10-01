package com.plot.plugin.road.profile;

/** 纵断面图渲染密度档位。 */
public enum ProfileChartRenderMode {
    /** Generate 页非当前 Road 缩略图。 */
    MINI,
    /** Generate 页当前 Road 概览。 */
    OVERVIEW,
    /** 独立编辑器窗口。 */
    EDITOR;

    public int maxStationTicks() {
        return switch (this) {
            case MINI -> 3;
            case OVERVIEW -> 5;
            case EDITOR -> 5;
        };
    }

    public int maxElevationTicks() {
        return switch (this) {
            case MINI -> 0;
            case OVERVIEW -> 3;
            case EDITOR -> Integer.MAX_VALUE;
        };
    }

    public boolean showElevationAxisLabels() {
        return this == EDITOR;
    }

    public boolean showStationAxisLabels() {
        return true;
    }

    public boolean showGuideLine() {
        return this == EDITOR;
    }

    public boolean showIntersectionLabels() {
        return this == EDITOR;
    }

    public boolean showIntersectionConnectors() {
        return this != MINI;
    }

    public float groundLineWidth() {
        return this == MINI ? 1.4f : 2.2f;
    }

    public float roadLineWidth() {
        return this == MINI ? 2.4f : 2.4f;
    }
}
