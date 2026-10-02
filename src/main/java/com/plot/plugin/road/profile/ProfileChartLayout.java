package com.plot.plugin.road.profile;

/**
 * 纵断面图布局：统一 plot 区与坐标换算，避免散落 padding 重复计算。
 */
public record ProfileChartLayout(
        float outerLeft,
        float outerTop,
        float outerRight,
        float outerBottom,
        float plotLeft,
        float plotTop,
        float plotRight,
        float plotBottom) {

    private static final float LEFT_GUTTER = 42f;
    private static final float RIGHT_GUTTER = 42f;
    private static final float TOP_GUTTER = 14f;
    private static final float BOTTOM_GUTTER = 24f;

    private static final float COMPACT_LEFT_GUTTER = 4f;
    private static final float COMPACT_RIGHT_GUTTER = 4f;
    private static final float COMPACT_TOP_GUTTER = 4f;
    private static final float COMPACT_BOTTOM_GUTTER = 6f;
    private static final float COMPACT_BOTTOM_GUTTER_WITH_STATION_LABELS = 16f;

    public static ProfileChartLayout fromOuterRect(float x0, float y0, float width, float height) {
        return fromOuterRect(x0, y0, width, height, false, true);
    }

    public static ProfileChartLayout fromOuterRect(
            float x0,
            float y0,
            float width,
            float height,
            ProfileChartRenderMode mode) {
        if (mode == null) {
            return fromOuterRect(x0, y0, width, height);
        }
        return fromOuterRect(x0, y0, width, height, mode.useCompactLayout(), mode.showStationAxisLabels());
    }

    private static ProfileChartLayout fromOuterRect(
            float x0,
            float y0,
            float width,
            float height,
            boolean compact,
            boolean stationLabels) {
        if (!compact) {
            return new ProfileChartLayout(
                x0,
                y0,
                x0 + width,
                y0 + height,
                x0 + LEFT_GUTTER,
                y0 + TOP_GUTTER,
                x0 + width - RIGHT_GUTTER,
                y0 + height - BOTTOM_GUTTER);
        }
        float bottomGutter = stationLabels
            ? COMPACT_BOTTOM_GUTTER_WITH_STATION_LABELS
            : COMPACT_BOTTOM_GUTTER;
        return new ProfileChartLayout(
            x0,
            y0,
            x0 + width,
            y0 + height,
            x0 + COMPACT_LEFT_GUTTER,
            y0 + COMPACT_TOP_GUTTER,
            x0 + width - COMPACT_RIGHT_GUTTER,
            y0 + height - bottomGutter);
    }

    public float plotWidth() {
        return plotRight - plotLeft;
    }

    public float plotHeight() {
        return plotBottom - plotTop;
    }

    public float plotX(double station, double totalStation) {
        if (totalStation <= 1e-9) {
            return plotLeft;
        }
        double ratio = Math.max(0.0, Math.min(1.0, station / totalStation));
        return plotLeft + (float) (ratio * plotWidth());
    }

    public float plotY(double elevation, double minElevation, double maxElevation) {
        if (maxElevation <= minElevation + 1e-9) {
            return plotTop + plotHeight() * 0.5f;
        }
        double ratio = (elevation - minElevation) / (maxElevation - minElevation);
        return plotTop + (float) ((1.0 - ratio) * plotHeight());
    }

    public double stationAtMouseX(float mouseX, double totalStation) {
        if (plotWidth() <= 1e-6f || totalStation <= 1e-9) {
            return 0.0;
        }
        double ratio = (mouseX - plotLeft) / plotWidth();
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double raw = ratio * totalStation;
        return Math.rint(raw * 4.0) / 4.0;
    }

    public double elevationAtMouseY(float mouseY, double minElevation, double maxElevation) {
        if (plotHeight() <= 1e-6f || maxElevation <= minElevation + 1e-9) {
            return minElevation;
        }
        double ratio = (mouseY - plotTop) / plotHeight();
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double raw = maxElevation - ratio * (maxElevation - minElevation);
        return Math.rint(raw * 4.0) / 4.0;
    }
}
